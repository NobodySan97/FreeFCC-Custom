# DJI Cellular Modem Live Hardware Map

Date: 2026-07-26.

Analysis of cellular modem daemon and DUML command set `0x51` (`WLM`) handling across DJI smart controllers.

## Summary

- DUML command set `0x51` (`WLM`) handles wireless link management and LTE modem state querying.
- `51:14` retrieves modem status; `51:1A` controls cellular dongle registration.
