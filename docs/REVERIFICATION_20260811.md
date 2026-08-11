# Перепроверка статических выводов свежим дизассемблированием (2026-08-11)

Независимый повторный анализ ключевых утверждений из
[`FIRMWARE_CORPUS.md`](FIRMWARE_CORPUS.md), [`DUML_COMMAND_AUDIT.md`](DUML_COMMAND_AUDIT.md),
[`WLM_CMDSET_51.md`](WLM_CMDSET_51.md) и [`PERCEPTION_DUML_HANDLER_MAP.md`](PERCEPTION_DUML_HANDLER_MAP.md).
Метод: свежие проекты Ghidra 12.1.2 headless + кросс-проверка rizin 0.9.1 /
`readelf` / Python-резолверы, без использования прошлых результатов как входных
данных. Каждый артефакт перед анализом сверен по SHA-256 и Build ID с
задокументированными значениями — расхождений нет ни в одном наборе.

Сырые артефакты перепроверки (проекты Ghidra, скрипты, дампы таблиц,
декомпиляции, JSON резолвера) лежат в `.scratch/reverify_20260811/` (в git не
входят). Подробные пообластные отчёты:

- `wa530_09ec_recheck.md`
- `rcpro2_cmdset51_recheck.md`
- `wa530_perception_1058_recheck.md`
- `rm510_0730_0719_09ec_recheck.md`

## Сводная таблица вердиктов

| Область | Утверждение | Вердикт |
|---|---|---|
| WA530 `dji_wlm` | `09:EC`, `wlm_lk_ctrl_set_sdr_param` @ `0x17dde0`, payload `00 03/00 04/00 00`, ≤3 retry | **CONFIRMED**, без address drift |
| RC Pro 2 `dji_wlm` v576 | Таблица 0x51: 82 слота, 35 заполнено, 30 req + 5 ACK-only (05/07/1D/1F/24) | **CONFIRMED** |
| RC Pro 2 `dji_wlm` v576 | `51:1A` — service mode switch | **CONFIRMED**, семантика полностью восстановлена |
| RC Pro 2 `dji_wlm` v576 | `51:19` = `wlm_modem_onoff_control`, ветвление по длине > 7 | **PARTIAL**: cmd_type = payload[3], не payload[0] |
| RC Pro 2 `dji_wlm` | Одинаковый набор handlers 0x51 в build 139 и 576 | **REFUTED в мелочи**: слот `0x2F` есть только в v576 |
| RC Pro 2 `dji_wlm` | `51:42` = `wlm_ability_nego_result_req` | **CONFIRMED** (оба build) |
| WA530 `dji_perception` | Регистратор @ `0x282689c`, 151 call site | **CONFIRMED** (151 `bl` + 3 tail-call `b`) |
| WA530 `dji_perception` | `10:58` отсутствует среди разрешённых регистраций | **CONFIRMED** (покрытие 153/154 сайта, 169 регистраций) |
| WA530 `dji_perception` | Cmdset 0x10 = autotest/fstest, ID 01–09/10–15/1B/22/23/81/90 | **CONFIRMED с дополнениями**: пропущен `10:80`; строго fstest только 01–0F |
| WA530 `dji_perception` | gesture-строки — таблица параметров `CapGestureCtrl` | **CONFIRMED** |
| WA530 `dji_perception` | Регистратор `0x1b36e0c`: 03:AA, 06:50, 0A:F0, 00:01 | **CONFIRMED** (ровно 4 сайта) |
| RM510 `dji_link` | Таблицы cmdset 00/07/18; cmdset 06 отсутствует → `06:72` уходит по UART | **CONFIRMED** |
| RM510 `dji_link` | `07:30` читает 2 байта, пишет vendor slot 6 + `country.bin` | **CONFIRMED** |
| RM510 `dji_link` | `07:19` игнорирует payload, возвращает alpha-2 из slot 6 | **CONFIRMED** |
| RM510 `dji_wlm` | `09:EC` @ `0x6450c`, та же логика payload | **CONFIRMED**, без address drift |

Итог: из 15 проверенных утверждений 13 подтверждены полностью, 1 подтверждено
частично (`51:19`, неточность в описании layout payload), 1 опровергнуто в
части строгого равенства (таблица 0x51 v139 vs v576 отличается одним слотом).
Ни одно ключевое практическое следствие аудита не поколеблено.

---

## 1. WA530 V01.00.0300 `dji_wlm`: DUML `09:EC`

Артефакт: `.scratch/wa530_0300/e4/root/system/bin/dji_wlm`, SHA-256
`66da35f73a67bddffb9bcd7564c7b7ff5ac1401fe68703f8476426637a9ce593`, Build ID
`44cbbdf500c75ce413333428c435b78d` — совпадение точное. Несмотря на пометку
«stripped», локальная таблица символов присутствует.

Адреса без drift: `wlm_lk_ctrl_set_sdr_param` = `0x17dde0` (размер 1676),
`wlm_event_send_sync` = `0x15e620` (размер 332).

Формирование кадра (rizin, raw VA):

```
0x0017df90   mov  w8, 0xec
0x0017df98   movk w8, 9, lsl 16        ; w8 = 0x000900ec
0x0017dfa8   str  w8, [x19]            ; header кадра = cmd 0xEC, cmd set 0x09
0x0017dfb4   str  w10, [x19, 0x10]     ; payload length = 2
0x0017dfb8   strb wzr, [x19, 0x14]     ; payload[0] = 0x00
```

Выбор payload[1]:

- `arg2 == 1` → `0` (`00 00`, reset/обычная ветка); выбирается вызывающей
  стороной, не вычисляется внутри;
- `wlm_get_agt_rpt_freq_band()`: 0 (2G) → `3` (`00 03`, лог «silence sdr
  2.4G»), 1 (5G) → `4` (`00 04`, лог «silence sdr 5.8G»), 2 (auto) → `4`;
- сбой `wlm_link_ctl_query_freq_ability()` → fallback `3`.

Retry: три развёрнутых вызова `wlm_event_send_sync` (`0x17e2b8`, `0x17e33c`,
`0x17e3c0`), каждый под условием ошибки предыдущего, с логами «try again …
index %u» и литеральными индексами 0/1/2 → максимум 3 попытки.

Уточнение к формулировкам документации (не противоречие): маппинг включает
auto→04 и query-failure→03, а ветка `00 00` выбирается вторым аргументом
функции.

## 2. RC Pro 2 (rc520) `dji_wlm`: таблица cmdset `0x51`

Артефакты (точное совпадение hash/Build ID):

| Build | SHA-256 | Build ID |
|---|---|---|
| v576 (V55.31.05.76) | `0d62e3b3…0b7220` | `a0a736c567c361bdd1568aac3ac99722` |
| v139 | `971604883f…2fcc7cbe` | `41970d8e26ecf0edee6c78f4f9f7f5d7` |

Символы восстановлены из встроенной `.gnu_debugdata` (mini-symbols) каждого
ELF; enum-имена — из `libwlm.so` (Build ID `15b96461fe04f99a3c24dfb03c966607`).

### Регистрация и формат таблицы

`wlm_et_cb_start` (v576 `0x140974`; v139 `0x123398`) вызывает
`duss_event_create_client_more_config` с таблицей v576 `0x206e80` /
v139 `0x1ceca8` и полем count `0x52` (82). Принадлежность таблицы к cmdset
`0x51` доказана следующим же вызовом
`duss_event_modify_dynamic_command(handle, 0x51, 5, …)`, чей callback совпадает
со слотом 05. Формат: 82 записи × 24 байта `{req_handler, ack_handler, aux}`,
индекс слота = cmd ID.

v576: 35 непустых слотов (01, 02, 03, 05*, 06, 07*, 08, 09, 0A, 0D, 0F, 10, 15,
18, 19, 1A, 1B, 1D*, 1E, 1F*, 20, 21, 22, 23, 24*, 27, 29, 2A, 2C, 2E, 2F, 34,
41, 42, 51; `*` = ACK-only). 30 request-handlers; ACK-only ровно
`05/07/1D/1F/24` (`wlm_route_switch_ack`, `wlm_link_ctrl_ack`,
`wlm_power_ctrl_set_agent_ack`, `wlm_rm_recv_local_freq_info_ack`,
`wlm_agent_test_ack`). Полные дампы: `ghidra_table_v576.txt`,
`ghidra_table_v139.txt` в `.scratch/reverify_20260811/`.

### `51:1A` = `wlm_service_mode_switch_req` — семантика восстановлена

Источник (по лог-строкам): `modules/link_switch/wlm_link_sw.c`. Payload
(msg + 0x14):

| Байт | Поле | Значения |
|---|---|---|
| [0] | version | — |
| [1] | service_type | 0=`SERVICE_LIVEVIEW`, 1=`SERVICE_DOWNLOAD` (проверка `<=1`) |
| [2] | mode | liveview: 0=`LIVEVIEW_SDR`, 1=`LIVEVIEW_HYBIRD`, 2=`LIVEVIEW_WIFI`; download: 0=`DOWNLOAD_COMMON`, 1=`DOWNLOAD_WIFI_HIGHSPEED` |
| [3..] | SN | строка серийника целевого устройства |

Невалидные type/mode → `wlm_mode_switch_resp(msg, 9, 9, 9, 0)`, ret `-0x3EB`.
Есть подавление дублей по (sender, seq), отказ при нулевом числе peer-устройств,
выбор цели по SN (`wlm_peer_dev_list_find`) или UAV-узел по умолчанию
(неудача → resp 7,7,7), затем link-mode gating в
`wlm_tranfer_link_mode_to_service_mode`.

### `51:19` = `wlm_modem_onoff_control` — PARTIAL

- Имя и файл `modules/power_ctrl/wlm_power_ctrl.c` подтверждены лог-строкой.
- Ветвление по длине подтверждено: `msg_len < 8` → return `-0x3F3`
  («invalid data, msg_len %u, sizeof %zu», sizeof=8). То есть ≤ 7 отвергается,
  обработка требует ≥ 8 (> 7) — как задокументировано.
- **Расхождение с документацией**: первый байт payload — это **msg_ver**, а не
  cmd_type. По собственной лог-формат-строке функции
  `"msg_ver:%u, link_type:%u, cmd_type:%u, control_type:%u"` layout:
  payload[0]=msg_ver, [1]=link_type, [2]=control_type, [3]=**cmd_type**.
  Диспетчеризация: `if (*(char*)(param_2+0x17) == 1)` — это payload[3]/cmd_type
  == 1; `control_type == 3` → `wlm_skip_sdr_agent_control(1)`.
  Требуется правка [`DUML_COMMAND_AUDIT.md`](DUML_COMMAND_AUDIT.md) (фраза про
  «первый ASCII-символ identity в payload[3]» близка к истине, но формулировку
  про «первый payload-derived byte = cmd_type» следует заменить на точный
  layout).

### `51:42` = `wlm_ability_nego_result_req` — CONFIRMED

v576 slot `0x42` req @ `0x1dbd04`, ack = `wlm_ability_nego_result_ack`;
аналогично v139 @ `0x1a8ab4`.

### Сравнение build 139 vs 576 — REFUTED в части строгого равенства

v139: 34 непустых слота / 29 request-handlers (не 35/30). Единственное
отличие: слот **`0x2F` `wlm_agt_mgr_general_control_req` присутствует в v576 и
пуст в v139**. Все остальные 34 слота, включая все ACK-only и ключевые
19/1A/42, идентичны. Практический вывод аудита (sweep упирается в те же
контракты) не меняется, но фразу «набор активных handlers совпадает между
build 139 и 576» в [`FIRMWARE_CORPUS.md`](FIRMWARE_CORPUS.md) следует
смягчить до «отличается одним слотом 0x2F».

## 3. WA530 `dji_perception`: отрицательный вывод по `10:58`

Артефакт: `.scratch/wa530_0300/e4/root/system/bin/dji_perception` (ELF64 PIE,
ARM64, NDK r26d, ~75 МБ), SHA-256
`0d7b9498629c13b18c514afd873a99d70a149e9bd378c15660b89cd64aae0f80`, Build ID
`178fb158bd131f48032b52e0df45104fd8933a61` — точное совпадение.

Метод (без Ghidra, независимый резолвер):

1. Полный numpy-скан `.text` (vaddr `0x1190860`, size `0x3463790`) с декодом
   всех `bl`/`b` imm26 и вычислением target в int64. Важно: первый проход
   воспроизвёл тот же класс ошибки, что и отброшенный «наивный» резолвер
   2026-07-24 (uint32 sign-extension underflow, потеря 23 backward-call
   сайтов) — исправлено и сверено.
2. Кросс-проверка rizin (`aac` + `axt`): независимо даёт те же **151 BL-xref**
   к регистратору и **4 BL-xref** к `0x1b36e0c`.
3. Разрешение аргументов: capstone backward-walk по w0–w3 через
   `mov/movz/movk/mov wN,wzr/orr`, `adrp+add`, `adr`, `ldrb/ldrh/ldr` с чтением
   файловых данных; table-driven циклы разрешены дампом статических таблиц из
   `.data.rel.ro` (+`.rela.dyn` addends).

### Результаты

- **Регистратор @ `0x282689c`: 151 `bl`-сайт** (точно как задокументировано)
  **плюс 3 tail-call `b`-сайта** (`0x1672a6c`, `0x2182974`, `0x2184264`) → 154
  ссылки. «151» в документации = CALL-xrefs; уточнить формулировку.
- **Покрытие 153/154 сайта → 169 регистраций, 164 уникальные пары.**
  `10:58` не встречается нигде: ни среди 169 разрешённых регистраций, ни в
  регистрационных таблицах данных (`0x4632ee0`/`0x4633020`), ни среди
  регистраций `0x1b36e0c`.
- Единственный непокрытый сайт: `0x11ae7c0` внутри passthrough-wrapper
  `0x11ae308` (регистрирует (set,id) от вызывающего). У wrapper **ноль
  статических вызывающих** (нет bl/b/blr, нет rela-addend, не экспортируется) —
  возможен только runtime/indirect вызов. Отрицательный вывод на этот сайт
  честно не распространяется.
- **Cmdset 0x10 (26 сайтов)**: ID 01–0F (таблица `0x47044ec` → handler
  `0x1543274` = `req_autotest_handle_cb` → `fstest_adapter_handle_cmd`, имена
  доказаны лог-строками), 1B (TerminalAction/Firefly push, **не** fstest),
  22×3 (`dummy_req_cb`), 23×3 (`delay_req_cb`), **80×1 (пропущен в
  документации**, tail-call `0x1672a6c`), 81×2 (`mcu_osd_sniff_cb`,
  `nav_osd_sniff_cb`), 90 (`device_test_request_callback`). Список ID из
  документации совпадает точно, но: (а) добавить `10:80`; (б) строго
  autotest/fstest — только 01–0F (и условно тестовые 22/23/90), 1B/81 —
  другие фичи.
- **Gesture-строки** (`gesture_control_enable/support/state`, `CapGestureCtrl`)
  — дескрипторы параметров в `.data.rel.ro` (`0x463c0a8`/`0x463c100`/`0x463c158`,
  соседи — `27_gimbal_tracking_ctrl_start`, `hand_ctrl_dire_hori_gain_ratio`);
  связи с DUML-регистрациями нет. CONFIRMED.
- **Регистратор `0x1b36e0c`**: ровно 4 сайта (все в `0x1ce2a28`):
  `03:AA` → `0x1ce2e2c`, `06:50` → `0x1ce310c`, `0A:F0` → `0x1ce3274`,
  `00:01` (`mov w0,wzr`) → `0x1ce33f0`. Без `10:58`. CONFIRMED.

Вывод: статический негатив по `10:58` на WA530 стоит; граница покрытия
(wrapper без статических вызывающих + теоретические runtime-пути) та же, что
задокументирована.

## 4. RM510: таблицы `dji_link`, `07:30`/`07:19`, `09:EC`

Артефакты (`fpga_tang_nano_9k_card_reader-spinal/.scratch/rc_rm510_20260723/`),
SHA-256/Build ID зафиксированы в `rm510_0730_0719_09ec_recheck.md`; Build ID
`dji_wlm` = `e14a06545de716c6332364c4c46cfa21` — совпадение. Символы —
из `.gnu_debugdata` mini-symbols (403 FUNC в `dji_link`).

### Таблицы handlers в `dji_link_event_start` @ `0x14fd0`

Восемь констант-указателей на таблицы и счётчики `0x100/0x61/0xd/0x36`;
таблицы — разреженные массивы stride 24 байта, индекс = cmd_id, handler-
указатели восстановлены из `.rela.dyn` (`R_AARCH64_RELATIVE`) независимым
Python-скриптом:

- `0x36000` = **cmdset 0x00**: `00:01 dji_event_common_get_device_version`,
  `00:0B sys_event_reboot`, `00:0E dji_event_heartbeat`, `00:32/36
  dji_event_{active,deactive}_config`, `00:4A dji_event_set_date`, `00:5B
  dji_event_ftpd_control`, `00:EA dji_event_handle_log_export`, `00:FF
  dji_event_query_device_info` — шесть публичных совпадений с
  dji-firmware-tools.
- `0x37848` = **cmdset 0x07**: `07:0B/0C wifi_mac`, `07:19
  dji_event_get_country_code` (slot `0x37aa0`), `07:30
  dji_event_set_coutry_code` (slot `0x37cc8`), `07:3C/3D bt_mac`, `07:5C
  mcu_bat_status_push`. Дистанция 07:30−07:19 = `0x228` = 23 слота × 24 —
  сходится с разностью ID.
- `0x3a8f0` = набор `18:35 lte_get_ci_test`, `18:37 dji_whoami_get_version`,
  `18:42 dji_event_report_status` (привязка к cmdset 0x18 — через пару
  request/handler 18:35 в `dji_wlm`, слабее публичных anchors 00/07).
- Пять таблиц (`0x37800, 0x37ce0, 0x37e18, 0x390c0, 0x399d8`) остаются без
  независимой привязки cmdset — как и признано в справочнике.
- **Cmdset 0x06**: ни одна таблица не привязана; ни один handler-name не
  относится к RC/stick. `rizin axt` по `duss_event_create_flex_route_client`
  находит ровно два регистратора (`dji_link_event_start`,
  `dji_whoami_event_start`) — вторая таблица тоже не cmdset-06.
- Следствие для `06:72` подтверждено через `dji.json`: route `60` →
  `{"target":"rc","index":0,"channel":"uart","protocol":"v1",
  "uart":{"interface":"/dev/ttyHS2","baudrate":115200}}`.
  Уточнение: строки `/dev/ttyHS2` нет ни в одном из шести бинарников — маршрут
  конфигурационный, исполняется framework-слоем, а не захардкожен в `dji_link`.

### `07:30` = `dji_event_set_coutry_code` @ `0x17e60` — CONFIRMED

```c
local_c0 = net_alpha2_to_country_code(param_2 + 0x14, 2, ...);
iVar2 = dji_vendor_write(6, &local_c0, 4);
if (iVar2 == 0 && local_c0 != 0) {
    save_country_code(local_c0, "/mnt/dji_persist/country.bin");
    duss_event_resp_ok(param_1, param_2);
```

Читаются ровно 2 байта payload (`param_2+0x14`, len 2); хвост кадра
`41550000415500000100` игнорируется. `net_alpha2_to_country_code` (`0x196a0`)
делает uppercase + memcmp по `country_table` (`0x3c600`, 0x7d записей × 16
байт). Запись: vendor slot 6 + `/mnt/dji_persist/country.bin`
(`save_country_code` @ `0x19934`, `fopen(path,"w+")`). Неизвестный код →
error `0xd6`, файл не пишется.

### `07:19` = `dji_event_get_country_code` @ `0x17db8` — CONFIRMED

```c
iVar2 = dji_vendor_read(6, &local_3c, 4);
if (iVar2 < 1) duss_event_resp_err(..., 0xd6, 0, 0);
else { country_code_alpha2(local_3c, local_40);
       duss_event_resp_data(param_1, param_2, local_40, 2); }
```

Request payload (включая байт `c0` из кадра FreeFCC) не читается вообще;
ответ — ровно 2 байта alpha-2 из vendor slot 6.

### `09:EC` в RM510 `dji_wlm` @ `0x6450c` — CONFIRMED, без drift

Mini-symbol `0x6450c` (размер 1400), декомпиляция по тому же адресу.
Header `*param_1 = 0x900ec`; payload[0]=0; payload[1]: `0` при `param_2==1`
(reset), `3` (2.4G) / `4` (5.8G, включая auto) по `wlm_get_agt_rpt_freq_band`,
fallback `3` при сбое query. До 3 попыток `wlm_event_send_sync`. Callers:
`wlm_freq_auto_avoid` (`0x64484`) и `wlm_link_control_wifi_preprocess`
(`0x64e10`, `0x64e3c`) — событийный вызов, фиксированного таймера нет.
Это ground-side подтверждение, симметричное air-side WA530 (раздел 1).

## Необходимые правки в документации (по итогам перепроверки)

1. `DUML_COMMAND_AUDIT.md` / `WLM_CMDSET_51.md`: `51:19` — cmd_type =
   payload[3], layout `{msg_ver, link_type, control_type, cmd_type}`; убрать
   формулировку «первый payload-derived byte = cmd_type».
2. `FIRMWARE_CORPUS.md`: «набор handlers 0x51 совпадает между build 139 и
   576» → «отличается одним слотом `0x2F wlm_agt_mgr_general_control_req`
   (есть в v576, пуст в v139)».
3. `PERCEPTION_DUML_HANDLER_MAP.md` / `FIRMWARE_CORPUS.md`: «151 call site» →
   «151 `bl` + 3 tail-call `b` (154 ссылки)»; в инвентарь cmdset 0x10 добавить
   `10:80`; уточнить, что строго fstest — только ID 01–0F.
4. `RM510_DUML_COMMAND_REFERENCE.md`: отметить, что `/dev/ttyHS2` задаётся
   через `dji.json` и как строка в бинарниках отсутствует.

## В работе (не входит в этот срез)

Параллельно идёт инвентаризация eMMC-дампов дронов на homesrv
(`/mnt/toshiba_6tb/dumps_emmc_dji/`: EMMC_Data_3/4/5_64gb/M3/M3_2/M4,
air3_1/air3_2, `Mavic3_emmc_2.bin`) и пакетов `dji_firmware/` (rm510 0700,
rm700 0803, wa345t 0501, wa530 0300). Цель — найти flight-MCU и sparrow2
transmission-MCU прошивки, которых не хватало для закрытия `03:AF`,
`09:27/0xffff0063` и `06:8C`, и кросс-проверить `10:58`/`09:EC` на других
платформах. Результаты будут оформлены отдельным документом; рабочий каталог —
`/mnt/toshiba_6tb/dumps_emmc_dji/_analysis/` на сервере.
