# Command set `0x51`: кто его принимает и что делает 4G-sweep FreeFCC

Дата разбора: 2026-07-25. Цель — закрыть вопрос «что именно шлётся при нажатии
*Send 4G Activation Frames* и что с этим происходит на приёмной стороне».

Разбор статический, по ELF-образам; ни один кадр в рамках этой работы на
устройство не отправлялся.

> **Правка первой редакции.** Первая версия этого файла утверждала, что в
> наборе `0x51` у `dji_wlm` заполнено всего шесть слотов. Это было неверно: там
> были найдены только **дополнительные** таблицы, регистрируемые через
> `duss_event_register_dynamic_command_table`. Основная таблица набора
> передаётся раньше — в `duss_event_create_client_more_config` — и содержит
> 82 слота (`0x00…0x51`), из которых заполнено 33 на RC2 и 35 на RC Pro 2.
> Соответственно прежняя запись аудита («таблица имеет только ID `00..51`»,
> трактовки `51:19`, `51:1A`, `51:22`) была верной, а её «исправление» —
> ошибочным. Ниже — полный разбор.

## 1. Что отправляет приложение

Профиль `app/src/main/assets/profiles/4g.json` + `Profiles.load4g()`.
128 кадров, `cmd_id` = `0x00…0x7F`, всё остальное во всех кадрах одинаково:

| Поле | Значение |
|---|---|
| `cmd_set` | `0x51` |
| src | `0x02` — type 2 (**MOBILE_APP**), index 0 |
| dst | `0xEE` — type `0x0E` (OFDM_GROUND), index 7 |
| `cmd_type` | `0x00` — Request, **NO_ACK_NEEDED**, без шифрования |
| payload | `00 00 00` + ASCII-серийник борта |
| транспорт | abstract Unix socket `/duss/mb/0x205` |
| темп | 1 раунд, 10 мс между кадрами, ответ не читается |

Байт в байт первый кадр при серийнике `1581F5BMD23A700A1V2K` (seq здесь
`0x1234`, в реальном отправителе он случайный):

```text
55 24 04 40 02 ee 34 12 00 51 00 00 00 00 31 35 38 31 46 35 42 4d 44 32 33
41 37 30 30 41 31 56 32 4b e3 98
│  │  │  │  │  │  │     │  │  │  └─ payload: 00 00 00 + "1581F5BMD23A700A1V2K"
│  │  │  │  │  │  │     │  │  └──── cmd_id  0x00 (в кадре i = i)
│  │  │  │  │  │  │     │  └─────── cmd_set 0x51
│  │  │  │  │  │  │     └────────── cmd_type 0x00 (NO_ACK)
│  │  │  │  │  │  └──────────────── seq (LE)
│  │  │  │  │  └─────────────────── dst 0xEE
│  │  │  │  └────────────────────── src 0x02
│  │  │  └───────────────────────── CRC-8 заголовка (init 0x77)
│  │  └──────────────────────────── version/len hi
│  └─────────────────────────────── len = 36
└────────────────────────────────── magic 0x55
```

Длина кадра = 16 + длина серийника (13 байт заголовка/CRC + 3 нулевых байта payload); при 20-символьном S/N это 36 байт,
весь sweep — 4608 байт за ~1.3 с.

Адресация: `host_id = (type << 8) | index`, DUML-байт = `(index << 5) | type`.
Отсюда `0xEE` → type `0x0E`, index 7 → host `0x0e07` = **`dji_wlm`**
(`dji_lte` — `0x0e06`). Сокет `/duss/mb/0x205` — это mbus-эндпоинт хоста
`0x0205` = type 2 (MOBILE_APP), index 5, то есть точка входа для приложения;
дальше кадр маршрутизируется DUSS по полю `dst`.

Во всех профилях `sender = 2` теперь описан одинаково как `MOBILE_APP`.
Прежняя подпись «CAMERA» в `4g.json`, `led_on.json` и `led_off.json` была
ошибкой только в метаданных; байты кадров от неё не зависели.

## 2. Как `dji_wlm` регистрирует наборы команд

Контракт восстановлен из `libduml_frwk.so` (`0x1a6e00` в RC2-образе):

```c
register_table(handle, void **tables, uint32_t *counts, uint16_t n_sets)
  for (set = 0; set < n_sets; set++)
      if (counts[set] && tables[set])
          for (id = 0; id < counts[set]; id++) {
              rec = (char*)tables[set] + id*0x18;      // stride = 24 байта
              if (rec.req || rec.ack || rec.flags)      // пустой слот пропускается
                  register_dynamic_command(handle, set, id, rec);
          }
```

Запись — 24 байта: `{void *req_handler; void *ack_handler; uint32 flags}`.
Индекс внешнего массива — **cmd_set**, индекс записи — **cmd_id**.

`dji_wlm` использует этот контракт **трижды**, и это главная развилка разбора:

1. **Основная регистрация** — `duss_event_create_client_more_config`
   (`wlm_et_cb_start`, RC2 `0xf65e0`): клиент `wl_manager_service` создаётся
   сразу с парой массивов `x2 = tables`, `x3 = counts`, `w4 = 0xEF`. Здесь
   регистрируются **все** наборы, которые слушает WLM, включая полный `0x51`.
2. **Дополнительные регистрации** — два вызова
   `duss_event_register_dynamic_command_table` (`0xf66b0`, `0xf6750`) со
   второй парой массивов; в них заполнен только слот `0x51`, и подставляется
   одна из трёх таблиц device-manager sync (выбор по `wlm_get_dev_mgr_cfg()`).
3. **Точечная правка** — `duss_event_modify_dynamic_command` (`0xf67f8`) для
   `51:05`; ставит тот же `wlm_route_switch_ack`, новых ID не добавляет.

Есть и четвёртый путь — `duss_event_register_dynamic_command_with_user_data`
из `wlm_dmb_fsm_state_set`, но это test-tool
(`modules/test_tool/wlm_test_detect_msg_bw.c`): `cmd_set`/`cmd_id` берутся из
runtime-структуры теста и регистрируются только на время прогона.

## 3. Основная таблица `0x51` — 82 слота

Таблицы лежат в `.data` и заполняются `R_AARCH64_RELATIVE`-релокациями.
В RC2 и WA530 `.rela.dyn` упакована как Android `APS2`, поэтому «в файле» там
нули — именно на этом легко ошибиться. В RC Pro 2 v576 — обычная `RELA`.

| Устройство | Таблица `0x51` | Слотов | Непустых записей | С request-handler |
|---|---|---:|---:|---:|
| RC2 (`a3s`) | `0x17ff20` | 82 (`0x00…0x51`) | 33 | 28 |
| RC Pro 2 v576 | `0x206e80` | 82 (`0x00…0x51`) | 35 | 30 |

| Кадр | req handler | ack handler |
|---|---|---|
| `51:01` | `wlm_process_forward_pkt` | — |
| `51:02` | `wlm_link_mode_sw_trigger` | — |
| `51:03` | `wlm_link_status_report` | — |
| `51:05` | — | `wlm_route_switch_ack` |
| `51:06` | `wlm_link_sw_res_sync` | `wlm_link_sw_res_ack` |
| `51:07` | — | `wlm_link_ctrl_ack` |
| `51:08` | `wlm_link_sw_nego_res_proc` | `wlm_link_sw_nego_ack` |
| `51:09` | `wlm_link_switch_test` | `wlm_service_test_ack` |
| `51:0A` | `wlm_link_mode_query` | — |
| `51:0D` | `wlm_receive_debug_control` | — |
| `51:0F` | `wlm_route_switch_req` | — |
| `51:10` | `wlm_et_get_video_unsmoothy_level` | — |
| `51:15` | `wlm_select_target_dev` | — |
| `51:18` | `wlm_receive_video_status` | — |
| **`51:19`** | **`wlm_modem_onoff_control`** | — |
| `51:1A` | `wlm_service_mode_switch_req` | — |
| `51:1B` | `wlm_power_ctrl_agt_rpt` | — |
| `51:1D` | — | `wlm_power_ctrl_set_agent_ack` |
| `51:1E` | `wlm_rm_recv_local_freq_info` | — |
| `51:1F` | — | `wlm_rm_recv_local_freq_info_ack` |
| `51:20` | `wlm_receive_product_conn_sta` | — |
| `51:21` | `wlm_test_callback` | `wlm_test_callback_ack` |
| `51:22` | `wlm_bind_status_changed` | — |
| `51:23` | `wlm_query_status` | — |
| `51:24` | — | `wlm_agent_test_ack` |
| `51:27` | `wlm_rtt_stat_anls` | — |
| `51:29` | `wlm_et_cb_tlv_process_agent_report` | — |
| `51:2A` | `wlm_et_cb_common_entry_special_link_rpt` | — |
| `51:2C` | `wlm_agt_mgr_bw_attach` | — |
| `51:2E` | `wlm_netlink_service_req` (только RC Pro 2) | `wlm_netlink_service_rsp` |
| `51:2F` | `wlm_agt_mgr_general_control_req` (только RC Pro 2) | — |
| `51:34` | `wlm_dev_mid_neigh_info_req` | — |
| `51:41` | `wlm_ability_nego_req` | `wlm_ability_nego_ack` |
| `51:42` | `wlm_ability_nego_result_req` | `wlm_ability_nego_result_ack` |
| `51:51` | `wlm_et_cb_process_v3_forward` | — |

`51:42` замыкает круг с LTE-справочником: `dji_lte` формирует именно этот
`msg_id` в `lte_query_wlm_nego_result` (`0x00510042`, целевой host берётся из
runtime-структуры), а принимает его `wlm_ability_nego_result_req`.

## 4. Дополнительные таблицы: device-manager sync

Поверх основной регистрируются ещё три таблицы, из которых активна первая
плюс одна из двух остальных (выбор по `wlm_get_dev_mgr_cfg()`):

| Устройство | Вариант 1 | Вариант 2 | Вариант 3 |
|---|---|---|---|
| RC2 (`a3s`) | `0x184a68`, 52 слота | `0x184f48`, 55 | `0x185470`, 54 |
| RC Pro 2 v576 | `0x20b8c0`, 52 | `0x20bda0`, 55 | `0x20c2c8`, 54 |
| WA530 (дрон) | `0x226570`, 52 | `0x226a50`, 55 | `0x226f78`, 54 |

| Кадр | req / ack | Вариант |
|---|---|---|
| `51:30` | `wlm_dev_mid_sync_req` / `_ack` | 2 |
| `51:31` | `wlm_dev_list_sync_req` / `_ack` | 1 |
| `51:32` | `wlm_dev_state_sync_req` / `_ack` | 1 |
| `51:33` | `wlm_dev_route_sync_req` / `_ack` | 1 |
| `51:35` | `wlm_dev_mid_config_req` / `_ack` | 3 |
| `51:36` | `wlm_dev_mid_change_req` / `_ack` | 2 |

Итого на RC2 одновременно зарегистрированы **38** непустых ID набора `0x51`
(33 основных + `30/31/32/33/36`) либо **37** (33 +
`31/32/33/35`). Для request-пакета важен не этот общий счётчик, а наличие
`req_handler`: у пяти основных записей (`05`, `07`, `1D`, `1F`, `24`) есть
только ACK-handler. Поэтому request sweep может войти лишь в **33** либо
**32** обработчика RC2. На RC Pro 2 соответствующие числа — **40/39**
зарегистрированных ID и **35/34** request-handler.

## 5. Другие наборы, которые слушает `dji_wlm` (RC2)

| cmd_set | Слотов | Заполнено | Команды |
|---|---:|---:|---|
| `0x00` | 256 | 3 | `44` power mgr, `B0` sysmode scene, `FF` `wlm_query_device_info` |
| `0x01` | 2 | 1 | `01` `wlm_recv_i_frame_update_shmem` |
| `0x03` | 69 | 2 | `43`/`44` FC push OSD / OSD home |
| `0x06` | 141 | 1 | `8C` `wlm_rm_recv_app_work_stage` |
| `0x08` | 103 | 1 | `66` `wlm_recv_liveview_status` |
| `0x09` | 237 | 9 | `39`, `44`, `62`, `75`, `84`, `85`, `93`, `A0`, `EC` — SDR/HDVT |
| `0x18` | 72 | 3 | `37` `wlm_get_lte_peer_state_info`, `3B` `wlm_et_get_lte_rpt_track`, `47` `wlm_recv_i_frame_for_wifi` |
| `0x19` | 68 | 1 | `43` `wlm_recv_rmc_status` |
| `0x23` | 21 | 1 | `14` `wlm_flight_push_handheld_osd` |
| `0xEE` | 8 | 1 | `07` `wlm_recv_app_run_background` |

То есть LTE-набор `0x18` WLM тоже слушает, но лишь в части peer-state и
телеметрии; активация модема в нём не участвует.

## 6. Что из этого следует для sweep'а

- На RC2 **95 либо 96 из 128 request-кадров не имеют request-handler**; на
  RC Pro 2 — 93 либо 94. Сюда входят все `0x52…0x7F`, пустые слоты ниже
  границы и ACK-only ID `05/07/1D/1F/24`.
- В **32–33 request-handler RC2** (34–35 на RC Pro 2) sweep всё же входит.
  Среди них `51:02` link mode switch trigger, `51:0F` route switch,
  `51:15` select target dev, `51:19` modem on/off, `51:1A` service mode
  switch, `51:1B` power-control report, `51:22` bind status и `51:2C`
  bandwidth attach. Все получают payload `00 00 00 + ASCII identity`,
  который не соответствует доказанному контракту этих разных команд.
- **`51:19` — единственный ID во всём наборе, прямо относящийся к модему.**
  `wlm_modem_onoff_control` живёт в
  `modules/power_ctrl/wlm_power_ctrl.c`. На RC2 (`0x1290bc`), RC Pro 2 v576
  (`0x1857b8`) и WA530 (`0x19d42c`) условие одинаково:
  `cmp len, #7; b.hi processing`. То есть длина **≤ 7 отвергается**, а
  обработка начинается только при длине > 7. Sweep всегда попадает в длинную
  ветку: payload содержит три нуля и минимум пятисимвольную `WA/WM` identity.
  Там первые четыре байта трактуются как поля состояния/управления, поэтому
  `payload[3]` оказывается первым ASCII-символом identity. В проверенном RC2
  handler это значение не равно допустимым `0/1`, попадает в
  `error cmd_type` и не доходит до on/off-действия. Сравнение с сохранённым
  состоянием само по себе не завершает обработку и не является отдельным
  «коротким контрактом».
- Профиль использует `cmd_type = NO_ACK_NEEDED` и не запрашивает/не читает
  ACK, поэтому «успешная отправка» в UI означает только успешную запись в
  сокет, а не подтверждённую обработку.
- Это согласуется с наблюдением пользователя: реальная отправка всего sweep не
  дала видимого эффекта. Это локальный отрицательный результат, а не
  доказательство поведения всех DJI builds: upstream отдельно заявляет успех
  на M30T/RM700 и помечает Mavic 4 Pro/RC Pro 2 как совместимые, но не
  публикует достаточных логов, чтобы связать результат именно с этим sweep.

Штатный путь активации — другой и описан в
[`LTE_DUML_COMMAND_REFERENCE.md`](LTE_DUML_COMMAND_REFERENCE.md): `00:32`
(`common_dongle_activate`), набор `0x18` у `dji_lte`, eSIM через `18:4B/4C`.

## 7. Воспроизведение

Для анализа использовались одноразовые локальные scratchpad-скрипты: минимальный
ELF-парсер, декодер Android packed relocations, дамп/сравнение command tables и
сборщик кадров с CRC-8/CRC-16 по `DumlTransport.kt`. Они не входят в
репозиторий; воспроизводимыми опорными данными здесь являются SHA-256
артефактов, адреса функций/таблиц и следующий порядок проверки.

| Артефакт `dji_wlm` | SHA-256 |
|---|---|
| RC2 (`a3s`) | `5195868a7c97b0e131c889590e84e7cabddfa6635aaf0b27e52da14fee5005d3` |
| RC Pro 2 v576 | `0d62e3b3cf368de1fc7d019debfb60457765c4a68289eeaef48988967a0b7220` |
| WA530 | `66da35f73a67bddffb9bcd7564c7b7ff5ac1401fe68703f8476426637a9ce593` |

1. Достать `.gnu_debugdata` (LZMA) → мини-symtab с именами функций.
2. Декодировать `.rela.dyn`: у RC2 и WA530 это `APS2`, у RC Pro 2 v576 —
   обычный `RELA`.
3. Найти `bl` на PLT-стаб `duss_event_create_client_more_config` — **это
   основная регистрация**; восстановить `x2` (tables) и `x3` (counts), базы
   лежат в стеке. Вызовы `duss_event_register_dynamic_command_table` дают лишь
   дополнения, и если смотреть только их, картина получится неполной.
4. Слот `cmd_set` → адрес таблицы, `counts[cmd_set]` → длина; дальше stride 24
   и релокации по `+0` (req) и `+8` (ack).
