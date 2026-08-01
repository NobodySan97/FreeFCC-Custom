# Flight Controller Parameter Table: Index-Based Path and LED Group

Date: 2026-07-26. Hardware Testbed: DJI Air 3S + RC Pro 2 (`rc520`), FreeFCC Custom 1.5.45, Aircraft S/N `1581F895C259P007J3RC`. All data below gathered read-only via `wire_exchange` on wrapped port `40007`; no parameter writes were executed.

## Why Use Index-Based Paths When Hashes Work?

A parameter hash is a function of its full name (`hash(name + "_0")`, `h = ((h << 8) | byte) mod 0xfffffffb`). Names are model-specific, so for a new model, hashes cannot be guessed. Index-based access removes this dependency: `03:E1` returns the parameter name, while `03:E2`/`03:E3` read and write values by index without using hashes at all.

## Aircraft Responses

| Command | Result | Evidence Level |
|---|---|---|
| `03:E0` `table_no=0` | `status=0`, `entries_crc=0xd0d68370`, **`entries_num=1513`** | `CONFIRMED` |
| `03:E1` `table_no=0`, `param_index=N` | Metadata + name; 755 entries retrieved | `CONFIRMED` |
| `03:F7` hash `a259ceed` | `min=0`, `max=255`, `def=0xEF`, name `forearm_led_ctrl` | `CONFIRMED` |
| `03:F8` hash `a259ceed` | `00 a259ceed 00` — lamp OFF | `CONFIRMED` |
| `03:F0` (2015 index format) | 0 responses in 8 attempts | `NEGATIVE` |

The flight controller uses the 2017 table format (`E0/E1/E2/E3`), and does not respond to the flat 2015 index format. Hash-based access (`F7/F8/F9`) operates in parallel with it.

## `03:E1` Response Format

```
u16 status | u16 table_no | u16 param_index | u16 type_id | u16 size
u32 limit_def | u32 limit_min | u32 limit_max | ASCII name
```

The name starts at offset 22 and arrives as a pair—short and full separated by `|`, e.g., `forearm_led_ctrl|g_config.misc_cfg.forearm_lamp_ctrl`. The full form yields the hash `0xedce59a2` (`a259ceed` on the wire), used by `led_on.json` / `led_off.json`.

`type_id` matches public specifications: `0=u8 1=u16 2=u32 3=u64 4=i8 5=i16 6=i32 7=i64 8=f32 9=f64`; for `f32`, limits are read as float.

## Table 0 LED Group

| Index | Type | Default | Min..Max | Name |
|---:|---|---:|---|---|
| 361 | u32 | 0 | 0..0xFFFFFFFF | `ext_led_ctrl` |
| 362 | u8 | 239 (`0xEF`) | 0..255 | `forearm_led_ctrl\|g_config.misc_cfg.forearm_lamp_ctrl` |
| 363 | u32 | 255 | 0..255 | `param_single_ledctrl_enable` |
| 364 | u32 | 21 | 0..255 | `param_single_ledctrl_exist` |
| 365 | u8 | 1 | 0..1 | `param_hidden_ledctrl_enable` |
| 366 | u8 | 1 | 0..1 | `param_hidden_ledctrl_exist` |

`0xEF` is the factory default mask for `forearm_led_ctrl`, not a plain "ON state". The application interprets exact `0x00` as OFF and exact `0xEF` as ON; any other value displays as `PARTIAL`.
