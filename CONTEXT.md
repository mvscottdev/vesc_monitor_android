# Glossary

One line per term. Use these words in code, docs and UI text.

| Term | Meaning | Avoid |
|---|---|---|
| Vehicle | The e-scooter or e-bike; owns one profile | board, device |
| Controller | One motor controller running VESC firmware, identified by its CAN ID (`local` = the one wired to the bridge) | VESC (ambiguous), ESC, slave, master |
| Bridge | The BLE module that relays packets to a controller over UART or CAN (NRF51, NRF52, VESC Express, HM-10) | dongle, adapter |
| Link | The BLE connection between the phone and a bridge | connection (when the BLE link is meant) |
| Node | Anything on the CAN bus: controller, BMS, Express, IO board | device |
| Topology | The list of controllers found on a link | setup, config |
| Session | Everything the service runs while connected: link, polling, derivations, alerts, recording | connection |
| Sample | One parsed reply from one controller, with a timestamp | packet (packet = framed bytes on the wire), tick |
| Telemetry frame | The combined snapshot the service sends to the UI (≤ 30 Hz) | tick, update |
| Speed source | Where speed comes from: `vesc`, `erpm`, `gps`, `auto` | mode |
| Ride | A logged trip from start to stop | trip, session, log |
| Run | One speed-test attempt (0-60 etc.) | test, drag |
| Bracket | A speed or distance target inside a run (e.g. 0-60 km/h) | split (split = the time for a bracket) |
| Capture | The stored data container of a ride or a run | recording |
| Chunk | One second of samples of one stream, compressed | blob, batch |
| Sag | Voltage drop below the rest reference under load | drop, droop |
| SoC | State of charge in % shown to the user | battery level |
