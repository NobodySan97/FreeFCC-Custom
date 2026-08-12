# Инвентаризация eMMC-дампов дронов DJI (homesrv, Toshiba 6TB)

Дата: 2026-08-12. Источники: дампы `/tmp/toshiba/dumps_emmc_dji/`
(sshfs → homesrv:/mnt/toshiba_6tb), извлечённые разделы и все per-dump
отчёты — локально `/home/danik/Projects_and_coding/.scratch/emmc/` и
зеркально на homesrv в `dumps_emmc_dji/_analysis/` (sha256 совпадают).
Мастер-отчёт: `.scratch/emmc/INVENTORY.md`; per-dump:
`INVENTORY_<dump>.md` (+ `*_detailed.md`/`*_addendum.md`). Сканер
упакованных DUML-команд: `.scratch/emmc/cmdtable_scan.py`.

Документ фиксирует, что дали дампы для закрытия «честных пробелов»
`DUML_COMMAND_AUDIT.md` и для 4G/LTE-картины проекта.

## Матрица идентичности устройств

| Дамп | Устройство (доказательство) | DJI FW | Android / arch | Примечания |
|---|---|---|---|---|
| EMMC_Data_3 | **v1_wa341 (RC Pro)** — build fingerprint `v1/v1_wa341/...:9/PD1A.180720.031/21082` | 10.00.05.81 (2025-06-04) | 9 / arm64 | squashfs повреждён в обоих A/B слотах на одном логическом смещении → dji_wlm/dji_lte этого билда невосстановимы |
| EMMC_Data_4 | **legacy Ambarella-эра** — нет GPT, plaintext kernel @0x800000, ext4 SB @0x50000000 (GD corrupt), FAT32 @0x52000000 | unknown | pre-Android Linux | современного DUML-стека нет; дамп с XGecu T56 |
| EMMC_Data_5_64gb | **eagle3_wa341** — `e3/eagle3_wa341/...:9/.../23701` | 10.00.08.02 (2025-09-16) | 9 / arm64 (A55) | LTE через внешний dongle; userdebug с su, unlock_uav/burn_otp/p7_sign |
| EMMC_Data_M3 | **e1e_wm260 (Mavic 3)** — `e1e/full_e1e_wm260/...:6.0/MDB08M/1384` | 10.00.81.36 (2022-12-07) | 6.0 / **arm32** | pigeon-модем fw в vendor `/modem_firmware/pigeon/` |
| EMMC_Data_M3_2 | **eagle2_wm260_native** — `eagle2/...:9/PD1A.180720.031/1384` | 10.00.81.36 | 9 / arm64 | тот же билд, 64-битный стек: dji_lte, liblte.so, dji_wlm_slave |
| EMMC_Data_M4 | **v1_wa341 (RC Pro)** — `v1/v1_wa341/...:9/.../18681` | 10.00.02.37 (2025-02-07) | 9 / arm64 | squashfs = perception-rootfs (dji_perception 68MB) |
| air3_1 | **e3t_wa233 (Air 3)** — `e3t/e3t_wa233/...:9/.../20577` | 10.08.09.64 (2025-05-09) | 9 / arm64 | полный стек: dji_wlm, dji_lte, dji_sdrs_agent |
| air3_2 | **e1e_wa233** — `e1e/full_e1e_wa233/...:6.0/MDB08M/15773` | 10.08.09.57 (2024-09-02) | 6.0 / **arm32** | dji_wlm бинаря нет, только libwlm.so |
| Mavic3_emmc_2 | **eagle2_wm260, второй экземпляр Mavic 3** | 10.00.81.36 | 9 / arm64 | A/B слоты = тот же билд; gimbal-слоты байт-идентичны M3_2 |

У всех дампов разделы `normal`/`rtos`/`scp`/`tos` — IM*H/PRAK/TBIE-encrypted
(plaintext-строк нет). `system`/`vendor` — чистый ext4.

## Методологическая находка (критично для всех будущих сканов)

DJI пакует DUML-команду как **LE32 `(cmd_set<<16)|cmd_id`**:
09:EC = байты `ec 00 09 00` (0x000900EC), 10:58 = `58 00 10 00`,
09:27 = `27 00 09 00`. Все ранние сканы искали порядок `ec 09 00 00` /
сырые `58 10` и давали ложные негативы. Ресскан с правильной упаковкой
дал позитивы ниже.

## Результаты кросс-проверок (пробелы аудита c/d)

### 09:EC (silence SDR) — CONFIRMED, с атрибуцией отправителя

- WM260 `dji_wlm` (EMMC_Data_M3, arm32, Build ID
  `936e791770df6160eaca9ba2d4bb52e3`): константа `0x000900EC` в literal
  pool @file 0x388b8; грузится `ldr r0,[pc,#0x1f4]` @0x386c2 внутри
  функции **`wlm_lk_ctrl_set_sdr_param`** (имя-строка @0x793d0, модуль
  `hardware/dji/service/wl_manager/modules/link_ctrl`, лог-линия 347).
  Код строит исходящее сообщение `{cmd@+0, u16@+6, 0x40@+8, len=2@+0x10,
  payload_byte@+0x14}` — согласуется с payload `00 03` (2.4G) /
  `00 04` (5.8G) из находок RM510/WA530.
- Silence-строки (`silence sdr 2.4G/5.8G`, `reject silence all band`)
  есть также в dji_wlm air3_1 (@0xaa60f/0xaa6f2) и eagle3 (Data_5).
- Handler-сторона (запись регистра) в проверенном plaintext-корпусе не
  локализована. Кандидаты: зашифрованная firmware transmission-MCU, main-SoC
  loopback/SDR-driver или RC-сторона; текущие артефакты не позволяют выбрать
  один из них.
- Caveat: id16 0x09ec в `.rodata` descriptor-таблицах
  (`{len, id16, rel32 handler, protobuf-desc}`) в dji_network/dji_lte —
  module-local enum-слоты, НЕ DUML-команды; в доказательства не идут.

### 10:58 — ложный кандидат на статическую диспетчеризацию (wa341)

- Immediate-сканы отрицательны везде (6 платформ).
- ~~Packed `0x00100058` как case-value в switch-таблице~~ — **ОПРОВЕРГНУТО
  2026-08-12** (см. `PERCEPTION_1058_CASE_BODIES_20260812.md`): «таблицы»
  (M4 @0xbe4c90, Data_5 @0x439f20) — это misaligned (+4 байта) чтение
  binary-search таблицы `.eh_frame_hdr` (данные C++ unwind'ера), а не
  DUML-диспетчеризация. Ни одного xref из кода; «labels» — pc_rel соседних
  FDE-записей; сетка {pc_rel, fde_rel} идеально отсортирована (86897/25257
  записей). Ghidra-декомпиляция по этим адресам: mutex-синхронизация
  depth-буфера (M4) и C++ teardown (D5) — командной логики нет.
- **Итог: статический обработчик 10:58 не найден в проверенных userspace-
  бинарях M4, Data_5 и wm260.** Это согласуется с негативами по WA530/WA234,
  но не исключает runtime/data-driven регистрацию или реализацию только в
  зашифрованной firmware.
- Методологическое правило: кандидатов в «case-таблицы» от побайтового
  поиска проверять на вхождение в `.eh_frame_hdr`/`.gcc_except_table`,
  сортированность «labels» и наличие xref.

### 09:27 / 0xffff0063

- M4 dji_perception @0x7d95a4 и M4 dji_autoflight @0x7350f0 содержат
  **байт-идентичную таблицу ~30 id команд cmd_set 0x09** (включая
  `0x00090027`) — 09:27 является first-class командой 09-го сета в
  wa341 userspace; назначение таблицы (whitelist/mask) — найти xref в IDA.
- До расшифровки pigeon `0xffff0063`/`0xffff0048` дали **0 попаданий** в
  исходном plaintext-корпусе M3/M3_2/Data_5. Последующий анализ
  `cp.img.dec_RTOS.bin` нашёл обе константы как ID межъядерных register-access
  сообщений (не DUML); см. `PIGEON_RTOS_DUML_20260812.md`.

## Статус пробелов DUML_COMMAND_AUDIT

- **(a) 03:AF flight-MCU handler — НЕ закрыт.** Plaintext-прошивок
  полётного MCU нет ни в одном дампе. Косвенное подтверждение роута:
  M3 dji.json `system/30`,`system/36` → target `flight` через ICC
  `/dev/icc_dev:ap0-mcu0-1.0`/`mcu0-ap0-1.0`.
- **(b) 09:27/06:8C sparrow2 handler — НЕ закрыт, блокирует TBIE.**
  Два независимых набора sparrow2 (air3_1, M4; третий в Data_3) остаются
  IM*H+PRAK/TBIE. Pigeon WM260 уже расшифрован, но поиск констант не доказал
  ни наличие, ни отсутствие dynamically registered handler'ов. Новый материал:
  полные plaintext
  nvram/SDRH-конфиги (amt/cp_nvram/ipc/pwr/rf_nvram/share_info) с трёх
  платформ и незашифрованный `lens.fw` (M3_2, Data_5).
- **(c) 10:58 — НЕ закрыт.** «Switch-таблица» опровергнута 2026-08-12
  (`.eh_frame_hdr` misaligned, см. выше). Статический обработчик не найден ни
  в одном из 6 проверенных userspace-бинарей; это negative evidence, а не
  доказательство глобального отсутствия команды.
- **(d) 09:EC — закрыт по отправителю** (`wlm_lk_ctrl_set_sdr_param`,
  dji_wlm WM260); handler — в зашифрованной SDR-firmware.

## LTE / 4G — новые находки из дампов

- **Mavic 3: LTE-стек живёт на eagle2 SoC** (M3_2): `dji_lte` (Build ID
  `75801eed87cf8b292cb7b61e64f189bb`) + `liblte.so` + `dji_wlm_slave`.
  Модем — **Quectel EC2x AT** (`AT+QICSGP`, `AT+QSIMSTAT`,
  `AT+QENG="servingcell"`, `AT+QFOTADL` modem-FOTA, `AT+TRISIMGET/SET`,
  `at+qnvfw` NV-write включая `lte_connection_control`), мультивендор:
  Quectel (/dev/ttyUSB2-4), **Fibocom** (`udhcpc -i fibocom_net`),
  **Huawei** (`dhcpcd -z huawei_net`). Cell-geolocation upload на
  `https://mydjiflight.dji.com/api/cell/location?mcc=…&mnc=…&lac=…&ci=…`.
  DUML-over-LTE форвардит камерные команды как cmd id
  0102/010a/027c/03aa/0401/0406/0650/2270. Dongle-FOTA через
  `/dongle_firmware/`, `dongle_fw_info_%d.json`.
- **e1e (M3) сторона**: dji_wlm = WLM-мастер гибридного SDR/LTE/WiFi
  (`hybrid_lte_sdr`, `use_sdr/use_lte`, `agt_rpt/LTE`); pigeon-скрипты
  (`burn_otp_pigeon_0.sh`, `modem_ddr_load.sh`, `test_modem_serial_link.sh`);
  `modem_info.sh` ходит DUML cmd_set 09 к SDR-модему через
  `dji_mb_ctrl -g 9`.
- **eagle3 `lte_cfg.json`**: `peer_lte_host_id 0x0e06`,
  `local_lte_test 0x0807`; **`lte_black_list: ["US","TH"]`**,
  `single_frequency_cn_list: ["JP","RU","IL","UA","KZ"]`, облачный
  ban band 7 для GB; донглы `dji_mini`/`ig833` (hot-plug, питание gpio4);
  relay/p2p_ipv46/p2p_stun/proto_tcp с ролями A/G; debug-хуки
  `skip_dongle_check/expire`.
- WLM host ids (wm260 и wa341 одинаково): sdr_agent **0x0900**,
  lte_agent **0x0806**, local_wlm 0x0907, peer_wlm 0x0e07.
- Для статанализа таблицы cmdset 0x51 готовы три dji_lte: air3_1
  (sha256 `4c95f895…`), Data_5 (`2034374a…`), M3_2 (`0d53d2d0…`) —
  лежат в `_analysis/`.

### Live feedback: Mavic 4 Pro + Dongle 2 + RC2

- 2026-08-12 тестировщик upstream FreeFCC v1.5.5 сообщил, что после ручного
  ввода aircraft serial 4G включился и DJI Fly показывает состояние **ON**:
  [issue #35, комментарий](https://github.com/doesthings/FreeFCC/issues/35#issuecomment-5263590483).
  По уточнению владельца этого checkout, связка — Mavic 4 Pro + Dongle 2 + RC2.
- Проверенный upstream build `v1.5.5` (`597157bd52120dfeb9677f79a8ad46b6027ce8dc`)
  отправляет sweep 51:00..51:7F с payload `00 00 01 || ASCII serial`; таким
  образом в нём присутствует и кандидат 51:1A. Отзыв подтверждает результат
  **всего batch**, но сам по себе не изолирует 51:1A от остальных 127 кадров.
- Уровень evidence: **OBSERVED** — состояние ON в DJI Fly после отправки;
  **не проверено** — полёт, реальный LTE data path, throughput/failover и
  возможность отключения. Это первая положительная hardware-проверка активации,
  но не полная валидация Enhanced Transmission.

## Реестр ключевых ELF (sha256 / Build ID) — в мастер-отчёте

Полные таблицы dji_sys, dji_perception, dji_wlm/libwlm.so,
dji_sdrs_agent/dji_lte/dji_mb_ctrl/libduml_frwk по всем дампам — в
`.scratch/emmc/INVENTORY.md` §«Key ELF registry» (с хешами и Build ID
каждого бинаря). Здесь не дублируются.

## Открытые хвосты

1. EMMC_Data_4 (Ambarella): отдельная carving-сессия, если понадобится —
   скан с шагом 1MB в 0x0–0x50000000, парсинг Ambarella PT.
2. EMMC_Data_3 squashfs: **окончательно невосстановим** (A и B слоты
   повреждены одинаково — источник или съём образа).
3. ~~Case-тела 10:58~~ — **снято 2026-08-12**: «таблица» была
   `.eh_frame_hdr` (см. `PERCEPTION_1058_CASE_BODIES_20260812.md`).
4. Потребитель 09:xx-таблицы (M4 dji_perception 0x7d95a4 /
   dji_autoflight 0x7350f0) — xref в IDA.
5. ~~dji_lte cmdset-0x51~~ — **снято 2026-08-12**: таблицы трёх дронов
   разобраны, все подмножества RC Pro 2 v576
   (см. `LTE_CMDSET51_DRONE_SIDE_20260812.md`).
6. `system_2`/`vendor_2` остальных дампов (кроме Mavic3_2) не проверены —
   могут нести вторую версию FW.

## Шифрование TBIE/STBE — итог расследования 2026-08-12

- Поколение 2022 (WM260/eagle2) **расшифровано полностью** публичными
  ключами TBIE-2020-02/TBIE-2021-08 (см. `TBIE_DECRYPTION_PATH_20260812.md`).
- Механика KDR→TBIE разобрана по расшифрованному TZOS (OP-TEE 3.2.0 +
  CC712): ключи выводятся SP800-108 KDF от HW OTP внутри TEE и NW не
  покидают (см. `TZOS_KDR_ANALYSIS_20260812.md`).
- **Ключи, адресуемые как TBIE-2022+/STBE (sparrow2, TA wa341/eagle3
  2024–2025), статически не извлечены**: подходящих userspace-кандидатов не
  найдено, p7_sign — тупик
  (PKCS#7-подписант, см. `P7_SIGN_ANALYSIS_20260812.md`), dev-ключи
  eagle2 к 2024+ блобам не подходят. Один и тот же fourcc `STBE` встречается
  в AP/TEE/modem-контейнерах eagle3, но это **не доказывает**, что фактический
  AES-ключ или аппаратный KDR-root у этих SoC общий. Оставшиеся векторы требуют
  живого устройства
  (TZDRAM через syscall-поверхность / LCS-проверка на userdebug) —
  **недоступны, железа нет**.
- Из расшифрованного pigeon RTOS (WM260) извлечено: межъядерные
  регистровые сообщения sparrow2 `0xffff0063/0xffff0048` с диспетчером
  и обработчиком; статические представления 09:27/09:EC/06:8C в pigeon не
  найдены, но динамическая регистрация DUML-callback ещё не протрассирована;
  03:AF не подтверждён
  (см. `PIGEON_RTOS_DUML_20260812.md`).
