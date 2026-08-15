#pragma once

#include <cstdint>

namespace manette {
constexpr uint32_t kMagic = 0x4D4E5450;    // MNTP
constexpr uint32_t kAckMagic = 0x4D4E5441; // MNTA
constexpr uint8_t kProtocolVersion = 2;
constexpr uint16_t kPort = 4405;
constexpr uint32_t kPacketSize = 20;
constexpr uint32_t kAckSize = 8;
constexpr uint64_t kTimeoutMs = 500;

enum Button : uint32_t {
    BTN_A       = 1u << 0,
    BTN_B       = 1u << 1,
    BTN_X       = 1u << 2,
    BTN_Y       = 1u << 3,
    BTN_UP      = 1u << 4,
    BTN_DOWN    = 1u << 5,
    BTN_LEFT    = 1u << 6,
    BTN_RIGHT   = 1u << 7,
    BTN_L       = 1u << 8,
    BTN_R       = 1u << 9,
    BTN_ZL      = 1u << 10,
    BTN_ZR      = 1u << 11,
    BTN_PLUS    = 1u << 12,
    BTN_MINUS   = 1u << 13,
    BTN_HOME    = 1u << 14,
    BTN_STICK_L = 1u << 15,
    BTN_STICK_R = 1u << 16,
};

struct State {
    uint8_t channel = 0;
    uint16_t sequence = 0;
    uint32_t buttons = 0;
    int16_t leftX = 0;
    int16_t leftY = 0;
    int16_t rightX = 0;
    int16_t rightY = 0;
    uint64_t lastPacketMs = 0;
};

inline uint16_t ReadU16BE(const uint8_t *p) {
    return (static_cast<uint16_t>(p[0]) << 8) |
           static_cast<uint16_t>(p[1]);
}

inline uint32_t ReadU32BE(const uint8_t *p) {
    return (static_cast<uint32_t>(p[0]) << 24) |
           (static_cast<uint32_t>(p[1]) << 16) |
           (static_cast<uint32_t>(p[2]) << 8) |
           static_cast<uint32_t>(p[3]);
}

inline int16_t ReadS16BE(const uint8_t *p) {
    return static_cast<int16_t>(ReadU16BE(p));
}

inline void WriteU16BE(uint8_t *p, uint16_t value) {
    p[0] = static_cast<uint8_t>((value >> 8) & 0xFF);
    p[1] = static_cast<uint8_t>(value & 0xFF);
}

inline void WriteU32BE(uint8_t *p, uint32_t value) {
    p[0] = static_cast<uint8_t>((value >> 24) & 0xFF);
    p[1] = static_cast<uint8_t>((value >> 16) & 0xFF);
    p[2] = static_cast<uint8_t>((value >> 8) & 0xFF);
    p[3] = static_cast<uint8_t>(value & 0xFF);
}
} // namespace manette
