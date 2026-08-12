# Анализ KDR→TBIE в расшифрованном TZOS eagle2 (OP-TEE + CryptoCell CC712)

Дата: 2026-08-12. Продолжение `TBIE_DECRYPTION_PATH_20260812.md` (п. 4 пути §5).
Инструмент: Ghidra 12.1.2 headless, проект `/tmp/TZOS_KDR`, скрипты и выгрузки:
`FreeFCC/.scratch/tzos_kdr_20260812/{scripts,out}/`.

## 0. TL;DR

1. `tos.img.dec_TZOS.bin` (eagle2, Mavic 3) — это **OP-TEE OS 3.2.0** (`3.2.0
   @0df5f3b3-dev #1 Fri Apr 15 11:42:17 CST 2022 aarch64`), база загрузки
   `0x3f000000`, с интегрированным драйвером **Arm CryptoCell CC712**
   (`sw-cc712tee-1.2.0.157`, исходные пути `/home/ryan.dong/workspace/OPTEE/e2_cc712/…`)
   и DJI-библиотекой `core/arch/arm/kernel/dji_ta_load/`.
2. **KDR-механика**: при загрузке TEE, если LCS==5 (secure), драйвер CC712
   выводит три 16-байтовых ключа образов **KDF(CMAC-AES, HW-ключ класса 6 из OTP,
   label, context=fourcc)** — `TAIE` (label `"IMAGE ENC KEY V2"`), `TBIE` и
   `KRIE` (label `"IMAGE ENC KEY"`) — и кладёт их в keymgr. Если LCS≠5 —
   используются **статические dev-ключи из .rodata** (`tbie`, `ufie`, `rrak`).
3. **TBIE-дешифровка в TEE** — это DJI-надстройка над IM*H: `image_verify_common`
   → (опц.) RSA-3072 PRAK-подпись заголовка → поиск `enc_key` fourcc в keymgr →
   **AES-128-ECB разворот scram_key** → SHA-256 digest → **AES-128-CTR payload
   (единый cipher на все чанки, паддинг до 32)**. Семантика байт-в-байт совпадает
   с `dji_verify`/OGs — oracle по cksum подтверждён ранее.
4. **Наблюдаемый факт:** TBIE-2021-08 совместим с описанным eagle2 KDF,
   а из образа eagle2 нельзя вывести ключи 2022+/STBE.
   **Гипотеза:** wa341/eagle3 использует аналогичную KDF-схему, но новый
   ключ может отличаться root, label, context или hardware key class.

## 1. Идентификация блоба

| Параметр | Значение |
|---|---|
| Файл | `/home/danik/Projects_and_coding/.scratch/emmc/Mavic3_2/tos.img.dec_TZOS.bin` |
| Размер | 401888 B (0x621e0) |
| sha256 | `2f68e20afd3ac96f84d81a907f3b7cef2a5e6dc153e69ba3b9d852cad1baeceb` |
| Формат | raw ARM64 LE, база `0x3f000000` (TZDRAM; указатели 0x3f06xxxx в векторах сходятся) |
| Версия | OP-TEE 3.2.0 @0df5f3b3-dev, сборка 2022-04-15 11:42:17 CST, aarch64 |
| Строка версии | file off `0x5f25c`; GPD: `GPD-1.1-dev`, manufacturer `LINARO` |
| CryptoCell | CC712, MMIO phys `0xff804000`; LCS-регистр `+0xfd4` (мл. байт), KDR-valid `+0xfd0` bit0, HBK-disable `+0xfd4` bit8 |
| Платформа | `core/arch/arm/plat-eagle2/main.c` |
| Рядом | `tos.img.dec_LATF.bin` (ATF/BL31, sha256 `11712f6b…aaf2f0`) |

TA внутри образа нет; TA грузятся извне как DJI-образы: `tee_ta_manager` +
`dji_ta_load` (`{%s,%d} TA size error!`, `failed to verify dji ta image`,
`ta size is error:ta_size:%zu < dji_format_size:%zu`). Встроенные PTA/early-TA:
`interrupt_tests.ta`, `stats.ta` (строки в rodata).

## 2. KDR: вывод ключей образов (eagle2)

### 2.1 Boot-инициализация ключей — `FUN_3f024450` (вызывается из `FUN_3f023a8c`, service_init)

```
FUN_3f02f7f0(&lcs);              // читает LCS: *(uint32*)(map(0xff804000)+0xfd4) & 0xff
if (lcs == 5) {                  // CC LCS: 0=CM 1=DM 5=SECURE 7=RMA
    FUN_3f02435c();              // TA_KeyDerivation: derive TAIE/TBIE/KRIE
    keymgr_inject(TAIE,buf@0x3f062200,16); inject(TBIE,0x3f062220,16);
    inject(KRIE,0x3f0621e0,16);  inject(PRAK, 0x3f061ec0, 800);   // FUN_3f025214
} else {
    keymgr_inject(tbie, 0x3f0533a0,16); inject(ufie, 0x3f0533c0,16);
    inject(rrak, 0x3f05c401, 800);                                 // FUN_3f0251a4 — dev-ключи
}
keymgr_add_ecc_key(0x30656c75 /*'ule0'*/, 0x21, 0x3f05c3e0);
keymgr_add_ecc_key(0x306b616f /*'oak0'*/, 0x21, 0x3f05c3bf);
```

### 2.2 Сам derive — `FUN_3f02435c` @ `0x3f02435c`

```
kdf(6, NULL, "IMAGE ENC KEY V2", 16, "TAIE", 4, out=0x3f062200, 16);
kdf(6, NULL, "IMAGE ENC KEY",    13, "TBIE", 4, out=0x3f062220, 16);
kdf(6, NULL, "IMAGE ENC KEY",    13, "KRIE", 4, out=0x3f0621e0, 16);
```
Строка-источник: `"IMAGE ENC KEY V2IMAGE ENC KEYtee_core_cc_key_generate"`
@ file `0x5c317`; строка ошибки `"TA_KeyDerivation failure res:0x%x"` @ `0x5c721`.

### 2.3 KDF — `FUN_3f0062f8` @ `0x3f0062f8` → `FUN_3f017238` @ `0x3f017238`

SP800-108 counter-mode KDF поверх **AES-CMAC** (CC712 sym-driver, DMA-очередь
`0x3c00`, режим 7 = CMAC): буфер `[counter:1][label][0x00][context][L:1..2]`,
на каждый 16-байтовый блок вывода один CMAC. Классы ключа (арг. 1):

| class | Семантика | Гейт |
|---|---|---|
| 0 | user key (16/32 B, из памяти) | — |
| 1 | HW root (HBK) | запрещён, если OTP-бит «HBK disable» (reg +0xfd4 bit8) |
| 2 | KDR | требует KDR-valid (reg +0xfd0 bit0) |
| 6 | HW OTP-ключ класса 6 (image keys) | без гейта |

RPMB-ключ — отдельно: `tee_rpmb_key_gen` → `FUN_3f0066b8` @ `0x3f0066b8`:
`kdf(1 /*HBK*/, NULL, "RPMB KEY", 8, "ARM ", 4, out, 32)`, только при
`(lcs & 0xfb) == 1` (т.е. LCS∈{1,5}); строка `"CC_UtilDeriveRPMBKey failed(0x%x)"`.

### 2.4 Keymgr — `FUN_3f025050` (inject) / `FUN_3f024e70` (lookup)

- Хранилище: 4 слота ×0x348 @ `0x3f062270` (большие ключи: PRAK/rrak 800 B),
  8 слотов ×0x48 @ `0x3f062f90` (16-B AES + ECC). Lookup по fourcc;
  `DAT_3f07204e` после boot скрывает «ROM keys» (flag&1).
- Поддерживаемые id: `TAIE TBIE KRIE UFIE` (тип 8), `tbie ufie` (тип 9, dev),
  `PRAK` (тип 0x20), `rrak` (0x21). **`STBE` в eagle2 отсутствует** — fourcc
  появился в контейнерах следующего поколения; его поддержка в
  зашифрованном TZOS пока лишь выводится из факта загрузки.
- «ROM Key» (`*(uint*)(key+0x10)&1`) отвергается верификатором, если во флагах
  вызова не установлен bit8 (`"Not support ROM Key"` @ `0x5ca9e`).

### 2.5 Статические dev-ключи в .rodata (LCS≠5) — извлечены

| id | Адрес (VA / file off) | Значение |
|---|---|---|
| `tbie` | `0x3f0533a0` / 0x533a0 | `baba6b29777b87702b31618cfa72c559` — **нет в публичной номенклатуре OGs** (проверено по всем 20+ ключам dji_imah_fwsig) |
| `ufie` | `0x3f0533c0` / 0x533c0 | `9cdaf6274ecb78f3eddce526bcec66f8` (= ключ из dji_verify, там назван RUEK) |
| `rrak` | `0x3f05c401` / 0x5c401 | 800 B, N_SIZE_ID=0x60 → **RSA-3072** pub; sha256(blob) `41d0ef97…b577dc` |
| `ule0` ECC | `0x3f05c3e0` / 0x5c3e0 | 33 B compressed: `0379fe37…aa38d3` |
| `oak0` ECC | `0x3f05c3bf` / 0x5c3bf | 33 B compressed: `03542921…213b06` |

### 2.6 PRAK (production) — НЕ в образе

`DAT_3f061ec0` (800 B) в файле = `0x60` + нули. PRAK поставляется **boot-хендоффом
от предыдущего загрузчика**: `FUN_3f02f6dc` @ `0x3f02f6dc` читает структуру по
`DAT_3f061a20` (boot args от ATF): `[0]=ptr PRAK(800B, size поле [4]==0x320),
[8]=ptr fast-reload IMFR-листа, [0x10]=ptr device-info (console off)`. PRAK
доступен TA через syscall 65 `syscall_prak_get` (`FUN_3f038248`). Т.е. на
production-устройстве PRAK-pub живёт в цепочке bootrom→BL2/ATF→TEE (вероятно
из OTP/подписанного bootarea), а не в TZOS.

## 3. Путь TBIE-дешифровки внутри TEE

Системные вызовы DJI (таблица @ `0x3f053600`; DJI-блок с idx 52):

| idx | VA | Имя (строка) |
|---|---|---|
| 52 | `0x3f037ab4` | `syscall_cryptocell_getlcs` |
| 53 | `0x3f037b14` | `syscall_rpmb_key_inject` |
| 54–64,66–68 | `0x3f037b78`… | `syscall_unrd_*` (RPMB-хранилище UNRD, 11 шт.) |
| 65 | `0x3f038248` | `syscall_prak_get` |
| 69 | `0x3f038304` | `syscall_fw_image_verify` → `fw_image_verify` |
| 70 | `0x3f032294` | `syscall_ecc_verify` (ECDSA по ключам ule0/oak0 — unlock-авторизация) |

Все они гейтятся `FUN_3f030904` (только из контекста user-TA).

`fw_image_verify` = `FUN_3f024560` → `image_verify_common` = `FUN_3f026168`
(флаги 0x723 = header-auth|decrypt|src-virt|allow-ROM-key|ptext+ctext verify):

1. magic `IM*H` (0x482a4d49), размеры, `type` @+0x60, `name` @+0x40
   (whitelist типов @ `0x3f058750`: `'0000','0040','10C0','20C0','30C0','40C0','00B0','1080','TZTA'`).
2. checksum `sum32` (`dji_ta_load/lib/checksum/sum32.c`, `FUN_3f024864`).
3. header-auth: ключ `auth_key` fourcc @+0x28 (`PRAK`) → SHA-256(header) →
   RSA verify сигнатуры **0x180 = 384 B (RSA-3072)** (`FUN_3f024c68`, CryptoCell PKA).
4. payload: ключ `enc_key` fourcc @+0x2c (`TBIE`/`TAIE`/`KRIE`/`UFIE`) →
   **AES-128-ECB decrypt scram_key[16] @+0x30** (`FUN_3f024bf4`, algo 0x1180;
   ошибка `"failed to decrypt scramble key"` @ 0x5cb0b).
5. digest: SHA-256 по всем чанкам с паддингом (`FUN_3f025fe0`, algo 0x11000000).
6. decrypt: **AES-128-CTR** (`FUN_3f025ddc` = `image_decrypt_payload`,
   algo 0x1080 → CC mode 0x210), единый cipher, каждый чанк допаджен до 32 B;
   накопление plain sum32 при bit3 (`"failed to verify payload plaintext checksum"`).

SiP SMC из normal world: `0xb200000d/e` (чтение/запись uint32 в secure),
`0xb2000011` → `image_fast_reload_load` (`FUN_3f0246e0`, IMFR-лист из хендоффа).

Normal-world цепочка (подтверждена на wa341 M4): `dji_fw_verify`/`dji_fw_load` →
`libfw_util.so` → `libfw_util_ca.so` (`fw_util_verify_init/update/final/load`,
`TA_FW_UTIL_CMD_LOAD`) → TEEC → **TA UUID `09db16c0-873b-4fed-b87ea5d2b86293a2`
(name=djita)** → TA вызывает syscall 69 → всё выше. Граница TZ↔NW = этот
TEEC-вызов; NW видит только результат, ключ не покидает TZ.

TA-файлы — IM*H `type=TZTA`, sig=384: M4 (wa341): `enc=TBIE` (даты 2023-11…
2025-01); Data_5 (eagle3): **`enc=STBE`** (2024-11, 2025-06). Т.е. сами TA
расшифровываются тем же `image_verify_common` внутри TZOS своей платформы.

## 4. Модемный TZOS (pigeon, WM260) — та же схема

`EMMC_Data_M3/.../normal.img.dec_TZOS.bin` (334688 B, sha256 `fde69c1d…5ef7b84`,
ARM32, старый CRYS DX драйвер) содержит те же компоненты: `"IMAGE ENC KEY V2"`,
`"IMAGE ENC KEY"`, `fw_image_verify`, `keymgr_inject_key/add_ecc_key`,
`"failed to decrypt scramble key"`, `getlcs`. Значит KDF-из-OTP-корня —
единая DJI-механика ≥2019; у модема корень прошивается как `cmpu_otp`/`cmpu_kdr`
(fastboot, заводские `burn_otp_*.sh`), у AP — OTP SoC eagle2/wa341.

## 5. Отличия wa341/eagle3 (2024–2025) — оценка

1. **Гипотеза с высокой уверенностью: схема аналогична** (CC712 KDF,
   labels, fourcc-keymgr). На это указывают
   идентичный userspace-стек (libfw_util_ca, djita-TA, type=TZTA) и сохранение
   `cmpu_otp/cmpu_kdr` процедуры у sparrow2 (`burn_otp_v1_0.sh` на eagle3
   userdebug — дословно тот же `fastboot flash cmpu_otp otp.sec`).
2. **Изменился как минимум итоговый ключ**: TBIE-2022+ не совпадает ни с одним
   публичным ключом. Причиной может быть новый root, label, context или
   hardware key class. Fourcc `STBE` может обозначать отдельный derive, но
   это не доказано без plaintext TZOS новой платформы.
3. Сам TZOS wa341/eagle3 в дампах зашифрован TBIE-2022+/STBE — прямое сравнение
   KDR-кода пока невозможно (chicken-and-egg).

## 6. Что нужно для ключа TBIE-2022+/STBE и реалистичные векторы

Требуется неизвестный 128-битный ключ. Если схема wa341/eagle3
аналогична eagle2, кандидатная формула —
`CMAC-KDF(HW_root, label, context=fourcc)`, но root/label/context/class не
наблюдались. Брутфорс/инверсия исключены. По убыванию реалистичности:

1. **userdebug eagle3 (Data_5) с `/xbin/su` + живое устройство.** Root в NW не
   даёт TZ-ключей, но: (а) если у userdebug-юнита **LCS≠5**, его TZOS работает
   на **статических dev-ключах из rodata** (как `tbie`/`ufie` на eagle2) — тогда
   прошивки/TA для таких юнитов расшифровываются dev-ключом, а сам TZOS
   userdebug-платформы может грузиться/читаться иначе; проверить LCS можно
   штатно: syscall 52 через TA либо `check_secure_debug` (есть в p7-наборе);
   (б) `p7_sign` — PKCS#7-обёртка, используемая `dji_lte` для
   LTE/eSIM-аутентификации (`fake_priv_key`, `https_client_sign`); в проверенном
   userspace **TBIE-ключ не найден**;
   (в) `/dev/otp` на AP не существует — OTP читает только CC712 в secure world.
2. **Сдампить derived-ключ из TZDRAM на живом устройстве.** На eagle2 derived
   TBIE лежит plaintext в secure RAM (`0x3f062220`). На wa341 адрес и layout
   неизвестны; похожее размещение — только рабочая гипотеза. Нужен
   secure-world read-примитив:
   уязвимость TA/syscall (71 кастомный syscall — большая поверхность, `unrd_*`,
   `fw_image_verify` парсит attacker-controlled IM*H из NW через shared memory)
   либо физ. доступ к шине TZDRAM. Это самый прямой путь: ключ выходит из
   KDF один раз при boot и дальше лежит в ОЗУ.
3. **CMPU OTP sparrow2 (модемный TBIE-2022+)** — аппаратно (glitch/JTAG/
   производственный fastboot с `pro_prak.pub.mon`, которого нет в дампах).
   Закрывает только модемные образы, не TA/AP.
4. **OTA 2024+** — перехват IM*H для wa341; ключ там не лежит, но позволяет
   наблюдать scram_key/заголовки и искать слабости (единый CTR на чанки,
   nonce=0 — при повторном scram_key возможна CTR-подстановка; scram_key
   уникален на образ, так что практической ценности мало).
5. Найти утечку нового ключа (как TBIE-2021-08 от Felix Domke в 2022) —
   вне нашего контроля, но механика утечки та же: ключ существует в открытом
   виде в TEE RAM каждого устройства.

Побочная проверка (выполнена): dev-ключи `tbie` (`baba6b29…72c559`) и `ufie`
из §2.5 прогнаны oracle-перебором (plain_cksum) по 2024+ блобам — 4 TA M4
(TBIE), 2 TA Data_5 (STBE), 4 образа sparrow2 air3_1 (TBIE: l1c/dsp/cp/scp).
**Ни одного совпадения** — 2024+ production образа шифруются новым корнем,
dev-ключи eagle2 к ним не подходят.

## 7. Артефакты

- `FreeFCC/.scratch/tzos_kdr_20260812/scripts/`: `TzosPre.java`,
  `TzosStrings.java`, `TzosDecomp2.java`, `TzosXref.java`
- `…/out/`: `strings_xrefs.txt` (все строки с xref), `kdr_decompile.txt`
  (31 функция: boot, kdf, keymgr, image_verify/decrypt, RPMB),
  `kdr2_decompile.txt` (KDF-примитив, LCS, RSA-verify, cipher init),
  `kdr3_decompile.txt` (CC712 AES-CMAC блок, xref PRAK/ключей),
  `kdr4_decompile.txt` (boot-хендофф PRAK, syscall-гейт, sym-adaptor),
  `kdr5_decompile.txt` (xref syscall-таблицы), `ghidra_run.log`
- Ghidra-проект: `/tmp/TZOS_KDR` (AARCH64:LE:64:v8A, base 0x3f000000, 1341 функция)
