# Live DUML-карта Air 3S + RC Pro 2

Дата: 2026-07-20.

Scope: DJI Air 3S `WA234`, RC Pro 2 `rc520`, FreeFCC `1.5.25`, один
пользовательски размеченный CE baseline, ручные FCC apply и отдельный чистый
cold-boot тест Auto-FCC. Это evidence только для этой связки.

## Главный результат

- На RC Pro 2 `40009` отдаёт не узкий RC-only фон, а широкий telemetry bus:
  camera, flight, GNSS/Home Point, gimbal, HD Link, vision и cellular frames.
- Air 3S публикует `03:44` длиной `96` B. LE u16 at payload offset `20` равен
  `0x0047`; bit 0 подтверждает уже записанный Home Point. Layout короче
  Avata 360/O4 (`102` B), поэтому extensions нельзя считать одинаковыми.
- `09:43=0000` наблюдался и в CE, и в двух независимо подтверждённых FCC
  состояниях. Avata-корреляция `0000/0200` не является универсальным FCC
  readback.
- В одном reboot-сеансе runtime записал `home_point_auto`, `42/42`, 12 matching
  ACK и physical FCC наблюдался одновременно, но пользователь не смог исключить
  ручное нажатие FCC в том же запуске. Причинная связь CAUSALITY UNKNOWN.
- Последующий контролируемый cold boot без ручной FCC-кнопки доказал отказ:
  listener завершился `monitor_failed` до Home Point; после появления
  `03:44=0x0047` AUTO attempt так и остался `null`, а пользователь подтвердил CE.
- Пассивный поток пока не содержит доказанного физического FCC boolean.

## Размеченные состояния

| Состояние | Физическая проверка | Runtime / capture |
|---|---|---|
| Initial CE | Пользователь подтвердил CE | 533 frames по всем портам; `09:43=0000` |
| Первый manual apply | Сначала был FCC, затем позже сбросился в CE; момент сброса неизвестен | `42/42`, но post-window уже имел `09:43=0000`; нельзя маркировать всё окно как FCC |
| Повторный manual apply | Пользователь подтвердил FCC по двум диапазонам; Transmission graph перестал обновляться | 512 frames, `09:43=0000` |
| Первый reboot RC Pro 2 | FCC наблюдался, но ручное нажатие в этом запуске нельзя исключить | Runtime AUTO `42/42`, `12` matching ACK; physical causality UNKNOWN |
| Чистый cold boot без FCC-кнопки | После Home Point пользователь подтвердил CE | `monitor_failed`, AUTO attempt `null`; `03:44=0x0047`, CE-shape `09:21` |

`all_writes_flushed` остаётся доказательством transport write, а не физического
RF mode. В первом ручном опыте это особенно видно: режим позже вернулся в CE,
хотя runtime всё ещё показывал успешную запись.

## Порты RC Pro 2

Первый CE-проход использовал максимальные ограничения текущего LAN API:

| Port | Результат |
|---|---|
| `40009` | Четыре окна по 128 frames; основной широкий поток |
| `40007` | 4 frames в CE, 39 после первого apply, 56 после cold boot; публикация нерегулярна |
| `8901` | 17–20 frames `00:81/82`, controller identity `rc520` |
| `8902–8904` | Соединение успешно, 0 frames за каждое 10-second окно |

Отдельное одновременное чтение `40009` во время FCC writer вернуло 0 frames.
Это NEGATIVE для такой второй broker connection: текущий rootless bridge не
является заменой `tcpdump -i lo` и не гарантирует наблюдение write burst другого
клиента.

## Home Point и identity

Air 3S `03:44`:

- payload length `96` B;
- `home_state` at offset `20` был `0x0047` в двух CE frames и снова `0x0047`
  в первом неоднозначном reboot-сеансе и в чистом post-Home-Point CE;
- frame содержит coordinates и aircraft identity, поэтому raw payload остаётся
  только в ignored `.scratch/` и не публикуется.

`00:81/82` различают identity источника: `WA234` относится к aircraft, `rc520`
к controller. `51:14` также содержит aircraft identity; exact serial в tracked
docs намеренно редактирован.

## Точное имя модели через DUML, 2026-07-26

Повторная live-проверка выполнена на DJI Air 3S + RC Pro 2 `rc520`, FreeFCC
`1.5.45`, DJI Fly `1.21.2`. Все активные пробы были read-only.

Пока FreeFCC находился на переднем плане, короткие captures `8901` и `40009`
публиковали только controller identity `rc520`. После штатного запуска DJI Fly
первый же заполненный capture `40009` дал два независимых aircraft
идентификатора:

| Frame | Route/type | Payload | Интерпретация |
|---|---|---|---|
| `00:82` | `0xA2 → 0x82`, `type=0x00` | ASCII `WA234` + NUL/padding | внутренний aircraft model code |
| `03:34` | `0xA2 → 0x82`, `type=0x00` | `00` + ASCII `DJI Air 3S` + NUL/padding | человекочитаемое имя, которое DJI Fly показывает как `Название` |

Полные CRC-valid frames:

```text
00:82
554d04a8a282df9800008257413233340000000000000000000000000000000000000000000000000000000208000000000000020800000200000000000000000000000000000000000000fe8c

03:34
552e04a7a2829f9800033400444a492041697220335300000000000000000000000000000000000000000000d085
```

Legacy-справочник `dji-firmware-tools` называет `03:34`
`UAV User String Get` / `Get Plane Name`. Поэтому строку `DJI Air 3S` нельзя
считать неизменяемым product identifier: пользовательское имя теоретически
может быть изменено. Надёжная идентификация должна хранить пару:

```text
model_code=WA234
model_name=DJI Air 3S
source=duml_push
verified_in_current_session=true
```

`WA234` следует считать основным стабильным идентификатором, а
`DJI Air 3S` — отображаемым именем, которое дополнительно сверяется с таблицей
model-code → product-name. Тот же текст независимо наблюдался Accessibility
service во вкладке DJI Fly «Информация».

### Активные read-only пробы

Контроллер отвечает на `00:01 VersionInquiry`, если использовать фактический
маршрут RC Pro 2:

```text
port=8901 sender=0x2A dst=0x06 cmd_type=0x20 cmd_set=0x00 cmd_id=0x01
TX 550d04332a06911a200001206e
RX 552c0436062a911a8000010000524335323000000000000000000000000e0000001501020a09000000006546
```

Разбор payload: hardware `RC520`, bootloader `0.0.0.14`, firmware
`10.2.1.21`. Текущий `device_info.json` использует `sender=0x82`, `dst=ANY` и
default port `40009`; на проверенном RC Pro 2 этот маршрут matching response не
дал.

Попытки явно запросить aircraft identity/model через `40009` не дали matching
response:

- `00:01 VersionInquiry` к `dst=0x03` и `dst=0xA2`;
- `00:FF Query Device Info` к `dst=0xEE`;
- `03:34 UAV User String Get` к `dst=0x03` и `dst=0xA2`;
- `03:37 Device Info Get` к `dst=0x03`;
- `03:74 Get Product Id` к `dst=0x03`.

Один bounded wrapped `03:34` exchange на `40007` также вернул только
постороннюю telemetry, без matching `03:34` response. Это NEGATIVE только для
проверенных маршрутов; оно не доказывает отсутствие другого внутреннего
request path DJI Fly.

Практический low-cost вариант для приложения: после запуска DJI Fly открыть
один короткий пассивный `40009` session, завершить чтение сразу после пары
`00:82`/`03:34`, сохранить только распознанные поля и сами два identity frame.
Не следует сохранять весь окружающий поток: в нём встречаются coordinates и
factory serial.

## FCC-кандидаты и исключённые false positives

| Frame | CE | Подтверждённый FCC | Вердикт |
|---|---|---|---|
| `09:43` | `0000`, 2/2 | `0000`, 8/8 в двух FCC sessions | NEGATIVE: не универсальный FCC bit |
| `09:21` | len 29, stable shape `000102ff0301...010103010002` | len 31, stable shape `000102XX0a0201...0301020003010002` | Сильный SDR config transition; `XX` динамический, не boolean |
| `06:AE` | offset 2 `0x41`, channels около `0x0420/0x0407` | offset 2 `0x01`, channels около `0x0400` | Почти наверняка sticks/input: big-endian channel centers `0x0400`; не RF state |
| `19:67` | Не попал в CE windows | `000201000102090100` после apply и в FCC | Applied/config marker либо sampling; не доказанный mode |
| `19:73` | 73 B | Меняется между двумя подтверждёнными FCC sessions | Динамический config/list, не mode |
| `21:06` | Средний byte динамический | Средний byte динамический | Link metric, не mode |
| `18:40` | `0000` | `0000` | NEGATIVE |

Legacy `dji-firmware-tools` называет `09:21` как
`HDLnk SDR Vt Config Info Get`, но не содержит dissector текущего 29/31-byte
layout. Поэтому structural diff считается OBSERVED, а точные поля — UNKNOWN.

## Auto-FCC cold-boot evidence

Сохранённый runtime первого неоднозначного reboot-сеанса:

```text
controller_model=rc520
status=fcc_written
origin=home_point_auto
home_point_observed_at_ms=1784573241421
apply_started_at_ms=1784573243422
apply_finished_at_ms=1784573246544
port=40009
writes=42/42
matching_acks=12
outcome=all_writes_flushed
monitor=stopped
```

Runtime подтверждает, что software AUTO lifecycle выполнялся: Home Point,
2-second settle, полный профиль, terminal stop. Но этот сеанс не доказывает,
что именно AUTO вызвал наблюдавшийся физический FCC, потому что ручное нажатие
пользователь исключить не смог.

Чистый повтор без ручной FCC-кнопки дал противоположный terminal result:

```text
Home Point before capture: not yet recorded
status=monitor_failed
last_attempt_origin=null
auto_home_point_observed_at_ms=null

Home Point after transition: 03:44 home_state=0x0047
physical mode=CE
status=monitor_failed
last_attempt_origin=null
auto_attempt_outcome=null
09:21=len29 CE shape
```

То есть listener умер до Home Point и не был восстановлен после фактического
перехода `home_state=true`. Это CONFIRMED Auto-FCC failure на проверенном cold
boot, а не только отсутствие UI-индикации.

Ручной recovery в той же aircraft session дал контролируемую пару:

| Поле | До ручной кнопки | После ручной кнопки |
|---|---|---|
| Home Point | `03:44=0x0047` | `03:44=0x0047` |
| Runtime origin | `null` | `manual` |
| Writes | Не начинались | `42/42`, `all_writes_flushed`, port `40009` |
| `09:21` | 29 B, CE shape `000102ff0301...` | 31 B, post-profile shape `000102130a0201...` |

Это локализует сбой до FCC writer: профиль и pinned `40009` работают, а
production listener на `40007` завершился до появления Home Point.

## Исправление `1.5.27`

Для `rc520` listener выбирает pinned Connect port `40009` и явно разрешает
наблюдавшийся relayed route `0xA2 → 0x82` только для CRC-valid unencrypted
`03:44`. На всех моделях listener продолжает ждать до Home Point либо явной
отмены. Повторное подключение выполняется через 5 s; RC2 и остальные модели
сохраняют direct/wrapped `40007`, но больше не открывают его с частотой 1 Hz,
которая забивала radio link. После
установки нужен ещё один чистый cold boot без ручной FCC-кнопки.

## Corpus и privacy

Raw JSON хранится только в ignored directories:

- `.scratch/live/20260720-air3s-rcpro2-baseline/`;
- `.scratch/live/20260720-air3s-rcpro2-fcc/`;
- `.scratch/live/20260720-air3s-rcpro2-confirmed-fcc/`;
- `.scratch/live/20260720-air3s-rcpro2-post-reboot-fcc/`.
- `.scratch/live/20260720-air3s-rcpro2-clean-auto-failure/`.
- `.scratch/live/20260720-air3s-rcpro2-clean-manual-recovery/`.

Опорные SHA-256:

| Artifact | SHA-256 |
|---|---|
| CE `40009` sample 1 | `af3d50e7687069cb8ee917a6cea9938cc816b37db75b1ac27df19752ae955fd2` |
| CE `40009` sample 4 with `03:44` | `2aa446179b8e8d37327f9018379bd0301050101ad3158c2192efbe2217637385` |
| CE `40007` | `4edb6a09b10beeeffdeef16754b0f435a70c668e447d54f5c81bf6ddaee8eaeb` |
| First confirmed FCC sample 1 | `31b068eeff819767f72f410b0c2f8f80e09d180b679bd37b9b7cc50ccceb29fb` |
| First confirmed FCC sample 4 | `6a63651b70906995252e0268a949afe669e490e6c4830d9195718dbbbf868eed` |
| Cold-boot FCC runtime | `4e3e6538c2be4e03a4fd3d9cd8a68949dc5f0bab1b7ecbb956ef0d91c8e256d1` |
| Cold-boot FCC `40009` sample 3 with `03:44` | `fb854400a6a173736c79ee7c290fa1609994472a29620e9deb2b2ea4ad5df29d` |
| Cold-boot FCC `40007` | `acd4787a6af3d9a2987bb814c892df07ae2b5e646fedc73309965d9a4b3c0bdf` |
| Clean auto-failure runtime | `2ae75b6c8130c16cf3dda90a80fd75354b4e384e8fa71a88b21ab631bda89092` |
| Clean post-Home-Point CE sample with `03:44` | `0ee6041e009892727670fe8cfdb456ea9074deff12add27c84684bfa1d9a1860` |
| Clean post-Home-Point CE `40007` | `a59624e3376f6bcfec74837f2e4cfda1f8488169f1688412fc290af1943e5de9` |

Все reported frames прошли encoded-length, CRC-8 и CRC-16 validation внутри
`duml_capture`. Raw corpus содержит coordinates и serials и не должен попадать
в git, release assets или публичные issue attachments.
