# Manette Network Protocol v1

Transport: UDP, port `4405`, big-endian/network byte order.

The sender publishes the **latest complete controller state** about every 16 ms. The receiver does not queue historical input. If no valid packet is received for 500 ms, the virtual controller is considered disconnected and the plugin falls back to the real Wii U controller path.

## Packet (20 bytes)

| Offset | Size | Field |
|---:|---:|---|
| 0 | 4 | Magic `MNTP` (`0x4D4E5450`) |
| 4 | 1 | Protocol version (`1`) |
| 5 | 1 | Player/channel (`0` in V0.1) |
| 6 | 2 | Sequence number |
| 8 | 4 | Button bitmask |
| 12 | 2 | Left stick X, signed 16-bit |
| 14 | 2 | Left stick Y, signed 16-bit |
| 16 | 2 | Right stick X, signed 16-bit |
| 18 | 2 | Right stick Y, signed 16-bit |

## Button bits

`0 A`, `1 B`, `2 X`, `3 Y`, `4 Up`, `5 Down`, `6 Left`, `7 Right`, `8 L`, `9 R`, `10 ZL`, `11 ZR`, `12 Plus`, `13 Minus`, `14 Home`, `15 L3`, `16 R3`.

## Design rules

- Latest packet wins.
- No input backlog.
- No retransmission for controller-state packets.
- A future reverse channel may carry rumble/connection telemetry without changing the 20-byte input packet.
