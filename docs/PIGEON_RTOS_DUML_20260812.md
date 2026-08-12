# PIGEON RTOS DUML — анализ расшифрованной прошивки модема DJI Mavic 3 (WM260)

Дата: 2026-08-12. Источник: свежерасшифрованные блобы поколения TBIE-2020-02/2021-08
(agent-19, см. `docs/TBIE_DECRYPTION_PATH_20260812.md`).
Цель: закрытие пробелов DUML_COMMAND_AUDIT по cmd_set 0x09 (09:27, 06:8C,
09:EC handler-сторона) и регистровым константам sparrow2 0xffff0063/0xffff0048.

## 1. Верификация блобов

Каталог: `/home/danik/Projects_and_coding/.scratch/emmc/EMMC_Data_M3/root/modem_firmware/pigeon/`

| файл | размер | sha256 | формат |
|---|---|---|---|
| cp.img.dec_RTOS.bin | 3 544 516 | 6cfa4b87797217bccbfcc99735860b768a30cb8e9ff7cdcb89a21ba72320838d | ARM32 RTOS (base 0x10000000) |
| cp.img.dec_M0IM.bin | 6 112 | fae19f3ccedec42abec16c1e070851d5b68c2c62eddf95659993fed4c06d929b | Cortex-M0 image |
| cp.img.dec_X164.bin | 886 880 | 1ee8b744bcf6db8a04498cd20aded07d5b0603a5317b52335fabdb730f981e25 | Xtensa DSP (SDR PHY: "amt tx parameter set err") |
| cp.img.dec_X450.bin | 789 248 | 0c9e1597dbd11a5e38f027b1d220bd34c23b019ca824970330193bb52df27f65 | Xtensa DSP ("UL CA rxdfe group param error!") |
| cp.img.dec_X451.bin | 459 808 | 549648aafb6d953f5b3768448fb98845009ed430fdc1c31f1e98d5c7e7079e3d | Xtensa DSP |
| cp.img.dec_RFNV.bin | 65 536 | 2adf94a5ea907d97448301088fb43f9ff424703d2b27c52073671658b712191a | RF NVRAM data |
| cp.img.dec_PHYS.bin | 8 192 | a11d216d68bf575a7954d25c64eee917f7410e82407d7a47695722d3546dea1c | PHY config data |
| normal.img.dec_KERN.bin | 5 759 960 | 3d459f2d28abaedbcdf45853a49898ff46b4ac07273c8fc43411f5c8070ae7be | Linux zImage ARM (>=3.17,<4.15) |
| normal.img.dec_LRFS.bin | 10 780 501 | 5b66c16d33b6ec7875823ec852b82b44d17b23d21cf1f923997a56f7525df61f | gzip→cpio rootfs (Android 23) |
| normal.img.dec_LDTB.bin | 30 968 | 3f84d123d47521ece175d342910d57f1f51a81375ca51c8a0fe0050116b48563 | DTB v17 |
| normal.img.dec_TZOS.bin | 334 688 | fde69c1dc3c2361f06792c8d0036e5494c0300f6e0d0d8d3e5be933e35ef7b84 | TrustZone OS |
| bootarea.img.dec_bootloader_BLLK.bin | 155 768 | eebe693d7189afca7f001d7243e778d6b246f377ad6c957785886f0eb5a194a6 | bootloader |

## 2. Формат pigeon cp RTOS

- **Архитектура: ARMv7-A, 32-bit A32 (не Thumb), little-endian, base = 0x10000000.**
  Base доказан двумя независимыми способами: boot-литералы в начале образа
  (0x102d5204, 0x1023f280, 0x1002008c указывают внутрь образа при base 0x10000000)
  и скан полнословных указателей на ASCII-строки (64 попадания вида
  `0x10000000+offset → начало строки`, напр. 0x100edd28 → `" vic ,please check"`).
- Исходные пути: `../../../../common/P1/src/...`, `../../../../custom/P1/...`
  (duml/app/p1_app_uav_wl_wm260.c @0x83390, duml/frwk/wl/wl.c, pcr/dji_pcr.c
  @0xe2cc4, driver/P1/vdec/...h264hwd_dpb.c, custom/P1/audio/opus-1.3.1/...).
  WM260-специфичный app-модуль присутствует: `p1_app_uav_wl_wm260.c`.
- DUML-парсер — модуль **PCR** (`custom/P1/pcr/dji_pcr.c`): байт-стрим
  автомат по 0x55 SOF, len(10 бит)@frame+1, CRC8 (строки `"[pcr] receive:
  V1_HEADER CRC8 failed"` @0xe28c4, `"[PCR] DjiV1CmdVerifyCrc8 == FALSE"`
  @0xe28ec, `"pcr: adnormal V1 cmd len: %d discard"` @0xe2910).
  Контекст канала в RAM 0x1103f4cc; layout кадра подтверждён лог-кодом:
  `[4]=sender(type<<5|id)`, `[5]=receiver`, `[6..7]=seq`, `[8]=attr`,
  `[9]=cmd_set`, `[0xa]=cmd_id`, `[0xb..]=payload`.
  Инициализация канала @0x100e2938 жёстко задаёт фильтр **cmd_set=0x0a,
  cmd_id=0xf0** (strb #0xa/#0xf0 @0x100e2978/0x100e2980) — единственный
  захардкоженный cmd-фильтр PCR в образе. Дефолтный frame-callback
  [ctx+0x418]=0x10136ba8 — assert-хвост (заглушка), реальный обработчик
  регистрируется динамически.
- Приложение `p1_app_uav_wl_wm260.c` содержит построитель telemetry-репорта
  @0x10170bd8 (структура 0x320 байт) с flight-recorder событиями
  `fw_0900_sdr_uav_report_2` @0x1710ec и `fw_0E00_p1_sdr_rc_report_2`
  @0x171e68 — это **ID лог-событий**, а не DUML-таблица.

## 3. Побайтовый поиск packed-констант (итог)

Искались во ВСЕХ расшифрованных образах pigeon (cp RTOS, M0IM, X164, X450,
X451, PHYS, RFNV, TZOS, KERN, LRFS целиком и все ELF внутри rootfs
поодиночке): LE32/BE32 (cmd_set<<16)|cmd_id, u16 (set<<8|id), байтовые пары
(set,id)/(id,set), ARM movw/movt- и Thumb movw-иммедиаты, cmp #imm8.

- **09:27 (0x00090027): статическое представление НЕ НАЙДЕНО** в проверенных
  формах упаковки pigeon. Хиты в rootfs — ложные
  (sepolicy, relocation-записи libduml_fastrtps.so, адрес 0x000927xx как
  функция в libduml_frwk.so).
- **09:EC (0x000900EC): статическое представление НЕ НАЙДЕНО** в проверенных
  формах упаковки pigeon. Единственный хит во всём
  EMMC — `Mavic3_2/scp.img.dec_BLM0.bin` @0x1268c, но это элемент монотонной
  последовательности 0x000900d4..0x000901xx (lookup-таблица enum→enum, без
  соседних указателей на код) — **ложный позитив, проверен по xref/контексту**.
- **06:8C (0x0006008C): статическое представление НЕ НАЙДЕНО** в коде;
  хиты только в DTB eagle2
  (device-tree данные) — ложные.
- **03:AF (0x000300AF): НЕ ПОДТВЕРЖДЁН.** Хиты в `dji_hdvt_uav` @0xc010 и
  `dji_sys` @0x63eb8 разобраны: это GCC switch jump-tables внутри .text
  (значения — смещения/указатели: в hdvt_uav таблица 12-байтовых записей
  {fn_default≈0x31ccd, fn, rodata_ptr}, 0x000300af — указатель на функцию
  в .text 0x3f08..0x39540; в dji_sys — offset-таблица переходов).
  Правило из PERCEPTION_1058_CASE_BODIES соблюдено — кандидаты отброшены.
- **0xffff0063 / 0xffff0048: НАЙДЕНЫ только в cp RTOS** — литеральный пул
  @0x101783b8/0x101783bc. Это **ID межъядерных сообщений sparrow2 register
  access**, не DUML. Xref:
  - обработчик @0x10177f60: `if (msg_id==0xffff0063 && type==2) →
    sub_101acb50(); if (msg_id==0xffff0048 && type==2) → sub_101abbf4()`,
    затем sub_1016faf8 (ответ);
  - диспетчер @0x10178310: `msg_id==0xffff0048 && [r4,#0xa]==2 && len<8`
    → sub_101abc04/sub_101ab78c/sub_1017ade8/sub_10168960;
  - лог-строка `"[0x%x] DATA_WRITE_REQ, addr=0x%x, core=%d, type=%d,
    value=%d"` @0x101783c4 (xref из тела @0x101780e8) — семантика: запись
    регистра sparrow2 по адресу, с указанием core (X164/X450/X451) и type.
  Хиты 0xffff0048 в eagle2 LRFS @0x1dccbc и TZOS — ложные (таблица
  {0xffff00xx-адрес, kernel-ptr} в ramdisk `init`; SMC-иммедиаты в TZOS).

## 4. Вывод по 09:EC (gap d)

**Handler-сторона 09:27/09:EC/06:8C в pigeon не локализована.** Ни в cp RTOS
(ни в одной проверенной форме упаковки), ни в DSP-образах X164/X450/X451, ни
в userspace normal-Linux (`libduml_frwk.so`, `dji_hdvt_uav`, `dji_sys`,
`libwlm.so`, `dji_wlm_slave`) не найдено статического представления этих ids.
Однако PCR использует dynamically registered callback, а registration API ещё
не протрассирован, поэтому отрицательный поиск констант не доказывает отсутствие
handler'а. Наблюдаемая роль модема — WLM **slave**: libwlm.so экспортирует
`libwlm_channel_send/create`,
`dl_lte_sdr_send_v1_init`, `lv_lte_sdr_recv_v*_init`, `wlm_share_info_*`
— приём параметров SDR идёт через межъядерные сообщения
(0xffff0063/0xffff0048, DATA_WRITE_REQ) и shared-info; это делает прямой DUML-
handler в pigeon менее вероятным, но не исключает его. Обработчик 09:EC
(payload 00 03 = 2.4G silence, 00 04 = 5.8G
по `wlm_lk_ctrl_set_sdr_param`) следует искать на стороне, адресованной
DUML-кадром: либо main SoC (eagle2 userspace dji_wlm — там же отправитель,
возможен локальный loopback в SDR-драйвер), либо RC-сторона (WA530/RC Pro —
см. существующий `wa530_09ec_recheck.md`). Для Mavic 3 pigeon в аудите по
09:27/09:EC/06:8C корректный статус — **NEGATIVE: static encoding not found;
dynamic registration not traced**, а не `not present on modem`.

## 5. Заметки для продолжения

- PCR-канал с фильтром 0a:f0 (@0x100e2938) — единственный захардкоженный
  DUML-маршрут в cp RTOS; стоит идентифицировать сервис 0a:f0 отдельно.
- Динамическая регистрация frame-callback ([ctx+0x418]) перезаписывается
  не прямой записью в ctx — регистрация идёт через API с ctx-параметром;
  полный список DUML-команд pigeon cp требует трассировки от
  `dji_pcr` register-функций (Ghidra headless по base 0x10000000).
- Таблица {fn,fn,name} в `dji_hdvt_uav` .text @0xbfe0+ — кандидат на
  внутреннюю cmd/event-таблицу userspace-диспетчера модема (duss_event);
  имена в .rodata 0x43xxx позволят восстановить семантику.
