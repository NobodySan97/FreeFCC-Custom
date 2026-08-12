# Путь расшифровки TBIE/IM*H/PRAK-контейнеров прошивок модема DJI

Дата: 2026-08-12. Исполнитель: agent-19 (стратегическое расследование).
Источники: локальные извлечения eMMC-дампов `/home/danik/Projects_and_coding/.scratch/emmc/`,
Ghidra 12.1.2 headless, публичный инструментарий OGs `dji-firmware-tools`
(`dji_imah_fwsig.py`, master, fetched 2026-08-12).

## 0. Главный результат (TL;DR)

1. **Граница расшифровки — НЕ в Linux userspace дрона.** AP лишь скармливает сырые
   блобы модему через `brload`/proprietary-`fastboot`; верификация подписи (PRAK,
   RSA) и расшифровка (TBIE, AES-128-CTR) происходят **внутри модемного SoC**
   (bootrom → CMPU), ключи — в OTP/eFuse модема (`burn_otp_pigeon_0.sh` шьёт
   `cmpu_otp`, `get_upload_pigeon_0.sh` шьёт `cmpu_kdr` = key derivation root).
2. **Ключи для поколения 2022 (WM260/eagle2, Mavic 3) — публичны и у нас уже всё
   расшифровалось**: `TBIE-2020-02` (pigeon-модем) и `TBIE-2021-08` (eagle2:
   normal/scp/tos/gimbal, ss_dsp, dsp0_pdmem). Контрольные суммы `encr_cksum`/
   `plain_cksum` из заголовков сошлись — расшифровка доказана, не предполагается.
3. **Поколение 2024–2025 (sparrow2 на wa341/wa233, eagle3, TA) — ключ `TBIE-2022+`
   / новый fourcc `STBE`; в проверенных userspace-кандидатах дампов он не
   найден** (перебор всех
   20+ публичных 16-байтных ключей по oracle `plain_cksum` — ни одного совпадения;
   AP-side хранилища ключей исследованы и не содержат TBIE-ключей).

## 1. Карта: кто трогает зашифрованные блобы

### 1.1 Модемный путь (pigeon на WM260, sparrow2 на wa341/wa233)

`EMMC_Data_M3/root/scripts/modem_ddr_load.sh` (полный текст прочитан):
- bootrom модема виден как USB `ProdID=0040` → `brload /vendor/modem_firmware/pigeon/bootarea.img`;
- далее boot loader `ProdID=d00d` → серия `fastboot flash modem-*`:
  `modem-pub_key` (шьётся **bootarea.img как публичный ключ**), `modem-share_info`
  (info.img), `modem-rta7_nvram`, `modem-rf_nvram`, `modem-amt`, `modem-package`
  (cp.img), `modem-normal` (normal.img).

Вывод: **AP не парсит и не расшифровывает IM*H** — блобы уходят в модем как есть.
Парсер заголовка IM*H, верификатор PRAK-подписи и TBIE-дешифратор находятся в
bootrom/загрузчике модема. Доверенная цепочка: OTP (cmpu_otp/cmpu_kdr) → bootrom →
bootarea (BLLK bootloader, подписан PRAK) → normal/cp (подписаны PRAK, зашифрованы
TBIE).

`burn_otp_pigeon_0.sh`: `/tmp/encrypt/fastboot flash cmpu_otp /tmp/encrypt/otp.sec` —
прожиг OTP на заводе. `get_upload_pigeon_0.sh`: шьёт `pro_prak.pub.mon` как
`cmpu_ver` и `cmpu_kdr` (файлы `/tmp/encrypt/*` — заводские, в дампах отсутствуют).

`test_modem_serial_link.sh`, `modem_info.sh` — диагностика по DUML/UART, к
криптографии отношения не имеют.

### 1.2 AP-side верификаторы

| Бинарь | Дамп | Роль |
|---|---|---|
| `dji_verify` (arm32, 30100 B, sha256 `649cf16d…c81d6`, GCC 4.7) | M3, air3_2 (байт-идентичны) | Полная локальная реализация verify+decrypt IM*H, ключи в `.rodata` (см. §3) |
| `dji_fw_verify` / `dji_fw_load` | M3, M3_2, Data_5 | CLI-обёртки над `libfw_util.so` (`-d symkey=KeyName`, `-v pubkey=KeyName`) |
| `libfw_util.so` (arm64, 68248 B) | M4 (`sqroot/system/lib64`), Data_3 | **Шим**: всё делегирует в `libfw_util_ca.so` |
| `libfw_util_ca.so` (68112 B) | M4 | TEE Client API: `TEEC_InitializeContext/OpenSession/InvokeCommand` (`libteec.so`) — верификация/расшифровка внутри TrustZone TA |
| TA `vendor/ta/*.ta` (M4, Data_5) | wa341/eagle3 | Сами зашифрованы IM*H (`name=djita type=TZTA enc=TBIE/STBE, sig=384`) — chicken-and-egg: ключ у TEE |
| `libdji_crypto_lib.so` (243372 B, M3) | все дампы | Чистый mbedtls (AES/RSA/SHA/x509), никакой TBIE-специфики |
| `libsecure.so` (Data_5) | eagle3 | RPMB, license, anti-rollback; TBIE не касается |
| `dji_mb_ctrl`, `dji_upgrade`, `dji_update_engine`, `modem_dual_upgrader` | все | Нулевые хиты по TBIE/PRAK/IM*H/PUEK/scram — прошивку в MCU шлют как opaque DUML-пейлоад |

Полнота перебора: `grep -rlF 'TBIE'/'PRAK'/'IMaH'/'IMRH'/'IM*H'` по всем
извлечённым файлам всех дампов — магики встречаются только в (а) самих блобах,
(б) `dji_verify`, (в) зашифрованных TA, (г) *.enc конфигах ML (`vendor_etc_ml`).

### 1.3 QFirehose / Quectel

`QFirehose`, `Quectel_upgrade_tool`, `quectel-qmi-proxy` (M3_2) обслуживают
внешние LTE-донглы Quectel/Fibocom/Huawei (INVENTORY §LTE). К TBIE/IM*H отношения
не имеют — шьют Qualcomm/Quectel-форматы донгла. (В локальной извлечении бинарей
нет; вывод по INVENTORY_EMMC_Data_M3_2.md §6.)

## 2. Формат контейнера IM*H (подтверждён двумя независимыми источниками)

Источник A: публичный `dji_imah_fwsig.py` (OGs). Источник B: декомпиляция
`dji_verify` (Ghidra, полный листинг:
`FreeFCC/.scratch/tbie_20260812/dji_verify_decomp.txt`).

Заголовок (192 байта, LE):
`IM*H` magic, `header_version` (0/1/2), `size`, `header_size`, `signature_size`
(256 = RSA-2048; 384 = RSA-3072; выбор зависит от платформы и
контейнера: в корпусе 2022 встречаются и 256 на WM260, и 384 на eagle2),
`payload_size`, `target_size`, os/arch/compression/anti_version, `auth_alg`,
**`auth_key` fourcc (`PRAK`)**, **`enc_key` fourcc (`TBIE`; у Data_5 TA — `STBE`)**,
**`scram_key[16]` — зашифрованный мастер-ключом сессионный ключ**, `name[32]`,
`type[4]`, `version`, `date`, `encr_cksum`, `plain_cksum`, `chunk_num`,
`payload_digest[32]` (SHA-256). Далее `chunk_num` записей по 32 байта
(`id[4], offset, size, attrib, address u64, reserved[8]`), затем RSA-PSS/SHA-256
подпись заголовка (для v2018+).

Криптография (v2, из `imah_get_crypto_params` + `FUN_00010c70` в dji_verify):
1. `crypt_key = AES-128-ECB-decrypt(TBIE_key, scram_key)` — разворот сессионного
   ключа (в dji_verify это `dji_image_decrypt_scram_key`, строка @0x130e6,
   xref из `FUN_00010c70`).
2. Чанки с `attrib&1==0` расшифровываются **AES-128-CTR, единый cipher на все
   чанки** (счётчик не сбрасывается, nonce=0), каждый чанк допаджен до 32 байт,
   паддинг тоже проходит через cipher.
3. Oracle без знания приватных ключей: `encr_cksum = 2^32 − sum32(header_nosum ‖
   chunk_headers ‖ payload)`; `plain_cksum = sum32(decrypted chunks с паддингом)`.
   Именно по ним доказана корректность каждой расшифровки ниже.

Декомпилированные функции dji_verify (Ghidra-адреса, image base 0x10000):
`FUN_00010b84` = `dji_image_verify_header` (RSA-проверка, sig 0x100),
`FUN_00010c70` = `dji_image_decrypt_scram_key` (ECB-разворот),
`FUN_00010d8c` = `dji_image_verify_update` (поточная обработка чанков),
`FUN_00011304` = SHA-1 block, `FUN_000115f0`+`FUN_00011798` = SHA-256,
`FUN_00011224` = keymgr init (memcpy 3×0x20c из .rodata), `FUN_00011270` =
keymgr get-by-name.

## 3. Ключи в `dji_verify` (arm32) — что реально зашито

Keymgr отображает fourcc → статический ключ (literal pool @file 0x12dc:
`RRAKGFAKPRAKPUEK`; указатели @0x12ec):

| fourcc | Тип | Расположение | Идентификация |
|---|---|---|---|
| RRAK | RSA-2048 pub, 0x20c=524 B (ImgRSAPublicKey64) | file 0x3596 | **== публичному RRAK v1 (OGs, 2017)** — modulus совпал побайтно |
| PRAK | RSA-2048 pub | file 0x338a | **== PRAK-2017-12** (Provisioning RSA Auth Key v3) |
| GFAK | RSA-2048 pub | file 0x317e | **== GFAK v1** (Geofence Auth Key) |
| RUEK | AES-128 | file 0x315e | `9cdaf6274ecb78f3eddce526bcec66f8` **== публичному RUEK (2017)** |
| RIEK | AES-128 | file 0x316e | `f169c0f38b2d9adc65ee0c57833294e9` **== RIEK-2017-01** (R&D dev key) |
| PUEK | AES-128 | указывает в .bss (0x1863c) — заполняется рантайм-копией, в файле нет отдельного тела | — |

Вывод: dji_verify несёт **только legacy dev/production-ключи 2017 года** и
RSA-2048 PRAK-2017-12. **TBIE-ключа в нём нет** — то есть даже штатный AP-side
верификатор физически не может расшифровать модемные TBIE-образы; это делает
только модем (OTP) / TEE (для системных разделов).

## 4. Результаты расшифровки (доказанные контрольными суммами)

Инструмент: `FreeFCC/.scratch/tbie_20260812/tbie_try2.py` (standalone,
семантика байт-в-байт из dji_imah_fwsig.py, RSA-верификация пропущена,
oracle = encr/plain cksum). Логи: `tbie_20260812/batch_out/*.log`.
Перебраны все 84 файла с магиком IM*H во всех дампах
(`tbie_20260812/imah_blobs.txt`).

### 4.1 Успех — поколение 2022 (11 образов)

| Образ | Ключ | Содержимое (доказанное) |
|---|---|---|
| M3 `pigeon/normal.img` (16.9MB) | TBIE-2020-02 | чанки TZOS (ARM code @0x1f600000), **KERN = Linux zImage ARM**, **LRFS = gzip rootfs (21.8MB)**, LDTB = DTB `d00dfeed` |
| M3 `pigeon/cp.img` (5.7MB) | TBIE-2020-02 | RTOS (3.5MB, полные исходные пути `common/P1/src/dji_frame_buf.c`, `dji_srv_audio.c`…), M0IM, X164/X450/X451 (Xtensa DSP), PHYS, RFNV, SHRI |
| M3 `pigeon/bootarea.img` @0x400 | TBIE-2020-02 | BLLK (155KB ARM bootloader, branch vectors) + BLFA |
| M3 `pigeon/bootarea.img` @0x2D000 | TBIE-2020-02 | BDIF: plaintext `require board=pigeon_wm260` |
| M3 `pigeon/info.img` | TBIE-2020-02 | SHRI: TLV карта адресов флеша |
| Mavic3_2 `normal.img` | TBIE-2021-08 | KERN/LRFS/LDTB + 13×DTB (eagle2 Linux) |
| Mavic3_2 `tos.img` | TBIE-2021-08 | LATF + **TZOS = OP-TEE OS с ARM CC712 crypto** (`/home/ryan.dong/workspace/OPTEE/e2_cc712/sw-cc712tee-1.2.0.157/...cc_hal.c`, `cc_rnd.c`, `pki.c`) |
| Mavic3_2 `scp.img` | TBIE-2021-08 | BLM0 (SCP firmware, ver 0x02030000) |
| Mavic3_2 `gimbal.img` + `gimbal_2.img` | TBIE-2021-08 | GIMB (572KB, `DJI_LOG_V3`, `dji_host_init`…); A/B байт-идентичны, один scram key |
| M3_2 `ss_dsp0/1/2.fw`, `dsp0_pdmem.img` | TBIE-2021-08 | CODI/DATI/CODE/DATE — Hexagon DSP @0xf0000000/0x39e00000 |

Файлы на диске: `<образ>.dec_<CHUNK>.bin` рядом с исходниками +
`bootarea.img.dec_bootloader_BLLK.bin`, `…dec_boardinfo_BDIF.bin`,
`info.img.dec_shareinfo_SHRI.bin`. Scram-ключи — в логах batch_out.

`Mavic3_2/normal_2.img` (B-слот): тот же ключ TBIE-2021-08 и тот же заголовок,
что у A-слота, но `encr_cksum` не сходится — A/B различаются с байта 0x164C001
(runtime-перезаписанный хвост раздела, см. INVENTORY). Не ошибка парсера.

### 4.2 Не расшифровалось — поколение 2024–2025 (73 образа)

- **sparrow2** (air3_1 2025-05, M4 2025-02, Data_3 2025-06): ap/cp/dsp/l1c/scp.img,
  bootarea.img@0x400 — `enc=TBIE`, sig=384 (RSA-3072), даты 20241227–20250604.
  **Ни один из 20+ публичных 16-байтовых ключей (все TBIE/UFIE/PUEK/RIEK/RUEK/RREK
  из dji-firmware-tools master) не подошёл** по plain_cksum.
- **ss_dsp*.fw** новых платформ (air3_2 2024-09, Data_3, Data_5 eagle3,
  M4 dspf) — то же.
- **TA** (M4 `vendor/ta/*.ta`, Data_5 `ta/*.ta`): `enc=TBIE` (M4) и **новый
  fourcc `enc=STBE`** (Data_5, eagle3, 2024-11) — ключа нет даже в публичной
  номенклатуре.

Итог: смена TBIE-ключа произошла между 2022-12 (10.00.81.36) и 2024-09
(10.08.09.57). Публичная таблица заканчивается на TBIE-2021-08.

## 5. Реалистичность извлечения ключа TBIE-2022+/STBE — честная оценка

Доказано отсутствие ключа в userspace: AP-side dji_verify (§3) несёт только
2017-ключи; wa341 путь verify/load целиком уходит в TEE (`libfw_util.so` →
`libfw_util_ca.so` → `TEEC_InvokeCommand`, строки TEEC_* @libfw_util_ca.so);
TA зашифрованы тем же новым ключом (самоссылка). libdji_crypto_lib/libsecure/
dji_mb_ctrl/dji_upgrade ключей не содержат (строковый аудит §1.2).

Возможные пути, по убыванию реалистичности:

1. **`p7_sign` на userdebug Data_5 (eagle3) — проверка закрыта.** Файл и
   его userspace-цепочка точечно извлечены и разобраны; см.
   [`P7_SIGN_ANALYSIS_20260812.md`](P7_SIGN_ANALYSIS_20260812.md). `p7_sign` —
   PKCS#7-обёртка для LTE/eSIM-аутентификации; в проверенном
   userspace симметричный TBIE/STBE-ключ не найден. Внутренний
   key path зашифрованной TEE TA остаётся непроверенным.
2. **CMPU OTP sparrow2** — аппаратный путь (glitch/JTAG/производственные
   интерфейсы модема). Дорого, но это то, откуда ключ реально читается
   (`cmpu_kdr` flash через fastboot — заводская процедура, требует
   `pro_prak.pub.mon`, которого нет в дампах).
3. **OTA-пакеты DJI 2024+**: IM*H-модули для sparrow2 прилетают на устройство и
   расшифровываются его TEE — ключ там же; но сами OTA могут содержать
   whitebox/обфусцированный дешифратор в RC-прошивках старых RC (как было с
   PUEK-2017-09 whitebox). Стоит проверить свежие ревизии dji-firmware-tools и
   форков на предмет TBIE-2022+.
4. **Расшифрованный `tos.img` (OP-TEE eagle2, 2022)** — уже доступен (§4.1):
   анализ его TA-loader'а и CC712 key ladder покажет механику KDR→TBIE derive,
   но сам ключ wa341-2024 там не лежит (другая платформа и эпоха).
5. Перебор/инверсия: scram_key разворачивается AES-128-ECB — known-plaintext
   атаки на AES-128 не существует; брутфорс исключён.

## 6. Что это закрывает

- **09:27/06:8C (sparrow2 handler)** — всё ещё заблокировано ключом TBIE-2022+
  (gap (b) частично: для WM260 pigeon теперь есть полный plaintext CP/AP стека —
  `dji_srv_audio.c`, diag, bs — обработчики DUML cmd_set 09 на модеме можно
  искать прямо в `cp.img.dec_RTOS.bin`, 3.5MB ARM кода с исходными путями).
- **03:AF (flight MCU)** — flight-прошивки в этих дампах нет вообще (уже
  установлено). Механика IM*H открыта, и flight-блоб поколения
  ≤2022 можно сразу пробовать дешифровать известными TBIE-2020-02/2021-08,
  но год сам по себе не гарантирует совпадение `enc_key` и варианта ключа.
- Побочный результат: `Mavic3_2/tos.img.dec_TZOS.bin` — полный OP-TEE OS образ
  eagle2 (source paths ryan.dong, CC712) — прямая мишень для анализа
  TEE-цепочки доверия и TA ABI (`fw_util_verify_*`).

## 7. Артефакты расследования

- `FreeFCC/.scratch/tbie_20260812/tbie_try2.py` — standalone дешифратор IM*H/TBIE (oracle по cksum)
- `FreeFCC/.scratch/tbie_20260812/dji_imah_fwsig.py` — эталон OGs (fetch 2026-08-12)
- `FreeFCC/.scratch/tbie_20260812/dji_verify_decomp.txt` — полная декомпиляция dji_verify (2008 строк, Ghidra)
- `FreeFCC/.scratch/tbie_20260812/scripts/DecompileAll.java` — Ghidra-скрипт
- `FreeFCC/.scratch/tbie_20260812/imah_blobs.txt`, `batch_out/*.log` — перечень и логи 84 образов
- Расшифрованные чанки — рядом с исходными блобами в `.scratch/emmc/**/ *.dec_*.bin`
