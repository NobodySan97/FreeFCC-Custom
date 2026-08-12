# Анализ p7_sign и заводского security-tooling eagle3_wa341 (EMMC_Data_5_64gb)

Дата: 2026-08-12. Исполнитель: agent-21.
Продолжение `TBIE_DECRYPTION_PATH_20260812.md` §5 п.1: извлечение и анализ
`p7_sign` из userdebug-дампа EMMC_Data_5_64gb как кандидата на носителя
симметричного ключа TBIE-2022+/STBE.

## 0. TL;DR

1. **p7_sign — это PKCS#7-подписыватель** (P7 = PKCS#7, а не DJI signature
   container): CLI-обёртка над OpenSSL `PKCS7_sign` для заводской/cloud-подписи
   сообщений (основной потребитель — `dji_lte`, LTE/eSIM-аутентификация).
2. **В проверенной userspace-цепочке p7_sign симметричный ключ
   TBIE-2022+/STBE не найден.** Подпись девайсным ключом
   выполняется внутри TrustZone TA (`e91c9402-…`, name=`teeta`). TA
   сама зашифрована STBE, поэтому её внутренний key path по этому
   дампу не проверен.
3. **`STBE` на eagle3 — общий идентификатор ключа в контейнерах**:
   разделы `scp`, `tos`, `gimbal`, `normal` (модемный Linux 21MB) — все `enc=STBE`,
   sig=384 (RSA-3072), даты 2024-12–2025-09. TBIE на этой платформе не
   встречается. Один fourcc не доказывает одинаковый AES-ключ или
   общий KDR-root для AP, TEE и модема.
4. Ничего из sparrow2/eagle3 не расшифровано: нового ключевого материала
   не найдено, дешифратору `tbie_try2.py` нечего пробовать.

## 1. Извлечение (методика)

`system.img` после инвентаризации agent-4 был удалён; повторное копирование
640MB по sshfs (~0.55MB/s) заменено точечным чтением: написан
`.scratch/emmc/ext4walk.py` — read-only ext4-walker (superblock → GDT →
inode/extents) поверх ranged-reads из
`/tmp/toshiba/dumps_emmc_dji/EMMC_Data_5_64gb/UserData.BIN`
(system @ sector 573440, offset 293601280). Сеть не ддосилась: читались
только inode-таблицы, каталоги и целевые файлы.

Артефакты: `.scratch/emmc/EMMC_Data_5_64gb/p7/` —
`p7_sign`, `check_secure_debug`, `burn_otp_v1_0.sh`, `unlock_uav_req_v1_0.sh`,
`unlock_uav_verify_v1_0.sh`, `libamt_util.so`, `libcryptoopenssl.so`,
`libteec.so`, `lte_fake.priv`, `rsa_public_key.pem`, `rsa_public_key_1.pem`,
`factory_raw.bin` (первые 72KB раздела factory_raw), `env.bin`.

## 2. Хэши / Build ID

| Файл | sha256 | Build ID / примечание |
|---|---|---|
| `/bin/p7_sign` (11304 B, ELF64 aarch64 PIE, stripped) | `50b9536874cf1e4af2864e89f0ddd4308411673c17997eeb8434580678295d30` | `2a78cddedd7c3eda4ab6bc635dfaad9b` |
| `/bin/check_secure_debug` (4552 B) | `671331ad91e235f56ac6f2ad6c19b748c6abc22c11f9788e82937e6790f2d573` | `c7647ad7dc36b3207fbe464101d84239` |
| `/lib64/libamt_util.so` (77280 B) | `1a785a8c02a7f5aacc7ceb675d3bf498174c14b65b0a33dda207d39eaf2d146d` | — |
| `/lib64/libcryptoopenssl.so` (2385664 B) | `170d9e61c2b2062ec28c7935265d68f80237fa9cb23b7609fb0abd4c46d7b5ac` | OpenSSL 1.1.1p |
| `/lib64/libteec.so` (11952 B) | `c2dd9f2ec9fcb5fe001352b47b897a0e1a27eb97d4e1a352cc6e3497587c3223` | OP-TEE client |
| `/bin/burn_otp_v1_0.sh` | `94312652160d012b36c17ca502a2bbc89714ccaa53d65918e6c331b3cc6dfbc4` | — |
| `/bin/unlock_uav_req_v1_0.sh` | `759199031f5004081af58e668654200d9889f3c6105709ed891c6fc6ae2660a3` | — |
| `/bin/unlock_uav_verify_v1_0.sh` | `df2f9105049ae3781978280caf489578b05868acdf37504c61fc3b2bdbfc2d3f` | — |

Символы восстановлены из `.gnu_debugdata` (mini-debuginfo, xz): у p7_sign это
`main` (0x2770, 1116 B), `rsa_sign` (0x2310), `rsa_pub_cmp` (0x2330),
`write_base64_output` (0x2390), `write_output` (0x24c0), `help` (0x2690).
Build ID мини-ELF совпал с основным — символы от этого же билда.
Хэши в таблице относятся к байтам, точечно прочитанным из ext4-образа.
Рабочие копии с добавленной `.gnu_debugdata` имеют другой SHA-256; это не
изменение исходного ELF в дампе.

## 3. Что делает p7_sign

Help (строка @0x1088):
`p7_sign -t 0 -i /tmp/p7.in -c /tmp/device.crt -p /system/etc/fake.priv -v 1 -b 1 -o /tmp/p7.out`

Опции getopt `ht:i:c:p:v:b:o:`: input/output file, device cert (PEM/DER,
`-v`), privkey (`-p`), output form (`-t`: 0=SMIME, 1=PEM, 2=DER), base64 (`-b`).

`main` (дизасм, адреса image base 0):
`OPENSSL_init_crypto` → `crypto_external_config` (libamt_util) → загрузка
device cert (`PEM_read_bio_X509`/`d2i_X509_bio`) →
`PEM_read_bio_PrivateKey` (файл `-p`) → `PKCS7_sign` +
`PKCS7_sign_add_signer(…, EVP_sha256)` → вывод через
`SMIME_write_PKCS7` / `PEM_write_bio_PKCS7_stream` / `i2d_PKCS7_bio_stream`
(+BIO_f_base64).

`rsa_sign` = thin wrapper над `https_client_sign` (libamt_util);
`rsa_pub_cmp` = wrapper над `https_check_private_key` — сверка публичного
ключа из сертификата с ключом, доступным устройству (TEE).

Вся криптография — через OpenSSL (`libcryptoopenssl.so`, OPENSSL_1_1_1p)
и libamt_util. **Никаких вызовов AES/RSA с встроенными ключами, никакой
TBIE/STBE/IM*H/PRAK-специфики** (строковый grep по p7_sign, libamt_util.so,
libteec.so, check_secure_debug: 0 хитов).

## 4. libamt_util.so — TEE-шим

Экспортирует ~90 функций (`plt_*`, `sec_debug_*`, `anti_rs_*`, `model_*`,
`rpmb_*`, `keyrepo_*`, `https_client_sign`, `amt_get_derive_log_key`,
`amt_get_derive_adsb_key`, `pkcs12_parse`…). Все ключевые операции —
`TEEC_InitializeContext/OpenSession/InvokeCommand` (libteec.so → OP-TEE
driver). Характерные строки: `crypto_begin/update/final`, `crypto_aes_cmac`,
`crypto_aes_decrypt`, `start invoke TA_KEYREPO_READ_KEY`,
`/factory_data/device.p12`, `board_sn=`, `production_sn=`, `chip_sn=`,
`cat /proc/cmdline | busybox grep mp_state=engineering`.

`https_client_sign` (0xa590, 512 B): целиком TEEC-сессия —
InitializeContext → OpenSession(**UUID константа @0x2560** =
`e91c9402-64a0-470f-88e7-bf5d3c606b6a`) → AllocateSharedMemory ×2 →
InvokeCommand → копирование подписи из shm. Ключ покидает TEE только в виде
готовой подписи.

**TA `e91c9402` — это `/vendor/ta/e91c9402.ta` из этого же дампа** (uuid в
имени файла совпал байт-в-байт), заголовок IM*H: `name=teeta type=TZTA
hver=2 auth=PRAK enc=STBE sig=384 date=0x20250630`,
scram=`c3eb1fbcb2999ed3c4be6777a7dc2119`, чанк `TZTA:0x45468 E`.
Вторая TA `09db16c0.ta`: `name=djita`, тоже `enc=STBE`, date=0x20241101,
scram=`eaed4e9ec23a8fc755741e72e86cb8d2`.

Скан `.rodata`/`.data`/`.data.rel.ro` p7_sign, libamt_util.so,
check_secure_debug на высокоэнтропийные ≥12-байтные константы (кандидаты в
AES-ключи): **пусто** (кроме TA UUID). Ключевого материала в userspace нет.

## 5. Заводские скрипты (полные тексты — в p7/)

- `burn_otp_v1_0.sh`: `fastboot flash cmpu_otp /tmp/encrypt/otp.sec` →
  reboot. Прожиг CMPU OTP **модема** (как `burn_otp_pigeon_0.sh` на wm260).
- `unlock_uav_req_v1_0.sh`: GPIO 23/3 (reset/power slave-чипа SDR) →
  `brload /tmp/encrypt/bootarea.img` → `fastboot flash unlock_uav_req
  /tmp/encrypt/gpt` → `fastboot get_staged /tmp/encrypt/upload.bin`.
  Это eagle3-аналог `get_upload_pigeon_0.sh` (прошивка cmpu_kdr/ver).
- `unlock_uav_verify_v1_0.sh`: `fastboot flash unlock_uav_verify
  /tmp/encrypt/otp.sec` → reboot.
- Все входные файлы (`/tmp/encrypt/*`) — заводские, в дампе отсутствуют.
- `check_secure_debug`: читает `sec_debug_state` через libamt_util → TEE
  (+обход через `mp_state=engineering` в cmdline).

Вывод идентичен wm260: доверенная цепочка и ключи — в CMPU OTP модема и в
TEE AP; userdebug-сборка даёт лишь CLI-фронтенды.

## 6. GPT-карта EMMC_Data_5_64gb (снята заново, LBA-заголовок)

| # | name | start | size |
|---|---|---|---|
| 4/5 | factory_raw / factory | 262144 / 278528 | 8.4MB / 16.8MB (factory — нули) |
| 7/8 | scp / scp_2 | 344064 / 360448 | 8.4MB |
| 9/10 | tos / tos_2 | 376832 / 393216 | 8.4MB |
| 11/12 | gimbal / gimbal_2 | 409600 / 425984 | 8.4MB |
| 13/14 | normal / normal_2 | 442368 / 507904 | 33.6MB |
| 15/16 | system / system_2 | 573440 / 1884160 | 671MB |
| 17/18 | vendor / vendor_2 | 3194880 / 4186112 | 507MB |
| 19/20 | squashfs / squashfs_2 | 5177344 / 5443584 | 136MB (нули) |
| 23 | blackbox | 5873664 | 5.9GB |
| 25 | media | 33816576 | 45GB |

### Заголовки IM*H boot-разделов (прочитаны in-place, по 4KB)

| Раздел | name/type | auth | enc | sig | date | chunks |
|---|---|---|---|---|---|---|
| scp | scp/TSCP v0x02030000 | PRAK | **STBE** | 384 | 2025-09-16 | BLM0:0x1c43c E @0x10000000 |
| tos | tos/TKRN | PRAK | **STBE** | 384 | 2025-09-16 | LATF:0x9140 E @0x7ee00000; TZOS:0x78be0 E @0x7f000000 |
| gimbal | M7firmware/TBLD | PRAK | **STBE** | 384 | 2024-12-19 | M7FW:0xa2192 E @0x8000000 |
| normal | normal/TKRN | PRAK | **STBE** | 384 | 2025-09-16 | KERN:0x1030008 E @0x40080000; LRFS:0x2ad400 E @0x44400000; LDTB + 5×DTB |

scram-ключи: scp `3ff438345b340f46ae78677b34b1a468`,
tos `e204bc7e29c5fa5c497dae0812c7443b`, gimbal
`2d8fdf13bcba315ec62047650910591d`, normal `d68c539fdc63ddddc00c41a8fa9f91ea`.

**Все зашифрованные компоненты eagle3_wa341 — STBE.** `normal` — это
модемный Linux стек sparrow2 (KERN arm64 zImage + LRFS rootfs + DTB),
тот же класс, что `Mavic3_2/normal.img` (TBIE-2021-08), но под новым ключом.

`factory_raw` (UNR0-контейнер, 72KB данных): записи `gb_param`, ProductSN
`1581F8LQC253P0020WHY`, SecureDebug-записи — тело преимущественно
высокоэнтропийное (зашифровано, вероятно TEE-ключом). `factory` раздел —
нули (device.p12 туда не прошит).

## 7. Потребитель p7_sign

`dji_lte` (уже извлечён): строки `rsa_sign start!`, `/data/sign.out`,
`pkcs12_parse`, `/system/etc/lte_fake.priv`, `failed to load pkcs7 signature
file` — LTE/eSIM/cloud-аутентификация донгла подписывается PKCS#7.
`/etc/lte_fake.priv` (извлечён, sha256 в артефактах) — **тестовый RSA-2048
private key** для userdebug; к TBIE/STBE отношения не имеет (асимметричный,
LTE-scoped).

## 8. Итог для задачи TBIE-2022+/STBE

- p7_sign мёртвый конец для симключа: ни ключей, ни derive-логики, ни
  OTP/eFuse/RPMB-доступа к TBIE — только RSA-подпись через OpenSSL/TEE.
- Проверка ключей на sparrow2-блобах (п.3 задания) не выполнялась за
  отсутствием новых кандидатов — перебирать нечего, `tbie_try2.py` без
  изменений.
- Реалистичные пути дальше (без изменений vs TBIE_DECRYPTION_PATH §5):
  TEE-сторона (`tos`/TKRN + TA `teeta`/`djita` — но они STBE, замкнутый
  круг), CMPU OTP модема (аппаратно), либо утечка ключа из
  заводской утилиты другого поколения/хоста.
- Новый факт, сужающий поиск: на eagle3 контейнеры AP boot-chain,
  TA и модемного `normal` ссылаются на один fourcc `STBE`.
  **Не доказано**, что все они получают одинаковые 128-битные ключи или
  используют один KDR-root: AP/TEE и модем могут разрешать этот id в
  разных hardware key domains.
