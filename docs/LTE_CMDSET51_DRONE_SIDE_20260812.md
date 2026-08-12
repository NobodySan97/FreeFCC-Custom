# Таблицы обработчиков DUML cmdset 0x51 в dji_lte дронов (air side) — сравнение с RC Pro 2

Дата: 2026-08-12. Продолжение `.scratch/reverify_20260811/lte_4g_path_trace.md` (RC Pro 2 v576 /
WA530) — теперь три **дроновых** бинаря `dji_lte` из eMMC-дампов.

Инструменты: pure-python анализ ELF (`.gnu_debugdata` → xz → mini-symtab; ANDROID_RELA через
`llvm-readelf -r`; адресация = ELF vaddr), `aarch64-linux-gnu-objdump`. Скрипты и сырые дампы:
`FreeFCC/.scratch/drone51_20260812/` (`extract_syms.py`, `find_tables.py`, `dump_table.py`,
`scan_packed.py`, `find_refs.py`; выводы `table_*_{uav,gnd1,gnd2}.txt`, `packed51_*.txt`,
`sites_*.txt`, `dis_*.txt`).

## 1. Верификация артефактов

| платформа | путь | SHA-256 | Build ID | статус |
|---|---|---|---|---|
| Air 3 (e3t_wa233), FW 10.08.09.64 | `.scratch/emmc/air3_1/root/bin/dji_lte` | `4c95f8956bc09b95…b448b2cb` | `926a6b04d68e87e617032ae0bdec0f33` | exact ✓ |
| Mavic 3 (eagle2_wm260), FW 10.00.81.36 | `.scratch/emmc/EMMC_Data_M3_2/root/bin/dji_lte` | `0d53d2d05085a614…903e9a1fb` | `75801eed87cf8b292cb7b61e64f189bb` | exact ✓ |
| eagle3_wa341, FW 10.00.08.02 | `.scratch/emmc/EMMC_Data_5_64gb/root/system_bin/dji_lte` | `2034374a468dcfc6…c7ffff565` | `4d7abbe5ad0cd95e01066b3e59969e40` | exact ✓ |
| RC Pro 2 v576 (референс, перепроверен) | `FreeFCC/.scratch/rcpro2_4g_ota/dji_lte_v576` | `6788276b77…d70e6ec` | `2ae6c041582be14d56507a2b70910792` | ✓ (по trace) |
| WA530 / RC 2 (референс, перепроверен) | `FreeFCC/.scratch/wa530_0300/e4/root/system/bin/dji_lte` | `deecd266cdff0a11…b0a2cef` | `d3568587bc0aea999958bd64db633f21` | exact ✓ |

Размеры дроновых бинарей: air3 = 1 323 448; M3 = **570 808** (заметно урезан); eagle3 = 1 910 136.

## 2. Метод и доказательства корректности таблиц

Формат таблицы тот же, что у dji_wlm / RC Pro 2 dji_lte: записи по 24 байта
`{ req_handler, ack_handler, aux }`, индекс слота = cmd ID, таблица лежит в `.data`, указатели
восстановлены по релокациям R_AARCH64_RELATIVE.

Таблица регистрируется через `duss_event_create_client_more_config` из ролевых функций
`lte_event_uav_start` / `gnd_gls_lte_event_start` / `gnd_rc_lte_event_start`; в конфиге на стеке
каждой таблице соответствует счётчик слотов (пары «ptr@sp+1768 ↔ count@sp+488» и т.д. восстановлены
по дизассемблеру; во всех пяти бинарях смещения полей совпадают). Динамической регистрации нет:
**ни один из трёх дроновых dji_lte не импортирует `duss_event_modify_dynamic_command`**
(dynsym проверен) — статические таблицы полны.

Независимые перекрёстные проверки (все сошлись):

1. Слоты 0x30/0x33 заняты функциями с именами `wlm_dev_mid_allocate_5130` /
   `wlm_dev_route_info_sync_5133` — индекс слота = cmd id, cmdset = 0x51.
2. Слот 0x07 = `lte_link_ctrl_proc_uav/gnd` — совпадает с доказанным 51:07 из trace WA530/v576.
3. **Последний заполненный слот каждой таблицы == count−1** (air3: 0x33 при count 52; M3: 0x11/0x0d/0x1e
   при 18/14/31; eagle3 и v576: 0x51 при 82).
4. **Конец каждой 0x51-таблицы (base + count·24) в точности равен базе следующей
   зарегистрированной таблицы** — проверено для всех 9 дроновых таблиц.
5. Cmdset-0x09 таблица: count 0x86 (air3/eagle3, есть слот 0x85 `get_peer_sdr_info_0985`) vs
   0x85 (M3, слота 0x85 нет) — совпадает с известным count 0x86 у WA530.
6. Cmdset-0x18 таблица: `sdr_pair_done_handler` попадает в 18:31 и `lte_set_esim_req_handle`
   в 18:4B — как в trace.

Следствие: записи, лежащие за границей count (например `common_dongle_activate`,
`lte_hms_set_subscriber`, `common_upgrade_set_action`), принадлежат **соседним таблицам других
cmdset** и в отчёт по 0x51 не входят (в прежних быстрых дампах они давали фантомные «51:57/51:66»
и т.п.).

## 3. Таблицы cmdset 0x51 по платформам

### 3.1 Air 3 (air3_1) — count 52 (id 0x00..0x33), все три роли

uav @ `0x13fe38` (регистрация `lte_event_uav_start` @ `0x69350`, call @ `0x694ec`):

| id | handler | addr |
|---|---|---|
| 51:01 | `lte_wl_manage_forward_pkt` | 0x14418 |
| 51:07 | `lte_link_ctrl_proc_uav` | 0x690d4 |
| 51:11 | `lte_wl_manage_forward_multi_pkt` | 0x141ac |
| 51:22 | `lte_get_sdr_pair_state` | 0x706d4 |
| 51:30 | `wlm_dev_mid_allocate_5130` | 0x721e8 |
| 51:33 | `wlm_dev_route_info_sync_5133` | 0x72868 |

gls @ `0x143960` (call @ `0x6adf0`): 01 fwd, 07 `lte_link_ctrl_proc_gnd` (0x6a8e4),
0x0d `lte_debug_rpt_ctrl` (0x6a190), 0x30, 0x33.
rc @ `0x146cc0` (call @ `0x6b0a4`): 01, 07 gnd, 0x0d, 0x10 `lte_gnd_data_capture` (0x6a608),
0x1e `lte_gnd_get_wifi_status` (0x6a0c8), 0x22 `lte_get_sdr_pair_state`, 0x30, 0x33.

### 3.2 Mavic 3 (EMMC_Data_M3_2) — минимальный набор

uav @ `0x91268`, **count 18**: 01 `lte_wl_manage_forward_pkt` (0xb958), 07
`lte_link_ctrl_proc_uav` (0x29e48), 0x11 `lte_wl_manage_forward_multi_pkt` (0xb710). Всё.
gls @ `0x94970`, **count 14**: 01, 07 `lte_link_ctrl_proc_gnd` (0x2b800), 0x0d
`lte_debug_rpt_ctrl` (0x2ada0).
rc @ `0x97880`, **count 31**: 01, 07 gnd, 0x0d, 0x10 `lte_gnd_data_capture` (0x2b520),
0x1e `lte_gnd_get_wifi_status` (0x2ad70).
(Регистрация: calls @ `0x2a250` / `0x2bbec` / `0x2be94`.)

### 3.3 eagle3_wa341 (EMMC_Data_5_64gb) — count 82 (0x00..0x51), все три роли

uav @ `0x1c51c8` (регистрация `lte_event_uav_start` @ `0x104980`, call @ `0x104b40`):

| id | req | ack |
|---|---|---|
| 51:01 | `lte_wl_manage_forward_pkt` 0x708a0 | — |
| 51:07 | `lte_link_ctrl_proc_uav` 0x1046b0 | — |
| 51:11 | `lte_wl_manage_forward_multi_pkt` 0x705f0 | — |
| 51:1D | `lte_power_ctrl_set_handle` 0x112ad0 | — |
| 51:22 | `lte_get_sdr_pair_state` 0x10f990 | — |
| 51:30 | `wlm_dev_mid_allocate_5130` 0x111d80 | — |
| 51:33 | `wlm_dev_route_info_sync_5133` 0x1128f0 | — |
| 51:41 | `lte_wl_manager_ability_nego_req` 0x1410d0 | `lte_wl_manager_ability_nego_ack` 0x1443e0 |
| 51:42 | `lte_wlm_ability_nego_result_req` 0x144a30 | `lte_wlm_ability_nego_result_ack` 0x145410 |
| 51:51 | `lte_wl_manage_forward_pkt_v3` 0x70c90 | — |

gls @ `0x1c8fc8` (call @ `0x106a2c`): 01, 07 `lte_link_ctrl_proc_gnd` (0x106290), 0x0d
`lte_debug_rpt_ctrl` (0x105a70), 0x30, 0x33, 0x41, 0x42, 0x51.
rc @ `0x1cc610` (call @ `0x106d80`): 01, 07 gnd, 0x0d, 0x10 `lte_gnd_data_capture` (0x105f70),
0x1e `lte_gnd_get_wifi_status` (0x105960), 0x22, 0x30, 0x33, 0x41, 0x42, 0x51.

### 3.4 Референсы (перепроверены тем же методом)

RC Pro 2 v576 dji_lte — **слот-в-слот идентичен eagle3_wa341**: uav @ `0x1865f0`
(01, 07, 0x11, 0x1d `lte_power_ctrl_set_handle` 0xdb0a4, 0x22, 0x30, 0x33, 0x41, 0x42, 0x51),
gls @ `0x18a3f0`, rc @ `0x18daf8` — те же наборы, что у eagle3 gls/rc.
WA530 (RC 2) uav @ `0x244238`, count 82: дополнительно **51:1F `lte_rm_ctrl_set_handle`**
(0x14cdd0) и **51:37 `local_dev_info_sync_req/ack`** (0x14d5d0/0x14db10); остальное как v576.

**Исправления к lte_4g_path_trace.md:** (a) «51:37» в таблице v576 — не подтверждается:
слот 0x37 у v576 пуст, 51:37 есть только у WA530; (b) «51:53/51:59» у WA530 — фантомы за
границей count=82 (слоты 1 и 7 следующей таблицы); (c) «51:57 `lte_hms_set_subscriber`» — тоже
за границей count: во всех пяти бинарях это слот 5 соседней 6-слотовой таблицы другого cmdset.

## 4. Исходящие кадры 0x51, которые генерирует дроновой dji_lte

Скан `.text` на конструкции packed-слова `(0x51<<16)|id` (movz/movk; лiteral-pool LE32
`0x0051xxxx` в файле нет вообще — проверено побайтно):

| id | air3 | M3 | eagle3 | функция (сайт) |
|---|---|---|---|---|
| 51:01 | ✓ | ✓ | ✓ | `wlm_mb_pre_process_cb`, `lte_wl_manage_forward_multi_pkt` (forward) |
| 51:03 | ✓ | ✓ | ✓ | `push_link_report_info` (air3 0x76b64 / M3 0x30b48 / e3 0x116f1c) — **пуш link-report состояния** |
| 51:13 | ✓ | ✓ | ✓ | `wlm_dump_event_cb_msg`, `wlm_dump_head_msg` (дамп/дебаг-пуши) |
| 51:1B | — | — | ✓ | `push_power_ctrl_report_to_wlm` (0xfbe48) — пуш power-ctrl репорта |
| 51:41 | — | — | ✓ | `lte_update_dynamic_ability` 0x13d444, `lte_send_global_nego_abilty` 0x140fd0, `lte_send_push_msg_to_wlm` 0x149890 |
| 51:42 | — | — | ✓ | `lte_query_wlm_nego_result` 0x13d5f4 (опрос результата nego у wlm) |

Никаких других 0x51-слов дроновые бинари не строят. 51:1A (service_mode_switch) в dji_lte
**отсутствует везде** — это таблица dji_wlm; dji_lte лишь отвечает на 51:07
(`lte_link_ctrl_proc_uav/gnd`, проверка eligibility, 1-байтовый ACK — семантика из trace).

## 5. 09:85 и инициация pairing на дроне

- **air3**: cmdset-0x09 uav-таблица @ `0x13f1a8` (count 0x86): слот 0x84 = `get_peer_sdr_info`
  (0x6c26c), слот **0x85 = `get_peer_sdr_info_0985`** (0x6c7c4); то же в rc-таблице
  (сайт 0x1462b8). Полный pair-механизм есть: `lte_pair_init` 0x8f174, `LtePairBySdr_*`,
  `pairing_do_pair_task` 0x92e98 и т.д. Лог-строки `get_peer_sdr_info_0985 … do_pair ret:%d`,
  `invalid sender peer_host`, `sdr pair state do not act` — та же валидация, что у v576/WA530.
- **eagle3**: cmdset-0x09 таблица @ `0x1c4538` (count 0x86): слот 0x85 = `get_peer_sdr_info_0985`
  (0x109450, uav-сайт 0x1c51b0; rc-сайт 0x1cbbf0). Плюс колбэки `lte_paired_global_nego_cb`
  0x13cc90 → пуш 51:41 в wlm (как §3 trace). Есть `get_peer_service_info`/`_ack`
  (18:3C в cmdset-0x18 таблице @ `0x1c3dd0`), 18:01 `lte_ability_negotiate_req`.
- **M3**: cmdset-0x09 таблица @ `0x905f0` имеет **count 0x85 = 133** — последний слот 0x84 =
  `get_peer_sdr_info` (0x2cef8); **слота 0x85 нет**, символа/строк `*_0985` в бинаре нет,
  pair-машинерии (`lte_pair_init`, `LtePairBySdr*`) нет вообще. 4G-pairing в этом билде Mavic 3
  отсутствует как код.
- **Отправителя 09:85 в проверенных dji_lte нет ни на одном дроне**: packed-скан cmdset 0x09 находит только
  09:A0 (`get_ssfn`/`gnd_get_ssfn`) — как и на ground-стороне в trace. Кадр 09:85 приходит извне
  (SDR-стек / dji_link) — дрон его **принимает**, а не генерирует.
- Семейства by-server pairing (18:52 `lte_pair_method_req` / 18:53 / 18:54 / 18:56) **нет ни на
  одном дроне**: cmdset-0x18 таблицы air3 (base `0x13ea40`, count 79) и eagle3 (base `0x1c3dd0`,
  count 79) заканчиваются на 18:4E; символов `*byserver*`/`lte_pair_method*` нет. Это уровень
  v576 (там тоже нет), а не WA530.
- Чтение статуса 4G — cmdset 0x18, не 0x51: 18:37 `get_peer_lte_state_info`,
  18:38 `get_peer_lte_link_info`, 18:14 `lte_event_cb_lte_get_state` (eagle3), плюс
  51:22 `lte_get_sdr_pair_state` (air3/eagle3) и исходящий пуш 51:03 `push_link_report_info`.

## 6. Сводное сравнение (uav-роль — та, что реально живёт на дроне)

| id | RC Pro 2 v576 | WA530 (RC2) | eagle3 (дрон) | air3 (дрон) | M3 (дрон) |
|---|---|---|---|---|---|
| 51:01 fwd | ✓ | ✓ | ✓ | ✓ | ✓ |
| 51:07 link_ctrl | ✓ | ✓ | ✓ | ✓ | ✓ |
| 51:11 fwd_multi | ✓ | ✓ | ✓ | ✓ | ✓ |
| 51:1D power_ctrl | ✓ | ✓ | ✓ | — | — |
| 51:1F rm_ctrl | — | ✓ | — | — | — |
| 51:22 sdr_pair_state | ✓ | ✓ | ✓ | ✓ | — |
| 51:30 mid_allocate | ✓ | ✓ | ✓ | ✓ | — |
| 51:33 route_info_sync | ✓ | ✓ | ✓ | ✓ | — |
| 51:37 local_dev_info_sync | — | ✓ | — | — | — |
| 51:41/42 ability nego | ✓ | ✓ | ✓ | — | — |
| 51:51 fwd_v3 | ✓ | ✓ | ✓ | — | — |

**Уникальных «воздушных» команд 0x51 нет**: дроновые таблицы — подмножества наземных.
eagle3 (дрон) ≡ RC Pro 2 v576 слот-в-слот — одно поколение кода; air3 — то же поколение без
ability-nego/power/forward-v3; M3 — старый минимальный билд (только forward + 51:07 + debug).
Различие uav/gls/rc-таблиц внутри одного бинаря: uav/rc имеют 51:22 (sdr pair state), rc —
ground-опросы 0x10/0x1e, gls — урезана; 51:07 различается `*_uav` vs `*_gnd` (роль).

## 7. Выводы для FreeFCC

1. Включение 4G на дроне по-прежнему идёт путём app→**51:1A в dji_wlm** → wlm FSM → **51:07 в
   dji_lte дрона**; dji_lte дрона сам режим не переключает (handler 51:07 только отвечает
   allow/reject).
2. В проверенных `dji_lte` нет клиентского API/генератора, который сам инициирует 09:85:
   air3/eagle3 принимают этот кадр от внешнего SDR/dji_link-стека. Поэтому из
   данного статического корпуса нельзя заключить, что внешний DUML-инициатор
   невозможен; доказано только отсутствие отправителя 09:85 внутри `dji_lte`.
   В M3-бинаре отсутствует исследованная pair-машинерия и слот 09:85, но это не
   исключает другой компонент или более новый firmware build.
3. Наблюдаемость состояния 4G со стороны дрона: пуши **51:03** (`push_link_report_info`) и
   51:13; опросы 18:37/18:38; у eagle3 также 51:1B/51:41/51:42 (ability nego с wlm).
4. Минимальный **статический кандидат** для эмуляции air-side 0x51 — ответ на
   51:07; 51:30/33 и, для eagle3, 51:41/42 также входят в зарегистрированные
   таблицы. Утверждать, что одного 51:07 достаточно для рабочего FSM, можно
   только после динамической трассировки: текущий анализ не воспроизводил
   pairing/negotiation целиком.

## 8. Hardware feedback 2026-08-12: Mavic 4 Pro + Dongle 2 + RC2

- В [upstream issue #35](https://github.com/doesthings/FreeFCC/issues/35#issuecomment-5263590483)
  тестировщик сообщил: автоматическое чтение serial не сработало, но после
  ручного ввода serial команда включила 4G, а DJI Fly показал состояние **ON**.
  По уточнению владельца этого checkout, пульт — RC2.
- Это тест upstream FreeFCC `v1.5.5`, commit
  `597157bd52120dfeb9677f79a8ad46b6027ce8dc`. Тот build отправляет 128 кадров
  cmdset 0x51 (ids 00..7F), каждый с payload `00 00 01 || ASCII serial`.
  Следовательно, положительный результат подтверждает **batch как целое**;
  он совместим с восстановленной семантикой 51:1A, но не изолирует этот id как
  единственную причину успеха.
- Уровень evidence: **OBSERVED** — DJI Fly показывает ON после batch;
  **не проверено** — полёт, LTE traffic/throughput, failover и команда OFF.
  Текущий FreeFCC в этом checkout отправляет уже один targeted 51:1A и ждёт
  response; перенос результата v1.5.5 на этот сокращённый путь требует отдельного
  live retest.
