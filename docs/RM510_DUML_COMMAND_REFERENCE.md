# Справочник DUML-команд из RM510 / DJI RC2

Дата среза: 2026-07-23; обновлено 2026-07-24.

Это статический справочник по командам, которые удалось восстановить из
сохранённых userspace ELF пульта RM510, с cross-check по live DUML-потокам
FreeFCC. Это не полный каталог всего DJI protocol: часть команд формируется
динамически, часть обслуживается отдельными MCU/aircraft components, а не
Android/Linux userspace пульта.

Связанный аудит команд, которые отправляет само приложение:
[`DUML_COMMAND_AUDIT.md`](DUML_COMMAND_AUDIT.md).

## Как читать таблицы

| Метка | Значение |
|---|---|
| `SEND` | В бинарнике найдено формирование/отправка фиксированной пары |
| `QUERY` | Синхронный запрос с ожиданием результата |
| `PUSH` | Отправка состояния/события без доказанного request/response contract |
| `HANDLER` | Входной обработчик или регистрация команды |
| `EXTENDED` | Внутренний DUSS identifier шире обычной пары из двух однобайтовых полей |

Имя функции надёжно показывает контекст, но не всегда полностью раскрывает
payload. Поэтому в колонке «уровень» отдельно отмечены точные и контекстные
выводы.

## Исходный корпус

Сохранённый рабочий corpus:
`fpga_tang_nano_9k_card_reader-spinal/.scratch/rc_rm510_20260723/`.

| Файл | Размер | Build ID | SHA-256 |
|---|---:|---|---|
| `dji_wlm` | 507296 | `e14a06545de716c6332364c4c46cfa21` | `f505f027b09e5fee7eaca0f6089c41a866d630e5e0fe70087ba875543f8dd013` |
| `dji_sdrs_agent` | 252472 | `dc7b9ab48d0e18d593c63ad78a65be1e` | `b1092ec65d76672182cc0c0c1d125be58bf8ab447ad3e3fbfab9eb37019d990e` |
| `dji_link` | 264080 в исходной копии | `370079f72741e7dee0216f75333e0b86` | `60478c1f366675e5baf9194cd5b44ebe26ef2d23ae8c662a773aa29be5ddab31` |
| `libduml_frwk.so` | 1494128 | `b70586902d0f6e7f8f7926af5d89b391` | `0a8ec725aa6b72d4cc087b3b95120928138a25f6694c98d9a441bfd071ad34ce` |
| `libwlm.so` | 294952 | `2d6d64b85a03802c6e10a0b1016d1d69` | `2b63101f2b01aceab6de6e63afc7dd8694710d4ab8e6e154adfb023b73337931` |
| `dji_mb_ctrl` | 20024 | `698984164eff96d0f90a4e415186fd9f` | `d0ae0666c9937afe3ed604576ca7d92b661c7a56b9c6a838572c469326175cb2` |

Для `dji_wlm`, `dji_sdrs_agent` и `dji_link` использована встроенная
`.gnu_debugdata`: символы позволяют привязать константу команды к конкретной
функции. `libduml_frwk.so`, `libwlm.so` и `dji_mb_ctrl` важны как transport/
framework evidence, но в этом проходе новых фиксированных product-команд из
них не извлечено.

Во время извлечения `.gnu_debugdata` `llvm-objcopy` нормализовал рабочую
scratch-копию `dji_link`: текущий контейнер имеет размер 264072 и SHA-256
`cd02dd84f16b4ad1d18c96fefa203740aa07290a547ec429f04ba98e6e77122c`.
Build ID и кодовые адреса не изменились; в таблице сохранены размер и hash
исходной полученной копии. Рядом оставлены производные
`dji_link.gnu_debugdata.xz` и `dji_link.gnu_debugdata.elf`.

Конфиг маршрутов RM510 `dji.json` имеет SHA-256
`5222400c9c6a4cc747a756e7e5c7a1e9fa9f455470fd8614ec434ed7cf83589a`.
Для aircraft-side cross-check также использованы:

| Артефакт | Размер | Build ID | SHA-256 |
|---|---:|---|---|
| WM260 `dji.json` | 267744 | — | `849f60e02f4eb07c3cd1a1ecece4eb167deca82ab217bc48fb28dac9930b4789` |
| WM260 `dji_perception` | 20462408 | `136fcec833fc5af8eac717e1785c38e4` | `76a08fcf22db6c9a66c9a01192bbc0984d078a5329d8d8d7f946c45fe0e90822` |
| WA341 `dji_sys` | 1742280 | `ed5cc566fae4ef008a19727014ca2c00` | `beb09425c85e5725aef7a29971dfc18b2d7e93102f3ad2a706dce1be5c958234` |
| WA530 `dji_wlm` | 2279160 | `44cbbdf500c75ce413333428c435b78d` | `66da35f73a67bddffb9bcd7564c7b7ff5ac1401fe68703f8476426637a9ce593` |
| WA530 `dji_perception` | 75631600 | `178fb158bd131f48032b52e0df45104fd8933a61` | `0d7b9498629c13b18c514afd873a99d70a149e9bd378c15660b89cd64aae0f80` |

## Маршрутизация opaque-команд полного FCC-профиля

DUML destination byte кодируется как
`(index << 5) | (module_type & 0x1f)`. Поэтому назначения ранее opaque
групп FreeFCC можно определить независимо от их payload:

| Команда | Raw destination | Symbolic host | Подтверждённый маршрут |
|---|---:|---|---|
| `00:00` | `0x1f` | `all:0` | Специальный broadcast destination; стандартный device ping |
| `03:AF` | `0x03` | `flight:0` | WM260 `dji_sys` → ICC `/dev/icc_dev`, send `ap0-mcu0-1.0`, receive `mcu0-ap0-1.0`, protocol `v1` |
| `06:72` | `0x06` | `rc:0` | RM510 `dji_link` → UART `/dev/ttyHS2`, 115200, protocol `v1` |
| `06:8C` | `0x09` | `vt_air:0` | Air-side transmission MCU; при hybrid route RM510 передаёт через `vt_gnd:7` |
| `10:58` | `0x12` | `bvision:0` | На WM260 и WA530 локальный `perception_service`, процесс `dji_perception` |

`dji_link_event_start` регистрирует локальные плотные handler tables для
cmdsets `00`, `07` и `18`, но не для `06`. Следовательно, `dji_link` только
пересылает `06:72`; реализация находится в отдельном RC MCU. Такой образ
`rc331_0600` позднее найден отдельно от системного OTA и подтверждает
`06:72 = set stick value lock`, `06:74 = get stick value lock`. Аналогично
`vt_air:0` не равен userspace-сервисам
`dji_sdrs_agent` (`vt_air:4`) или `dji_wlm` (`vt_air:7`): `06:8C`
обслуживается transmission MCU на борту.

Поэтому точные функции `03:AF` и `06:8C` пока остаются `UNKNOWN`.
`03:AF` принимает не Linux `dji_sys`, а отдельный flight-controller MCU:
`dji_sys` только маршрутизирует raw destination `0x03` в host `flight:0`
(`0x30`) по ICC. Firmware этого MCU в сохранённом WM260 Android/eMMC-корпусе
нет. Семантика RC stick lock подтверждена кодом отдельного MCU-образа; точное
соответствие каналов физическим органам управления и эффект `ch5f` требуют
контролируемого live readback.

`00:00` при этом закрывается отдельно. В WA341 `dji_sys` slot
`cmd_set=0/cmd_id=0` указывает на `sys_event_dev_ping` (`0xbe650`) и
`sys_event_dev_ping_ack` (`0xbe750`). Request handler в ветке
`0xbe6e8–0xbe724` отвечает исходными request data/length через
`duss_event_resp_data_v2/v3`. Поэтому `00 00 01` в профиле — echo token
broadcast ping, а не изменение региона, activation или FCC primitive.

Для `10:58` подтверждён только конечный userspace-получатель:
`bvision:0/perception_service` в `dji_perception`. WA530 и WA234 не содержат
`10:58` среди статически разрешённых регистраций конкретного helper, но
resolver WA530 неполон, а WA234 оставляет два runtime-fed пути. Широкий
ARM32-скан WM260 не локализует registration helper и для отрицательного вывода
непригоден. Поэтому точный handler и смысл `03 01 00` остаются `UNKNOWN`.

Публичный DJI midware здесь нельзя переносить буквально: `CmdSet.EYE(10)`
означает десятичный `10`, то есть cmdset `0x0A`, а FreeFCC отправляет десятичный
`16`, то есть `0x10`. Совпадение cmd id `0x58` и payload с
`GetPerceptionGesture` недостаточно и было отброшено. Одинаковый `10:58` в
начале и конце профиля также не доказывает старые противоположные действия
«enter/exit service mode».

Локальный DJI Fly 1.19.4 добавляет только отрицательное client-side evidence:
в `libdatajar.so` (SHA-256
`1a7abeb4cd4f51fae4c3d0de7243adbdcaa705b34aeea310af8a9841563c0527`)
есть metadata strings `DataEyeGetPerceptionGesture`,
`GetPerceptionGesture`, `CmdIdEYE` и `CmdSet`, но embedded DEX защищён.
Наличие класса не устраняет несовпадение `0x0A` против `0x10` и не раскрывает
WM260 receiver handler.

## `dji_wlm`

| Команда | Тип | Функция | Call site | Что делает | Уровень |
|---|---|---|---:|---|---|
| `08:65` | `QUERY` | `wlm_bw_bybrid_task` | `0x35fd8` | Синхронный запрос внутри задачи hybrid bandwidth; точный payload contract не декодирован | `INFERRED` |
| `07:A120` | `EXTENDED/PUSH` | `event_track_send` | `0x59170`, `0x59460` | Event tracking/report. `0xA120` — расширенный ID, а не legacy byte `0x20` | `INFERRED` |
| `51:04` | `PUSH` | `wlm_push_dev_osd` | `0x5cc1c` | Device OSD/status push | `CONFIRMED` по symbol context |
| `51:05` | `PUSH` | `wlm_push_link_sw_result2sysmode`; `wlm_inform_route_sw` | `0x5e2c0`, `0x67ec8` | Результат link switch и уведомление route switch | `CONFIRMED` по symbol context |
| `51:07` | `QUERY` | `__wlm_link_ctl` | `0x670e8` | Link control request; payload ещё не декодирован | `INFERRED` |
| `51:0C` | `PUSH` | `wlm_link_state_manage_task` | `0x5b910` | Фиксированный 10-байтный local all-link-mode/composite-link-state report | `CONFIRMED` по data flow |
| `51:14` | `PUSH` | `wlm_link_state_manage_task` | `0x5b6dc` | Переменный neighbour/device-link list: `2 + 49 × N` байт | `CONFIRMED` по data flow и live length |
| `09:21` | `QUERY` | `wlm_lk_ctrl_set_sdr_param` | `0x645bc`, `0x64620`, `0x64684` | Получение текущего SDR/link состояния; до трёх попыток | `CONFIRMED` для control flow |
| `09:EC` | `SEND` | `wlm_lk_ctrl_set_sdr_param` | RM510 `0x64900`, `0x64974`, `0x649e8`; WA530 sender `0x15e620` из функции `0x17dde0` | Wi-Fi/SDR coexistence: `00 03` silence SDR 2.4G, `00 04` silence SDR 5.8G, `00 00` reset/ordinary branch; до трёх попыток при ошибке | `CONFIRMED`, независимо на ground и air |
| `18:35` | `SEND` | `wlm_select_upgrade_case`; `wlm_capture_dongle_log` | `0x71334`, `0x71564` | Один multiplexed diagnostic/control ID к host `0x0e06`; upgrade-case и capture dongle log различаются payload/action | `CONFIRMED` по двум функциям, payload layout частичный |

`09:EC` вызывается событийно при переключении coexistence/частот. Жёсткого
цикла «каждые 10 секунд» в этой функции не найдено. WA530 независимо
подтверждает этот contract: строки в `dji_wlm` прямо называют Wi-Fi band
2G/5G/auto и ветви `silence sdr 2.4G` / `silence sdr 5.8G`, после чего
`wlm_event_send_sync` повторяется не более трёх раз.

### Link-state payload `51:0C` и `51:14`

В `wlm_link_state_manage_task` базовый raw ID равен `0x0051000c`. Вызов по
`0x5b910` отправляет его как `51:0C` с фиксированной длиной 10 байт:
`wlm_get_local_all_lk_mode` и `wlm_get_local_comp_lk_sta` собирают локальные
режимы/сводное состояние SDR, LTE, Wi-Fi и command/data links.

Для neighbour report код прибавляет к ID `8`, получая `0x00510014`, и вызывает
отправку по `0x5b6dc`. Payload имеет layout:

| Offset | Размер | Значение |
|---:|---:|---|
| `0` | 1 | Число соседних устройств `N` |
| `1` | 1 | Ноль/reserved |
| `2 + 49 × i` | 23 | Identity/name region соседнего устройства |
| `+23` | 2 | LE u16 из peer link-state structure |
| `+25` | 1 | Peer state/type byte |
| `+26` | от 3 | Link modes, заполненные `wlm_dev_link_get_lk_mode` |
| `+29`, `+33`, `+37` | 3 × 4 | Три LE u32 timestamp/age values, исходные значения делятся на 1000 |
| `+41` | 8 | Нули/reserved |

Размер записи равен 49 байтам, полный размер — `2 + 49 × N`. Live payload
длиной 51 байт поэтому означает ровно одного соседа. Первые 23 байта записи
копируются из identity/name region peer structure; это объясняет, почему в
живом `51:14` присутствует полный aircraft serial. Строка журнала firmware
называет записи `neighbour` и печатает для них SDR/LTE/Wi-Fi state, receive
timestamps, command/link/video/data modes.

`wlm_forward_msg_send` — общий forward path: вызывающий передаёт ID во время
выполнения. Такие команды нельзя честно добавить в таблицу как фиксированные
пары только по этому wrapper.

## `dji_link`

| Команда | Тип | Функция | Call site | Что делает | Уровень |
|---|---|---|---:|---|---|
| `07:A120` | `EXTENDED/QUERY` | `dji_init_task_entry` | `0x134b8` | Внутренняя синхронизация/event-track при запуске сервиса | `INFERRED` |
| `08:32` | `PUSH` | `dji_command_live_view_push_to_rc` | `0x1c270` | Live-view state push на RC | `CONFIRMED` по symbol context |
| `00:32` | `PUSH` | `dji_command_active_push` | `0x1c3b8` | Activation state push | `CONFIRMED` по symbol context |
| `00:32` | `PUSH` | `dji_command_active_auth_push` | `0x1c514` | Activation authorization push; тот же ID, другой payload path | `CONFIRMED` по symbol context |
| `00:32` | `HANDLER` | `dji_event_active_config` | table slot `0x364b0` | Multiplexed activation protocol; первый payload byte выбирает subcommand | `CONFIRMED` по registration и data flow |
| `00:E5` | `HANDLER` | `dji_event_handle_djicare` | table slot `0x37578` | DJI Care whitelist/bind/unbind protocol | `CONFIRMED` по registration и data flow |
| `06:C4` | `SEND` | `dji_command_deactive_wipe_data` | `0x1c6a8` | Deactivation/data-wipe command | `CONFIRMED` по symbol context |
| `06:A5` | `QUERY` | `dji_command_set_mcu_active` | `0x1c7d4` | Установка MCU active state/flag | `CONFIRMED` по symbol context |
| `07:19` | `HANDLER` | `dji_event_get_country_code` | table slot `0x37aa0` | Читает vendor country slot `6`, преобразует в alpha-2 и возвращает 2 байта | `CONFIRMED` по registration и data flow |
| `07:30` | `HANDLER` | `dji_event_set_coutry_code` | table slot `0x37cc8` | Читает первые 2 payload bytes как alpha-2, пишет vendor slot `6` и `country.bin` | `CONFIRMED` по registration и data flow |
| `18:35` | `HANDLER` | `lte_get_ci_test` | table slot `0x3ade8` | Subcommand `02` возвращает 3-байтное LTE device state | `CONFIRMED` по registration и data flow |
| `18:39` | `SEND` | `send_fusion_info_to_lte` | `0x1cb8c` | 27-байтный sparse report с тремя WLM link-ratio в LTE service, destination `0x0e06` | `CONFIRMED` по data flow |
| `51:10` | `SEND` | `send_lte_info_to_wlm` | `0x1ccb4` | 34-байтный LTE channel-state block в WLM, destination `0x0e07` | `CONFIRMED` по data flow |
| `0200:0D05` | `EXTENDED/QUERY` | `secure_open_debug_auth` | `0x1e844` | Внутренняя secure debug authorization | `CONFIRMED` по symbol context; wire layout расширенный |

`dlink_forward_message_with_no_ack` и `duss_send_pack` также принимают
команду во время выполнения. Наличие вызова wrapper не раскрывает полный набор
пересылаемых ID.

`18:39` формируется после успешного `libwlm_channel_get_param`. Payload заранее
обнуляется, затем три ratio записываются в offsets `0`, `6` и `3`, а ещё один
state byte — в offset `25`; длина всегда 27 байт. Функция вызывается из
`sys_process_cb` и `dji_stream_recv_task`, то есть это event/stream-side
синхронизация WLM → LTE, а не периодический FCC keepalive.

`51:10` копирует ровно 32 последовательных байта из LTE stream structure и
добавляет LE u16, получая длину 34 байта. В начале блока firmware отдельно
логирует `chan_num` и четыре соседних u16 values. Вызов находится в
`dji_send_stream_via_localsocket` и выполняется только для активного LTE
stream path. Точные имена всех полей без type information пока не
восстановлены.

### Входные handler tables `dji_link`

`dji_link_event_start` передаёт во flex-route client несколько плотных таблиц.
Каждый slot занимает 24 байта, поэтому command ID однозначно выводится как
`(handler_slot - table_base) / 24`. Таблицы `00`, `07` и `18` независимо
сходятся с именами функций и уже известными wire-командами:

| Команда | Handler | Подтверждённая локальная функция RM510 |
|---|---|---|
| `00:01` | `dji_event_common_get_device_version` | Device/version query |
| `00:0B` | `sys_event_reboot` | Reboot handler |
| `00:0C` | `dji_get_device_state_00_0c` | Device-state query |
| `00:0E` | `dji_event_heartbeat` | Heartbeat |
| `00:32` | `dji_event_active_config` | Activation/configuration protocol |
| `00:36` | `dji_event_deactive_config` | Deactivation configuration |
| `00:4A` | `dji_event_set_date` | Set date/time |
| `00:50` / `00:51` | `dji_set_serial_number` / `dji_get_serial_number` | Set/get serial |
| `00:5B` | `dji_event_ftpd_control` | FTP daemon control |
| `00:78` | `dji_get_sdcard_present` | SD-card presence |
| `00:88` | `dji_link_device_notify_req` | Device notification |
| `00:E0` / `00:E1` / `00:E2` | `secure_sync_secure_state` / `secure_req_open_debug` / `secure_open_debug_auth` | Secure debug flow |
| `00:E5` | `dji_event_handle_djicare` | DJI Care whitelist/bind/unbind |
| `00:EA` | `dji_event_handle_log_export` | Log export |
| `00:ED` / `00:EE` | `bb_event_cb_log_sync` / `bb_event_cb_log_info` | Blackbox log sync/info |
| `00:FF` | `dji_event_query_device_info` | Device-info query |
| `07:0B` / `07:0C` | `dji_event_set_wifi_mac_addr` / `dji_event_get_wifi_mac_addr` | Set/get Wi-Fi MAC |
| `07:19` / `07:30` | `dji_event_get_country_code` / `dji_event_set_coutry_code` | Get/set country |
| `07:3C` / `07:3D` | `dji_event_set_bt_mac_addr` / `dji_event_get_bt_mac_addr` | Set/get Bluetooth MAC |
| `07:5C` | `dji_event_mcu_bat_status_push` | MCU battery status |
| `07:B5` | `dji_event_get_status` | Device status |
| `07:E0` | `dji_event_hdvt_status_push` | HDVT status |
| `18:35` | `lte_get_ci_test` | LTE CI/device-state test |
| `18:37` | `dji_whoami_get_version` | WhoAmI/version |
| `18:42` | `dji_event_report_status` | Status report |

У `00:32` локальный handler поддерживает subcommands `00`, `01`, `35`, `37`,
`39` и `3B`. Первый byte FreeFCC payload `31 31 00 00 00` в этот список не
входит. У `00:E5` локальный DJI Care handler поддерживает `03`, `10`, `12` и
`17`; первый byte FreeFCC payload `32 32 01` также не поддерживается.

Это `NEGATIVE` только для локальных RM510 handlers. Удалённый contract
destination `0x6F` проверен отдельно по прошивке самолёта.

### Удалённый получатель `0x6F`

В канонической адресации DUML byte устройства кодируется как
`(index << 5) | (module_type & 0x1f)`. Поэтому `0x6F` — это
`module_type=0x0F`, `index=3`. В системном образе Mavic 3 / WM260
`/etc/dji.json` назначает `sec_service` именно на `s_to_p_air:3`.
Соответствующий процесс — `/bin/dji_sec`:

| Артефакт | Build ID | SHA-256 | Что подтверждает |
|---|---|---|---|
| WM260 `/bin/dji_sec` | `a03cde3cfd9b9ecd670d49900d54fc97` | `ea9356bec59b55e3be7da2fcc726b5a7f31320696fa044387e8842dca014b019` | Получатель `0x6F`, регистрация security/activation и DJI Care flows |
| WM260 `/lib/libdji_secure.so` | `7c2ca5291a184845a8c4106aab3e86fb` | `7c99e9fc2110e7173cdbebc880ec8edb0e9a461cdf16d13189c9632562732b40` | Реализация `sec_cmd_act_command_handler`, subcommand `0x31` и упаковщики DJI Care |

Для `00:32 / 31 31 00 00 00` aircraft handler принимает первый byte `0x31`
и формирует 59-байтный activation-state response: состояние активации,
product SN, version/security fields. Оставшиеся четыре request bytes в этой
ветке не используются. Это запрос состояния, а не запись и не команда
включения FCC.

`00:E5` на том же получателе обслуживает DJI Care binding/pairing/status
protocol. В `dji_sec` подтверждены bind/unbind events, RC matching, TEE
verification и отправка сообщений с `cmd_set=0`, `cmd_id=0xE5`.

Входной dispatcher `sys_sec_djicare_general` находится по ELF-адресу
`0x7384`. Он читает первый payload byte как subtype и принимает только:

| Subtype | Handler / действие | ELF-адрес |
|---:|---|---:|
| `01` | `sys_sec_djicare_binding_enable_req_receive`; требует payload не короче 7 bytes | `0x6d1c` |
| `02` | ставит внутреннее состояние `3` и возвращает однобайтовый ACK | `0x6df0` |
| `04` | `sys_sec_get_djicare_status_req_receive`; возвращает 113-byte status block | `0x6e40` |
| `09` | `sys_sec_djicare_verify_binding_req_receive` | `0x6ee0` |
| `0A` | `sys_sec_djicare_verify_remove_binding_req_receive` | `0x7110` |
| `FF` | `sys_sec_djicare_glass_connection_receive` | `0x72fc` |

Другой subtype уходит в default-ветку
`duss_event_resp_err(..., 0xE3, ...)`. Поэтому первый byte FreeFCC
`32 32 01` (`subtype=0x32`) в этой WM260 firmware однозначно
**не поддерживается** и не запускает DJI Care либо RF/FCC-действие.

Экспортированные упаковщики `libdji_secure.so` независимо подтверждают
трёхбайтовый prefix `command/subcommand/version`: `07 07 01` для bind
request, `08 08 01` для remove-binding request, `20 20 01` для bind-unmatch
push, `A1 A1 01` и `A2 A2 01` для результатов. `dji_sec` отдельно формирует
`10 10 01` для запроса RC whitelist. Упаковщика с prefix `32 32 01` в этой
библиотеке нет.

Адрес нельзя безусловно переносить на все поколения: в прошивках Air 3S и
Mavic 4 основной `sec_service` назначен на `ve_air:4`, а в WA341
`s_to_p_air:3` присутствует с урезанным набором security-команд. Поэтому
вывод `0x6F = dji_sec/sec_service` считается `CONFIRMED` для исследованного
WM260 snapshot, а не глобальным правилом DJI.

Локальный `07:19` не читает request payload вообще. `07:30` использует только
первые два байта как alpha-2 country code; оставшийся хвост не участвует в
этой реализации. Повторное использование `18:35` в `dji_wlm` для
upgrade/log operations показывает, что название команды зависит не только от
пары `cmd_set:cmd_id`, но и от destination/host.

## `dji_sdrs_agent`

| Команда | Тип | Функция | Call site | Что делает | Уровень |
|---|---|---|---:|---|---|
| `00:0100` | `EXTENDED/HANDLER` | `sa_event_ping` | registration `0x229a0`, create `0x229ac` | Эхо входного payload без изменения | `CONFIRMED`; raw ID `0x00000100` |
| `07:A120` | `EXTENDED/QUERY` | `sa_relay_task_entry` | `0x1f574` | Общий relay task operation | `INFERRED` |
| `07:A120` | `EXTENDED/QUERY` | `sa_relay_get_profile` | `0x1fcb0` | Получение relay profile | `CONFIRMED` по symbol context |
| `07:A120` | `EXTENDED/QUERY` | `sa_relay_route_switch` | `0x1fe94` | Relay route switch | `CONFIRMED` по symbol context |
| `07:A120` | `EXTENDED/QUERY` | `sa_relay_shutdown_pigeon` | `0x2001c` | Shutdown relay/pigeon path | `CONFIRMED` по symbol context |

Разные операции multiplexed через один расширенный ID и различаются payload.
`sa_heartbeat_task` отправляет команду, полученную в runtime (`w21`), на
destination `0x20:0e00`; фиксировать для неё выдуманную пару нельзя.

Кроме зарегистрированного ping, в ELF есть ещё четыре именованные служебные
функции без восстановленной числовой DUML-пары:

| Handler | Контекст |
|---|---|
| `sa_event_ping` | Возвращает входной payload; единственный handler с восстановленным raw ID `0x00000100` |
| `sa_event_sysreboot` | Не перезагружает Android: выполняет reset modem; при втором payload byte `0x02` удерживает modem reset |
| `sa_event_amt_nvram_rw` | Сегментированное AMT NVRAM read/write с file match и проверкой offset/length; активный вызов потенциально разрушителен |
| `sa_event_common_query_device_info` | Формирует строку build/device info и отвечает без отдельного retcode |
| `sa_event_rt_control_by_name` | По имени `/dev/...` включает или выключает соответствующий route-table item через `duss_mb_control_route_item` |

`sa_event_start` явно создаёт service client с `sa_event_ping` и raw ID
`0x00000100`. Для четырёх остальных функций прямой DUML registration site не
найден: они используются как служебные callbacks/helpers в других
service/parameter paths.

Отдельная статическая таблица по адресу `0x3abc8` содержит 16 регистраций
встроенного parameter manager. `duss_register_cmd` читает из каждой 24-байтной
записи `cmd_set`, `cmd_id`, request handler и общий ACK handler, затем вызывает
`duss_event_register_dynamic_command` по `0x34e3c`:

| Команда | Request handler | Назначение |
|---|---|---|
| `03:F3` | `reset_cfg_item_value_func` | Reset config item value |
| `03:F7` | `get_cfg_item_info_by_hash_func` | Metadata config item по hash |
| `03:F8` | `read_cfg_item_value_by_hash_func` | Read config item по hash |
| `03:F9` | `write_cfg_item_value_by_hash_func` | Write config item по hash |
| `03:FA` | `reset_cfg_item_value_by_hash_func` | Reset config item по hash |
| `03:FB` | `recv_fixed_send_cfg_by_hash_func` | Receive fixed-send config |
| `03:FC` | `req_fixed_send_cfg_by_hash_func` | Request fixed-send config |
| `03:E0` | `api_user_ask_table_func` | Запрос parameter table |
| `03:E1` | `api_user_ask_param_by_index_func` | Запрос metadata по index |
| `03:E2` | `api_usr_get_param_by_index_func` | Read parameter по index |
| `03:E3` | `api_usr_set_param_by_index_func` | Write parameter по index |
| `03:E4` | `api_usr_def_param_by_index_func` | Default/reset parameter по index |
| `01:40` | `get_cfg_item_info_by_hash_func` | Common-set alias для `03:F7` |
| `01:41` | `read_cfg_item_value_by_hash_func` | Common-set alias для `03:F8` |
| `01:42` | `write_cfg_item_value_by_hash_func` | Common-set alias для `03:F9` |
| `01:43` | `reset_cfg_item_value_by_hash_func` | Common-set alias для `03:FA` |

Эта таблица относится к parameter manager, а не к четырём оставшимся SDRS
handlers. Поэтому назначать `sa_event_sysreboot`, `sa_event_amt_nvram_rw`,
`sa_event_common_query_device_info` или `sa_event_rt_control_by_name` ID по
соседству нельзя.

## Границы метода восстановления таблиц (2026-07-24)

Приём «таблица = релокации `R_AARCH64_RELATIVE` в `.rela.dyn`» разобран на
четырёх сервисах и работает не везде. Сводка, чтобы не повторять тупики:

| Сервис | Как хранится таблица | Метод |
|---|---|---|
| `dji_sdrs_agent` (PM) | записи `{code, req, ack}` по 24 байта; `code` — константа в образе | работает, нумерация читается напрямую |
| `dji_link` | разреженные массивы, индекс слота = `cmd_id` | работает, но нужен якорь для базы |
| `dji_wlm` (RM510, RC Pro 2) | массив по 24 байта, `+0` req, `+8` ack | работает, нужен якорь |
| `dji_lte` | статически инициализированная pointer-table не найдена | **неприменим** |

Для `dji_lte` проверены оба признака: адреса известных handler'ов
(`lte_set_esim_req_handle`, `lte_trisim_req_handle`,
`lte_privatization_req_handle`, `lte_link_diag_req_handle`,
`lte_get_test_tool_cmd`) не встречаются в файле ни как 8-байтные слова, ни как
`r_addend` релокаций — из 2 083 символов релокациями разрешаются лишь шесть.
Регистрация связана со стартовым путём `lte_event_uav_start`, однако одного
отсутствия указателей недостаточно, чтобы восстановить точный механизм
построения таблицы. Опубликованная в
[`LTE_DUML_COMMAND_REFERENCE.md`](LTE_DUML_COMMAND_REFERENCE.md) версия
остаётся доступным источником нумерации; независимо перепроверить её именно
релокационным приёмом нельзя.

### Могут ли помочь прошивки дрона — проверено, нет

Логичная идея: взять aircraft-сторону (WA530, WA341, WA234) и найти там те же
обработчики с известными `cmd_set:cmd_id`, чтобы получить якоря для таблиц
пульта. Проверено — не работает, и по конкретной технической причине.

| Образ | `.rela.dyn` | Прямые указатели на handler'ы | Вывод |
|---|---:|---|---|
| WA530 `dji_sys` | 222 записи, ни одна не ведёт на `sys_event_*` | нет | статическая pointer-table этим методом не найдена |
| WA341 `dji_sys` | 259 записей, ни одна не ведёт на `sys_event_*` | нет | то же |
| WA530 `dji_wlm`, `dji_sdrs_agent`, `dji_network` | детектор `{code, req, ack}` не находит таблиц | — | то же |

Ни `sys_event_dev_ping`, ни `sys_event_dev_ver_get` не встречаются в этих
образах ни как 8-байтные слова в данных, ни как `r_addend` релокаций. Это
доказывает только отсутствие подходящей статически инициализированной
pointer-table; runtime-регистрация вероятна, но её конкретный механизм должен
подтверждаться разбором startup-кода.

Чем прошивки дрона всё же полезны: они дают **словарь имён**. В WA530
`dji_sys` 69 функций семейства `sys_event_*`, среди них говорящие
`sys_event_dev_ping`, `sys_event_dev_ver_get`,
`sys_event_country_code_changed`, `sys_event_check_connection`,
`sys_event_cb_ext_fc_get_type`/`set_type`. Это материал для семантики
конкретных команд, но не для нумерации: имя без слота якорем быть не может.

### Почему шесть таблиц `dji_link` остаются нерасшифрованными

Для них пробовался автоматический подбор `cmd_set`: для каждого известного
набора имён из wireshark-диссектора вычислялись `cmd_id` при базе, равной
адресу таблицы, и считались совпадения ключевых слов между именем функции
прошивки и публичным именем команды. Результат — 0–2 совпадения на таблицу,
причём совпадения оказывались артефактами (один и тот же
`dji_event_set_wifi_mac_addr` «подходил» к разным базам на позициях `0B`/`0C`).
Для сравнения, у доказанной таблицы `0x37848` тот же критерий даёт три
совпадения, включая `07:30` Set Country Code.

Двух совпадений мало: именно так возникла ошибочная первая редакция таблицы
`0x51`. Поэтому эти таблицы оставлены неопознанными до появления живого
capture или независимого источника имён.

## Таблица `0x51` в `dji_wlm` RM510 и сравнение с RC Pro 2 (2026-07-24)

Тем же способом восстановлена таблица command set `0x51` на стороне пульта
RM510. Раскладка совпадает с RC Pro 2: массив по 24 байта, поле `+0` — request
handler, `+8` — ack, всё через релокации.

База — `0x73730`, закреплена на `01` → `wlm_process_forward_pkt`. Проверка
получилась сильнее, чем один якорь: **13 позиций совпали с таблицей RC Pro 2**
(`01`, `02`, `03`, `05`, `06`, `07`, `08`, `0A`, `0F`, `10`, `15`, `18`, `20`),
что при неверной базе было бы невозможно.

| ID | Request handler | Ack handler |
|---:|---|---|
| `01` | `wlm_process_forward_pkt` | — |
| `02` | `wlm_link_mode_sw_trigger` | — |
| `03` | `wlm_link_status_report` | — |
| `05` | — | `wlm_route_switch_ack` |
| `06` | `wlm_link_sw_res_sync` | `wlm_link_sw_res_ack` |
| `07` | — | `wlm_link_ctrl_ack` |
| `08` | `wlm_link_sw_nego_res_proc` | `wlm_link_sw_nego_ack` |
| `09` | `wlm_link_sw_test` | `wlm_event_foo` |
| `0A` | `wlm_link_mode_query` | — |
| `0F` | `wlm_route_switch_req` | — |
| `10` | `wlm_et_get_video_unsmoothy_level` | — |
| `15` | `wlm_select_target_dev` | — |
| `16` | `wlm_request_download_lm` | `wlm_respone_download_lm` |
| `18` | `wlm_receive_video_status` | — |
| `20` | `wlm_receive_product_conn_sta` | — |
| `21` | `wlm_test_callback` | — |

### Чем RM510 отличается от RC Pro 2

RM510 регистрирует 16 слотов против 35 у RC Pro 2. Отсутствуют, в частности:

- `19` `wlm_modem_onoff_control`, `1A` `wlm_service_mode_switch_req`,
  `22` `wlm_bind_status_changed` — то есть **весь modem/LTE-блок**;
- `1B`/`1D` power control, `1E`/`1F` frequency info, `23` query status,
  `27` RTT, `29`/`2A` TLV и special link, `2C` bandwidth attach,
  `2E` netlink, `2F` general control, `34` neighbour info,
  `41`/`42` ability negotiation, `51` v3 forward.

Уникальны для RM510: `09` `wlm_link_sw_test` / `wlm_event_foo`,
`16` `wlm_request_download_lm` / `wlm_respone_download_lm`,
`21` `wlm_test_callback`.

Для FreeFCC это конкретный вывод про 4G-sweep: три ID, о которых известно, что
они делают что-то осмысленное на RC Pro 2 (`51:19` modem on/off, `51:1A`
service mode switch, `51:22` bind status), отсутствуют в восстановленной
статически инициализированной таблице RM510. Это согласуется с отрицательным
результатом live-прогона sweep, но не исключает другой runtime route или
динамическую регистрацию, поэтому утверждать глобальное отсутствие
обработчика нельзя.

## Таблицы `dji_link`, восстановленные из релокаций (2026-07-24)

`dji_link` держит обработчики в разреженных массивах по 24 байта на слот, где
индекс слота равен `cmd_id`. В образе они нулевые (PIE), поэтому восстановлены
из `.rela.dyn`: `r_offset` — адрес слота, `r_addend` — адрес handler'а.

Базы закреплены на независимо известных парах:

- `cmd_set 0x00` → база `0x36000`. Проверка: `00:01` Version Inquiry,
  `00:0B` Reboot Chip, `00:0E` Heartbeat, `00:32` Activate Config,
  `00:4A` Set Date/Time, `00:FF` Query Device Info — шесть совпадений с
  публичной таблицей `dji-firmware-tools` одновременно.
- `cmd_set 0x07` → база `0x37848`. Проверка: `07:0B`/`07:0C` WiFi Ap Mac Addr
  Set/Get и `07:30` WiFi Ap Set Country Code — три совпадения; расстояние
  между `07:19` и `07:30` равно ровно 23 слотам, что совпадает с разностью ID.

### cmd_set 0x00: база VA 0x36000, шаг 24
| Команда | Handler в RM510 | Публичное имя |
|---|---|---|
| `00:01` | `dji_event_common_get_device_version` | Version Inquiry |
| `00:0B` | `sys_event_reboot` | Reboot Chip |
| `00:0E` | `dji_event_heartbeat` | Heartbeat/Log Message |
| `00:32` | `dji_event_active_config` | Activate Config |
| `00:36` | `dji_event_deactive_config` | — |
| `00:4A` | `dji_event_set_date` | Set Date/Time |
| `00:5B` | `dji_event_ftpd_control` | — |
| `00:E5` | `dji_event_handle_djicare` | — |
| `00:EA` | `dji_event_handle_log_export` | — |
| `00:ED` | `bb_event_cb_log_sync` | — |
| `00:EE` | `bb_event_cb_log_info` | — |
| `00:FF` | `dji_event_query_device_info` | Query Device Info |

### cmd_set 0x07: база VA 0x37848, шаг 24
| Команда | Handler в RM510 | Публичное имя |
|---|---|---|
| `07:0B` | `dji_event_set_wifi_mac_addr` | WiFi Ap Mac Addr Set |
| `07:0C` | `dji_event_get_wifi_mac_addr` | WiFi Ap Mac Addr Get |
| `07:19` | `dji_event_get_country_code` | WiFi Ap 19 (безымянная) |
| `07:30` | `dji_event_set_coutry_code` | WiFi Ap Set Country Code |
| `07:3C` | `dji_event_set_bt_mac_addr` | — |
| `07:3D` | `dji_event_get_bt_mac_addr` | — |
| `07:5C` | `dji_event_mcu_bat_status_push` | — |
| `07:B5` | `dji_event_get_status` | — |
| `07:E0` | `dji_event_hdvt_status_push` | — |

### Уточнение 2026-07-25: границы таблиц и независимая проверка

В первой редакции таблицы `0x00` и `0x07` были обрезаны: я ограничил их
диапазоном `0x1000` байт вместо `256 × 24 = 0x1800`. Из-за этого четыре
handler'а не попали в списки и были ошибочно отнесены к «нерасшифрованным
таблицам». С корректной границей добавились `00:ED`, `00:EE`, `07:B5`, `07:E0`
(они уже включены в таблицы выше).

Базы независимо подтверждены на **другой прошивке**: в свежем
`V03.02.0700_rm510` (Android `V03.06.01.66`, 2023-12) те же якоря дают
`dji_event_get_country_code` по `0x34ce0` и `dji_event_set_coutry_code` по
`0x34f08` — разница `0x228`, ровно 23 слота, как и разность ID `0x30 − 0x19`.
Полученный набор `07:xx` совпадает с приведённым выше полностью.

Из ранее «потерянных» handler'ов вне таблиц `0x00`/`0x07` остаются только три:
`dji_event_write_keys`, `dji_event_rtk_data_enc`, `dji_event_report_status` —
их `r_offset` выходит за 256 слотов от обеих баз.

### Карта всех таблиц, регистрируемых `dji_link_event_start`

Функция `dji_link_event_start` (`0x14fd0`) собирает на стеке дескриптор и
передаёт его в `0x34e50`. В коде видно **восемь** адресов таблиц и четыре
счётчика:

| Адрес таблицы | Комментарий |
|---|---|
| `0x36000` | **cmd_set `0x00`**, доказана шестью публичными совпадениями |
| `0x37800` | не расшифрована |
| `0x37848` | **cmd_set `0x07`**, доказана тремя публичными совпадениями |
| `0x37ce0` | не расшифрована |
| `0x37e18` | не расшифрована |
| `0x390c0` | не расшифрована |
| `0x399d8` | не расшифрована |
| `0x3a8f0` | не расшифрована |

Счётчики слотов, которые кладутся рядом: `0x100` (256), `0x61` (97),
`0x0D` (13), `0x36` (54). Сопоставление «таблица ↔ счётчик» из этого кода
однозначно не выводится: указатели и счётчики пишутся в разные области
стекового дескриптора с нерегулярными смещениями.

Размер `0x100` согласуется с таблицей `0x00`: `0x36000 + 256 × 24 = 0x37800`,
то есть следующая таблица начинается ровно там, где заканчивается первая.

Остальные шесть таблиц содержат handler'ы `dji_event_get_status`,
`dji_event_hdvt_status_push`, `dji_event_write_keys`,
`dji_event_rtk_data_enc`, `dji_event_report_status` и другие, но их `cmd_set`
не установлен: для каждой нужна хотя бы одна независимо известная пара
`cmd_set:cmd_id`, чтобы закрепить базу. Без такого якоря нумерация была бы
догадкой — именно так возникла ошибка в первой редакции таблицы `0x51`
(см. [`RC_PRO2_DUML_COMMAND_REFERENCE.md`](RC_PRO2_DUML_COMMAND_REFERENCE.md)).

### Что это даёт FreeFCC напрямую

| Кадр приложения | Подтверждение из RM510 |
|---|---|
| `07:19` | `dji_event_get_country_code`. **Публичный диссектор знает эту команду только как безымянную «WiFi Ap 19»** — здесь её назначение восстановлено из кода пульта и совпадает с live-поведением (возврат alpha-2) |
| `07:30` | `dji_event_set_coutry_code` (опечатка — в самой прошивке). Совпадает с публичным «WiFi Ap Set Country Code» |
| `00:32` | `dji_event_active_config`, публично «Activate Config» — подтверждает трактовку кадра как activation-state query, а не FCC-write |
| `00:E5` | `dji_event_handle_djicare`. То есть у пульта обработчик **есть**; на WM260 тот же кадр отвергается DJI Care dispatcher'ом с ошибкой `0xE3`. Разница объясняется тем, что это разные получатели, а не тем, что команда несуществующая |

### Чего нет в публичных таблицах

`00:36` `dji_event_deactive_config`, `00:5B` `dji_event_ftpd_control`,
`00:EA` `dji_event_handle_log_export`, `07:3C`/`07:3D` set/get BT MAC,
`07:5C` `dji_event_mcu_bat_status_push`.

## Таблица parameter manager, восстановленная из релокаций (2026-07-24)

`dji_sdrs_agent` — это parameter-manager пульта (в нём же лежит
`compute_hash_value_by_name`, тот самый алгоритм PM-хэшей). Его таблица команд
в образе нулевая: бинарник PIE, и указатели проставляются динамическим
линкером. Поэтому таблица восстановлена из `.rela.dyn`.

Структура записи — 24 байта, код команды идёт **перед** парой указателей:

```c
struct pm_cmd_entry {      // 24 bytes, stride 24
    uint64_t code;          // (cmd_id << 16) | cmd_set
    uint64_t req_handler;   // R_AARCH64_RELATIVE relocation
    uint64_t ack_handler;   // R_AARCH64_RELATIVE relocation
};
```

`code` лежит в образе как обычная константа, поэтому нумерация здесь не
выводится косвенно, а читается напрямую — в отличие от таблицы `0x51` в
`dji_wlm`, где пришлось закреплять базу по известным контрактам.

| `03:F3` | `reset_cfg_item_value_func` | `public_ack_handler` |
| `03:F7` | `get_cfg_item_info_by_hash_func` | `public_ack_handler` |
| `03:F8` | `read_cfg_item_value_by_hash_func` | `public_ack_handler` |
| `03:F9` | `write_cfg_item_value_by_hash_func` | `public_ack_handler` |
| `03:FA` | `reset_cfg_item_value_by_hash_func` | `public_ack_handler` |
| `03:FB` | `recv_fixed_send_cfg_by_hash_func` | `public_ack_handler` |
| `03:FC` | `req_fixed_send_cfg_by_hash_func` | `public_ack_handler` |
| `03:E0` | `api_user_ask_table_func` | `public_ack_handler` |
| `03:E1` | `api_user_ask_param_by_index_func` | `public_ack_handler` |
| `03:E2` | `api_usr_get_param_by_index_func` | `public_ack_handler` |
| `03:E3` | `api_usr_set_param_by_index_func` | `public_ack_handler` |
| `03:E4` | `api_usr_def_param_by_index_func` | `public_ack_handler` |
| `01:40` | `get_cfg_item_info_by_hash_func` | `public_ack_handler` |
| `01:41` | `read_cfg_item_value_by_hash_func` | `public_ack_handler` |
| `01:42` | `write_cfg_item_value_by_hash_func` | `public_ack_handler` |
| `01:43` | `reset_cfg_item_value_by_hash_func` | `public_ack_handler` |

### Что это подтверждает

Одиннадцать позиций точно совпадают с публичной таблицей
`DJI_DUMLv1_CMD_SET_TEXT` / `flyc` из `dji-firmware-tools`:
`E0` Get Tbl Attribute, `E1` Get Item Attribute, `E2` Get Item Value,
`E3` Set Item Value, `E4` Reset Def. Item Value, `F7` Get Param Info By Hash,
`F8` Read Param By Hash, `F9` Write Param By Hash, `FA` Reset Params By Hash,
`FB` Read Params By Hash, `FC` Write Params By Hash.

Для FreeFCC это прямое подтверждение из кода самого пульта, а не только из
публичной таблицы: LED- и GPS-путь приложения (`03:F7` metadata probe,
`03:F8` read, `03:F9` write) адресован именно тем обработчикам, которые
заявлены в аудите.

### Что нового

- **`03:F3` → `reset_cfg_item_value_func`** — этой пары нет в публичной
  таблице `dji-firmware-tools`.
- **`01:40`–`01:43`** — тот же набор операций (`get info` / `read` / `write` /
  `reset` by hash) продублирован в command set `0x01` (Special) с теми же
  handler-функциями. То есть у PM-операций RM510 есть альтернативный вход,
  не совпадающий с привычным `0x03`.

Практических изменений в профилях это не требует: рабочий путь `03:F8`/`03:F9`
подтверждён. Дубль `01:4x` зафиксирован как факт таблицы, его поведение на
живом устройстве не проверялось.

## Cross-check с live-потоком RC2

### RM510 + Mavic 3T: identity и LED

Live-проверка от 2026-07-26 на RM510 с включённым DJI Mavic 3T:

- aircraft product code `WM265T` приходит на `40009` в push `00:82`,
  route `0xA2 → 0x82`;
- модель доступна при закрытом DJI Pilot 2, поэтому для заполнения Info
  достаточно короткого пассивного чтения DUML;
- полный factory serial виден отдельно в `51:14`; значение намеренно не
  приводится;
- пользователь подтвердил, что на этой же связке работают операции LED
  on/off. В данном проходе состояние LED повторно не переключалось, поэтому
  нового raw request/response evidence для LED не снималось.

Модельный payload `00:82` начинается с:

```text
57 4d 32 36 35 54 00 00 ...   # ASCII "WM265T"
```

Приложение проверено на живом устройстве: после ручного
`Refresh aircraft identity` вкладка Info показывает controller `rm510`,
aircraft `DJI Mavic 3T`, code `WM265T` и отдельно полученный S/N.

Ниже перечислены пары, реально наблюдавшиеся приложением. Это transport
evidence, а не доказательство наличия handler именно в трёх ELF выше.

| Команда | Наблюдение |
|---|---|
| `03:43` | GPS/flight telemetry, поля спутников и GPS state |
| `03:44` | Home Point telemetry; на проверенном layout `home_state` at offset 20 |
| `06:77` | Один passive RC status frame, payload `00`; точная семантика неизвестна |
| `06:A4` | RC telemetry/status, payload `00` |
| `06:AE` | Основной background RC payload на `40009`; семантика неизвестна |
| `00:81`, `00:82` | Controller identity telemetry |
| `07:18`, `07:30` | Country/area write family |
| `07:19` | Country-code query family |
| `51:04` | Cellular/device OSD telemetry; совпадает со статическим `wlm_push_dev_osd` |
| `51:14` | Neighbour/device-link list `2 + 49 × N`; live length 51 означает одного peer, а identity region его записи содержит полный aircraft serial |

Полная частотная карта live capture сохранена в
[`DUML_STREAM_MAP.md`](DUML_STREAM_MAP.md).

## Граница полноты

Таблица включает все фиксированные пары, которые в текущем проходе удалось
привязать к именованным функциям `dji_wlm`, `dji_link` и `dji_sdrs_agent`.
Она заведомо не включает:

- ID, передаваемые в generic forward/send wrappers только во время выполнения;
- команды remote MCU, aircraft, camera и flight controller, которых нет в
  userspace ELF пульта;
- handler tables, числовые поля которых ещё требуют data-flow reconstruction;
- зашифрованные/упакованные компоненты, не входившие в сохранённый corpus.

При следующем расширении справочника надо добавлять не просто найденный
hex-number, а минимум: файл и Build ID, функцию, call site/registration site,
направление, payload evidence и уровень уверенности.
