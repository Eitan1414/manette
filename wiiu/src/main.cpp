#include "protocol.h"

#include <wups.h>

#include <coreinit/thread.h>
#include <coreinit/time.h>
#include <padscore/kpad.h>
#include <padscore/wpad.h>

#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <thread>

WUPS_PLUGIN_NAME("Manette");
WUPS_PLUGIN_DESCRIPTION("Android tablet to virtual Wii U Pro Controller");
WUPS_PLUGIN_VERSION("0.2.0");
WUPS_PLUGIN_AUTHOR("Eitan1414 / OpenAI");
WUPS_PLUGIN_LICENSE("MIT");

WUPS_USE_WUT_DEVOPTAB();

namespace {
std::atomic<bool> gRunning{false};
std::atomic<int> gSocket{-1};
std::thread gReceiverThread;
std::mutex gStateMutex;
manette::State gState;
uint32_t gPreviousProButtons = 0;
int gPreviousChannel = -1;

uint64_t NowMs() {
    return OSTicksToMilliseconds(OSGetTime());
}

float NormalizeAxis(int16_t value) {
    if (value == INT16_MIN) return -1.0f;
    return static_cast<float>(value) / 32767.0f;
}

int16_t ToWPADAxis(int16_t value) {
    const float normalized = NormalizeAxis(value);
    const float scaled = normalized < 0.0f ? normalized * 2048.0f : normalized * 2047.0f;
    return static_cast<int16_t>(scaled);
}

bool ReadVirtualState(manette::State &out) {
    std::scoped_lock lock(gStateMutex);
    if (gState.lastPacketMs == 0 || NowMs() - gState.lastPacketMs > manette::kTimeoutMs) return false;
    out = gState;
    return true;
}

bool IsVirtualChannel(int channel, const manette::State &state) {
    return channel == static_cast<int>(state.channel);
}

uint32_t ToProButtons(uint32_t buttons) {
    uint32_t out = 0;
#define MAP_BUTTON(src, dst) if ((buttons & (src)) != 0) out |= (dst)
    MAP_BUTTON(manette::BTN_A, WPAD_PRO_BUTTON_A);
    MAP_BUTTON(manette::BTN_B, WPAD_PRO_BUTTON_B);
    MAP_BUTTON(manette::BTN_X, WPAD_PRO_BUTTON_X);
    MAP_BUTTON(manette::BTN_Y, WPAD_PRO_BUTTON_Y);
    MAP_BUTTON(manette::BTN_UP, WPAD_PRO_BUTTON_UP);
    MAP_BUTTON(manette::BTN_DOWN, WPAD_PRO_BUTTON_DOWN);
    MAP_BUTTON(manette::BTN_LEFT, WPAD_PRO_BUTTON_LEFT);
    MAP_BUTTON(manette::BTN_RIGHT, WPAD_PRO_BUTTON_RIGHT);
    MAP_BUTTON(manette::BTN_L, WPAD_PRO_TRIGGER_L);
    MAP_BUTTON(manette::BTN_R, WPAD_PRO_TRIGGER_R);
    MAP_BUTTON(manette::BTN_ZL, WPAD_PRO_TRIGGER_ZL);
    MAP_BUTTON(manette::BTN_ZR, WPAD_PRO_TRIGGER_ZR);
    MAP_BUTTON(manette::BTN_PLUS, WPAD_PRO_BUTTON_PLUS);
    MAP_BUTTON(manette::BTN_MINUS, WPAD_PRO_BUTTON_MINUS);
    MAP_BUTTON(manette::BTN_HOME, WPAD_PRO_BUTTON_HOME);
    MAP_BUTTON(manette::BTN_STICK_L, WPAD_PRO_BUTTON_STICK_L);
    MAP_BUTTON(manette::BTN_STICK_R, WPAD_PRO_BUTTON_STICK_R);
#undef MAP_BUTTON
    return out;
}

bool DecodePacket(const uint8_t *data, size_t size, manette::State &state) {
    if (size != manette::kPacketSize) return false;
    if (manette::ReadU32BE(data) != manette::kMagic) return false;
    if (data[4] != manette::kProtocolVersion) return false;
    if (data[5] > 6) return false;

    state.channel = data[5];
    state.sequence = manette::ReadU16BE(data + 6);
    state.buttons = manette::ReadU32BE(data + 8);
    state.leftX = manette::ReadS16BE(data + 12);
    state.leftY = manette::ReadS16BE(data + 14);
    state.rightX = manette::ReadS16BE(data + 16);
    state.rightY = manette::ReadS16BE(data + 18);
    state.lastPacketMs = NowMs();
    return true;
}

void SendAck(int fd, const sockaddr_in &sender, socklen_t senderLength, const manette::State &state) {
    uint8_t ack[manette::kAckSize]{};
    manette::WriteU32BE(ack, manette::kAckMagic);
    ack[4] = manette::kProtocolVersion;
    ack[5] = state.channel;
    manette::WriteU16BE(ack + 6, state.sequence);
    sendto(fd, ack, sizeof(ack), 0, reinterpret_cast<const sockaddr *>(&sender), senderLength);
}

void ReceiverLoop() {
    const int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (fd < 0) return;
    gSocket.store(fd);

    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_port = htons(manette::kPort);
    address.sin_addr.s_addr = htonl(INADDR_ANY);

    if (bind(fd, reinterpret_cast<sockaddr *>(&address), sizeof(address)) < 0) {
        close(fd);
        gSocket.store(-1);
        return;
    }

    uint8_t packet[64]{};
    while (gRunning.load()) {
        sockaddr_in sender{};
        socklen_t senderLength = sizeof(sender);
        const int received = recvfrom(fd, packet, sizeof(packet), MSG_DONTWAIT,
                                      reinterpret_cast<sockaddr *>(&sender), &senderLength);

        if (received <= 0) {
            OSSleepTicks(OSMillisecondsToTicks(2));
            continue;
        }

        manette::State decoded{};
        if (!DecodePacket(packet, static_cast<size_t>(received), decoded)) continue;

        {
            std::scoped_lock lock(gStateMutex);
            if (gState.channel != decoded.channel) {
                gPreviousProButtons = 0;
                gPreviousChannel = decoded.channel;
            }
            gState = decoded;
        }
        SendAck(fd, sender, senderLength, decoded);
    }

    close(fd);
    gSocket.store(-1);
}

void StartReceiver() {
    if (gRunning.exchange(true)) return;
    {
        std::scoped_lock lock(gStateMutex);
        gState = {};
    }
    gPreviousProButtons = 0;
    gPreviousChannel = -1;
    gReceiverThread = std::thread(ReceiverLoop);
}

void StopReceiver() {
    if (!gRunning.exchange(false)) return;
    const int fd = gSocket.load();
    if (fd >= 0) shutdown(fd, SHUT_RDWR);
    if (gReceiverThread.joinable()) gReceiverThread.join();
    gSocket.store(-1);
    {
        std::scoped_lock lock(gStateMutex);
        gState = {};
    }
    gPreviousProButtons = 0;
    gPreviousChannel = -1;
}

void FillKPADStatus(KPADStatus &status, const manette::State &state) {
    std::memset(&status, 0, sizeof(status));
    if (gPreviousChannel != static_cast<int>(state.channel)) {
        gPreviousProButtons = 0;
        gPreviousChannel = state.channel;
    }

    const uint32_t hold = ToProButtons(state.buttons);
    status.pro.hold = hold;
    status.pro.trigger = hold & ~gPreviousProButtons;
    status.pro.release = gPreviousProButtons & ~hold;
    gPreviousProButtons = hold;

    status.pro.leftStick.x = NormalizeAxis(state.leftX);
    status.pro.leftStick.y = NormalizeAxis(state.leftY);
    status.pro.rightStick.x = NormalizeAxis(state.rightX);
    status.pro.rightStick.y = NormalizeAxis(state.rightY);
    status.pro.charging = false;
    status.pro.wired = false;
    status.format = KPADDataFormat::WPAD_FMT_PRO_CONTROLLER;
    status.extensionType = KPADExtensionType::WPAD_EXT_PRO_CONTROLLER;
    status.error = KPADError::KPAD_ERROR_OK;
}

void FillWPADStatus(WPADStatusProController &status, const manette::State &state) {
    std::memset(&status, 0, sizeof(status));
    status.core.error = 0;
    status.core.extensionType = WPAD_EXT_PRO_CONTROLLER;
    status.buttons = ToProButtons(state.buttons);
    status.leftStick.x = ToWPADAxis(state.leftX);
    status.leftStick.y = ToWPADAxis(state.leftY);
    status.rightStick.x = ToWPADAxis(state.rightX);
    status.rightStick.y = ToWPADAxis(state.rightY);
    status.charging = false;
    status.wired = false;
}
} // namespace

INITIALIZE_PLUGIN() {}

ON_APPLICATION_START() {
    StartReceiver();
}

ON_APPLICATION_ENDS() {
    StopReceiver();
}

DEINITIALIZE_PLUGIN() {
    StopReceiver();
}

DECL_FUNCTION(int32_t, KPADReadEx, KPADChan channel, KPADStatus *data, uint32_t size, KPADError *outError) {
    manette::State state{};
    const bool connected = ReadVirtualState(state);
    if (!connected || !IsVirtualChannel(static_cast<int>(channel), state)) {
        if (!connected) gPreviousProButtons = 0;
        return real_KPADReadEx(channel, data, size, outError);
    }

    if (data == nullptr || size == 0) {
        if (outError) *outError = KPAD_ERROR_NO_SAMPLES;
        return 0;
    }

    FillKPADStatus(data[0], state);
    if (outError) *outError = KPAD_ERROR_OK;
    return 1;
}

DECL_FUNCTION(int32_t, KPADRead, KPADChan channel, KPADStatus *data, uint32_t size) {
    KPADError error{};
    return my_KPADReadEx(channel, data, size, &error);
}

DECL_FUNCTION(int32_t, WPADProbe, WPADChan channel, WPADExtensionType *outExtensionType) {
    manette::State state{};
    if (!ReadVirtualState(state) || !IsVirtualChannel(static_cast<int>(channel), state)) {
        return real_WPADProbe(channel, outExtensionType);
    }
    if (outExtensionType) *outExtensionType = WPAD_EXT_PRO_CONTROLLER;
    return 0;
}

DECL_FUNCTION(void, WPADRead, WPADChan channel, void *buffer) {
    manette::State state{};
    if (!ReadVirtualState(state) || !IsVirtualChannel(static_cast<int>(channel), state) || buffer == nullptr) {
        real_WPADRead(channel, buffer);
        return;
    }
    FillWPADStatus(*static_cast<WPADStatusProController *>(buffer), state);
}

DECL_FUNCTION(WPADDataFormat, WPADGetDataFormat, WPADChan channel) {
    manette::State state{};
    if (!ReadVirtualState(state) || !IsVirtualChannel(static_cast<int>(channel), state)) {
        return real_WPADGetDataFormat(channel);
    }
    return WPAD_FMT_PRO_CONTROLLER;
}

DECL_FUNCTION(uint8_t, WPADGetBatteryLevel, WPADChan channel) {
    manette::State state{};
    if (!ReadVirtualState(state) || !IsVirtualChannel(static_cast<int>(channel), state)) {
        return real_WPADGetBatteryLevel(channel);
    }
    return 4;
}

DECL_FUNCTION(void, WPADControlMotor, WPADChan channel, BOOL enabled) {
    manette::State state{};
    if (!ReadVirtualState(state) || !IsVirtualChannel(static_cast<int>(channel), state)) {
        real_WPADControlMotor(channel, enabled);
    }
}

WUPS_MUST_REPLACE(KPADReadEx, WUPS_LOADER_LIBRARY_PADSCORE, KPADReadEx);
WUPS_MUST_REPLACE(KPADRead, WUPS_LOADER_LIBRARY_PADSCORE, KPADRead);
WUPS_MUST_REPLACE(WPADProbe, WUPS_LOADER_LIBRARY_PADSCORE, WPADProbe);
WUPS_MUST_REPLACE(WPADRead, WUPS_LOADER_LIBRARY_PADSCORE, WPADRead);
WUPS_MUST_REPLACE(WPADGetDataFormat, WUPS_LOADER_LIBRARY_PADSCORE, WPADGetDataFormat);
WUPS_MUST_REPLACE(WPADGetBatteryLevel, WUPS_LOADER_LIBRARY_PADSCORE, WPADGetBatteryLevel);
WUPS_MUST_REPLACE(WPADControlMotor, WUPS_LOADER_LIBRARY_PADSCORE, WPADControlMotor);
