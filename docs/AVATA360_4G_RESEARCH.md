# 4G Activation Research: Avata 360 (`WA530`) & RC2 (`rc331`)

Date: 2026-07-22.

This document collects empirical observations and protocol analysis regarding experimental 4G cellular dongle activation frames on DJI smart controllers.

## Summary

- Experimental 4G activation writes are passed to the local abstract DUSS socket `@/duss/mb/0x205`.
- Reachability and write completion to `@/duss/mb/0x205` do not guarantee physical 4G modem connection without a paired cellular modem and valid SIM subscription.
