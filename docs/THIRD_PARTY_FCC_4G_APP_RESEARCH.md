# Сторонние DJI FCC/4G-приложения и механизмы

Дата фиксации: 2026-07-25.

Цель документа — собрать найденные сторонние приложения и открытые реализации,
которые включают FCC либо заявляют разблокировку DJI Enhanced Transmission/4G,
и отделить реальные технические признаки от рекламы и неподтверждённых
назначений DUML-команд.

Связанные внутренние документы:

- [Live-карта DJI cellular-модемов](DJI_CELLULAR_MODEM_LIVE_MAP.md);
- [справочник DJI LTE DUML/DUSS](LTE_DUML_COMMAND_REFERENCE.md);
- [анализ Drone-Hacks Mobile APK](DRONE_HACKS_MOBILE_APK_ANALYSIS.md);
- [аудит команд FreeFCC](DUML_COMMAND_AUDIT.md);
- [разбор command set `0x51`](WLM_CMDSET_51.md).

## Уровни доказательств

- `OBSERVED` — непосредственно найдено в APK, ELF, исходном коде, документации
  производителя или декомпилированном call path.
- `DERIVED` — следует из нескольких наблюдений, но не является отдельным
  аппаратным тестом.
- `HYPOTHESIS` — правдоподобное объяснение, для которого ещё нужен capture или
  реальное устройство.
- `NEGATIVE` — искомая команда или механизм не найден в проверенном scope.
- `VENDOR CLAIM` — утверждение разработчика продукта, не подтверждённое
  независимым capture.

Ни один APK из этого исследования не устанавливался на пульт. Команды на
реальное устройство не отправлялись, OTA не загружалась и не применялась.

## Краткий результат

| Решение | FCC | 4G | Установленный механизм | Статус |
|---|---:|---:|---|---|
| NLD FCC Android | Да | Заявлено | USB/AOA/RCLink, серверный профиль, native DUML, фоновое поддержание FCC | APK подтверждает механизм; plaintext профиля скрыт |
| OpenFCC | Да | Да | Native relay к DUSS, защищённые команды, aircraft SN, Always-on 4G; опциональный controller OTA flow | Сильное статическое evidence; точные 4G-кадры скрыты |
| Drone Tweaks | Да | Отдельно не заявлено | Модифицированный DJI Fly с изменённой региональной логикой | Механизм FCC подтверждён самим поставщиком |
| Drone-Hacks | Да | Не включает | Патч firmware либо серверная One-Shot-команда | Точные mobile payload отсутствуют в APK |
| FCC Switch / открытые N1/N2 apps | Да | Нет | Два открытых DUML-кадра через USB serial | Публичный минимальный FCC-примитив |
| FreeFCC-USB | Заявлено | Нет | 21-кадровый профиль через AOA/RCLink | Исходники открыты, но README отмечает отсутствие hardware test |
| O3/FPV `ham_cfg_support` | Да | Нет | Файл-флаг на SD-карте очков | Другой product family, не consumer LTE |
| DJI Mobile SDK | Не предоставляет FCC unlock | Да | LTE authentication, policy check, WLM link/service mode | Штатный многостадийный путь |

## Проверенный APK-корпус

| Артефакт | Версия | Размер | SHA-256 |
|---|---|---:|---|
| NLD FCC `com.nolimitdronez.nldfcc` | `1.2.0.5` (`41`) | 4,894,384 | `a37d0125334c79038d8dd63d4a12e4da29be884ae5510853de94211d497cb580` |
| OpenFCC `app.openfcc` | `1.2.30` (`35`) | 12,000,582 | `04a580af86b780d7d497a676738e16ee8d609983a4c5b60dc7c772d23f87e8ee` |
| OpenFCC Windows Setup | `1.2.4` family | 54,057,943 | `50bc1e3a1ac532af4ed54a0057960610e619ddfe0246233f134a5dd02d26b741` |
| Drone-Hacks Mobile `com.dronehacks.client` | `1.0.1` (`1000001`) | 89,211,250 | `4694dd10fb78d1c317906b4bac15aa8e6aaa9096a941bd7f93388068c6a955dd` |

Примечание: APK и декомпиляции хранятся только в ignored `.scratch/` либо в
локальном `Downloads`; исходные бинарники не добавляются в Git.

## NLD FCC Android

Источники:

- [страница продукта](https://nolimitdronez.com/nldfcc-for-android);
- [страница загрузки](https://nolimitdronez.com/download);
- [анонс NLD FCC Android](https://nolimitdronez.com/introducing-nld-fcc-for-android).

### Что подтверждает APK

`OBSERVED`:

- приложение имеет собственный разбор/сборку DUML;
- поддержаны Android Open Accessory, USB bulk/VCOM и внутренний `RCLink` для
  smart controller;
- для разных путей выбираются разные component routes;
- native-библиотека `libnld-core.so` скрывает построение профиля, API URL,
  шифрование/дешифрование ответов и обработку DUML response;
- запрос профиля содержит `DEVICE_TYPE` и разновидность transport/license;
- foreground service показывает состояние `Maintaining FCC mode`;
- для smart-controller режима работает coroutine-loop с паузой `2000 ms`;
- на каждом проходе вызывается применение/проверка профиля; native cache
  проверяется первым, а повторная сеть нужна только при специальном результате,
  поэтому это не означает HTTP-запрос каждые две секунды;
- уведомление содержит действие остановки FCC;
- UI отображает этапы `Enable 4G SIM`, `Enable FCC Mode` и
  `Disable Remote ID`.

`DERIVED`: NLD — не простая оболочка над двумя известными N1/N2-кадрами.
Приложение имеет полноценный транспорт, model-dependent профиль, обработку
ответов и фоновое поддержание состояния.

`UNKNOWN`: UI из трёх шагов не доказывает три отдельные DUML-команды. Все три
операции могут входить в один серверный профиль. Plaintext команд в DEX,
resources и обычных строках `libnld-core.so` не найден.

`VENDOR CLAIM`: NLD заявляет worldwide 4G SIM и отключение Remote ID на
поддерживаемых моделях. Без plaintext capture нельзя установить, какой кадр
отвечает за 4G и одинаков ли он для разных aircraft.

### Важное сравнение с FreeFCC

NLD поддерживает FCC примерно раз в две секунды. Это показывает, что сам по
себе пятисекундный короткий тик FreeFCC не выглядит чрезмерно частым. Однако
нельзя переносить этот вывод на полный 128-кадровый `4g.json`: NLD может
проверять native state, использовать кэш и отправлять лишь необходимую
model-dependent часть.

## OpenFCC

Источники:

- [главная страница OpenFCC](https://openfcc.app/);
- [совместимость](https://openfcc.app/compatibility);
- [patch notes](https://openfcc.app/patch-notes).

### APK

`OBSERVED` в `libopenfcc_secure.so`:

- endpoint `/duss/mb/0x205`;
- отдельные native relay sessions;
- JNI entry points `nativeOpenRelaySession`, `nativeRelaySessionSend`,
  `nativeDecryptEnvelope`, `nativeExportRelayPlaintextOnce` и
  `nativeBuildDumlFrame`;
- отдельные HKDF contexts для FCC и 4G sessions;
- foreground path `FourGHeartbeatService`;
- режим `always_on_4g`;
- получение и отображение aircraft serial;
- encrypted/enveloped command material вместо открытого JSON-профиля.

APK содержит правдоподобно названные decoy-классы и документы. Названия вроде
`FccSequenceWm261Legacy` нельзя считать источником истины без перехода к
native relay и реальному plaintext.

`DERIVED`: OpenFCC действительно имеет отдельную долговременную 4G-сессию, а
не только маркетинговую надпись. Aircraft serial участвует в 4G path, что
согласуется с patch notes: разработчик называет SN обязательным для
диагностики 4G.

`UNKNOWN`: точные DUML `cmdSet`, `cmdId`, route и payload защищены. Статический
APK не позволяет доказать, какие команды включают WLM, какие снимают country
gate и какие только поддерживают уже созданную сессию.

### Desktop launcher и controller firmware

`OBSERVED` в декомпилированном launcher:

- предусмотрен отдельный `4G controller OTA swap`;
- launcher получает одноразовый download grant;
- проверяет SHA-256 загруженного `update.zip`;
- использует Android `update_engine_client` для A/B OTA;
- после успешного применения перезагружает контроллер;
- внутреннее описание связывает swap с открытием controller WLM gate, а
  активационные кадры затем отправляет OpenFCC APK.

`UNKNOWN`: firmware payload не загружался. Его модель, build, подпись, точный
diff и безопасность применения в этом исследовании не проверены.

`DERIVED`: OpenFCC разделяет как минимум два слоя:

1. firmware/capability gate на контроллере;
2. последующую FCC/4G-командную сессию приложения.

Это лучше объясняет рабочий 4G на регионально заблокированных системах, чем
безусловная отправка одного общего набора кадров.

## Drone Tweaks

Источники:

- [Drone Tweaks FAQ](https://www.drone-tweaks.com/faq);
- [линейка продуктов](https://www.drone-tweaks.com/).

`OBSERVED` по документации поставщика: Drone Tweaks распространяет
модифицированную версию DJI Fly/GO4, которая сохраняет FCC mode и использует
обычный DJI account.

`DERIVED`: этот подход может изменять country/FCC checks непосредственно в
DJI Fly, то есть на другом слое, чем FreeFCC. Он не доказывает наличие особой
универсальной DUML-команды FCC.

`NEGATIVE`: на публичных страницах не найден отдельный продукт или обещание
разблокировать worldwide 4G. Отзывы о совместной работе Drone Tweaks и DJI
dongle доказывают максимум совместимость, но не включение 4G самим продуктом.

## Drone-Hacks

Источники:

- [известные проблемы Drone-Hacks](https://wiki.drone-hacks.com/en/dh2-known-issues);
- [Mavic 3 и 4G](https://wiki.drone-hacks.com/en/dh2-mavic-3-standard);
- [FCC guide](https://wiki.drone-hacks.com/en/fcc-guide).

Подробный статический разбор вынесен в
[DRONE_HACKS_MOBILE_APK_ANALYSIS.md](DRONE_HACKS_MOBILE_APK_ANALYSIS.md).

`OBSERVED`:

- Android Mobile APK подключается непосредственно к aircraft по USB;
- One-Shot FCC получает с backend готовые `cmdSet`, `cmdId` и `payload`;
- команда передаётся через общий `SendCustomPacket`;
- конкретные FCC bytes не находятся в APK;
- desktop/firmware продукт поддерживает постоянные модификации FCC отдельно от
  Mobile One-Shot.

`VENDOR CLAIM`: Drone-Hacks предупреждает, что постоянный FCC может ухудшать
работу 4G dongle, и советует `DH FCC off`.

`UNKNOWN`: формулировка «FCC signal may overpower 4G» не раскрывает физический
механизм и не подтверждена нашим RF capture. Возможны RF desense, особенности
общего radio frontend, firmware policy либо неверная интерпретация полевого
наблюдения.

Практический вывод: одновременное постоянное FCC и 4G нужно проверять отдельно,
а не считать гарантированно совместимыми или несовместимыми.

## Открытые FCC-приложения для RC-N1/N2

Проверенные исходники:

- [M4TH1EU/DJI-FCC-HACK](https://github.com/M4TH1EU/DJI-FCC-HACK);
- [posernico91-lab/dji-fcc-tool](https://github.com/posernico91-lab/dji-fcc-tool);
- [derekhe/dji-mavic-fcc](https://github.com/derekhe/dji-mavic-fcc);
- [FCC Switch в Google Play](https://play.google.com/store/apps/details?id=io.poserpy.rangeboost).

Независимые реализации отправляют одинаковые два raw frame через USB CDC
`19200 8N1`:

```text
55 0D 04 21 2A 1F 00 00 00 00 01 86 20
55 18 04 20 02 09 00 00 40 09 27 00 02 48 00 FF FF 02 00 00 00 00 81 1F
```

Второй кадр содержит:

```text
09:27 00024800ffff0200000000
```

`OBSERVED`: payload соответствует записи assistant-регистра
`0xffff0048 = 2`, ранее известной в FreeFCC как `setForceFcc`.

`DERIVED`: первый короткий frame выполняет transport/session/bootstrap роль;
называть его «сменой региона» без firmware handler evidence нельзя.

`NEGATIVE`: в этих приложениях нет включения 4G, WLM pairing, LTE
authentication или Enhanced Transmission.

## FreeFCC-USB и 21-кадровый профиль

Источник:
[doesthings/FreeFCC-USB](https://github.com/doesthings/FreeFCC-USB).

`OBSERVED` в открытом JSON:

- 21 frame, два раунда;
- встречаются `03:F9`, `06:72`, `07:30`, два `09:27`, `07:18` и другие
  команды;
- используется AOA/RCLink envelope и transport keepalive;
- описание профиля утверждает capture из `/api/trial/command`;
- README одновременно помечает приложение как не проверенное на реальном
  дроне.

`UNKNOWN`: комментарии к кадрам не являются firmware-proof. В частности,
назначения service mode, region commit и «универсальность для всех aircraft»
требуют handler analysis либо live ACK/readback.

`HYPOTHESIS`: путь `/api/trial/command` и форма model-dependent профиля похожи
на коммерческий FCC backend, но без provenance/capture нельзя приписывать
профиль конкретно NLD или OpenFCC.

`NEGATIVE`: этот 21-кадровый профиль не содержит доказанного полного DJI LTE
activation flow и не является подтверждением текущего FreeFCC `4g.json`.

## O3/FPV и `ham_cfg_support`

Источник:
[YarosMallorca/dji-o3-fcc-hack](https://github.com/YarosMallorca/dji-o3-fcc-hack).

Приложение создаёт либо удаляет файл `ham_cfg_support` на выбранной SD-карте.
Файл предназначен для DJI goggles/O3/FPV product family.

`OBSERVED`: это отдельный file-flag механизм FCC.

`NEGATIVE`: он не включает consumer DJI Cellular Dongle, не создаёт WLM pair и
не раскрывает команды Enhanced Transmission.

## Штатный DJI LTE path

Источники:

- [DJI Mobile SDK LTE](https://developer.dji.com/doc/mobile-sdk-tutorial/en/tutorials/lte.html);
- [DJI Cellular Dongle FAQ](https://repair.dji.com/help/content?customId=01700008285&documentType=&lang=en&paperDocType=ARTICLE&re=US&spaceId=17).

Публичный MSDK использует `ILTEManager`:

- получение verification code;
- `startLTEAuthentication(...)`;
- обновление authentication info;
- проверка возможности включения LTE;
- переключение Enhanced Transmission между `OCU_SYNC` и `OCU_SYNC_LTE`.

В локально проверенном DJI MSDK 5.18 также присутствуют WLM link/service keys
и внутренний action `KeyRcEnable4G`. Перед переключением штатные delegate
проверяют `LTEService.canEnableLTE()` и возвращают `CAN_NOT_ENABLE_LTE`, если
policy/capability не разрешает операцию.

Официальный FAQ подтверждает дополнительные обязательные условия:

- совместимые aircraft/controller/dongle;
- SIM без PIN и рабочая регистрация в сети;
- актуальные firmware и DJI Fly;
- 4G на обеих сторонах;
- подписка и DJI relay server;
- включение Enhanced Transmission через DJI Fly.

`DERIVED`: штатный DJI 4G — это state machine:

```text
dongle detect/activate
  -> SIM/APN/dial
  -> peer dongle
  -> authentication/subscription
  -> pairing
  -> WLM ability/policy
  -> OCU_SYNC_LTE
  -> relay session
```

Одной универсальной безусловной команды «включить модем» в проверенном
официальном path нет.

## Сопоставление с текущим FreeFCC

### FCC

Наиболее сильные открытые признаки:

1. смена country через `07:30` подтверждена live-проверкой FreeFCC;
2. `09:27 00024800ffff0200000000` повторяется в нескольких открытых
   N1/N2-реализациях;
3. коммерческие приложения используют более широкий профиль и/или изменённый
   DJI Fly, но plaintext часто скрывают.

Это поддерживает отдельное A/B-тестирование минимального FCC core, но не
доказывает назначение каждого кадра полного профиля.

### 4G

`NEGATIVE`: последовательность FreeFCC `51:00..7F` не найдена в открытом виде
ни в NLD, ни в OpenFCC, ни в Drone-Hacks, ни в официальном MSDK path.

Отдельная кросс-проверка приёмной стороны записана в
[WLM_CMDSET_51.md](WLM_CMDSET_51.md). Она восстановила dynamic command tables
`dji_wlm` из трёх независимых образов: RC2, RC Pro 2 v576 и WA530.

`OBSERVED`:

- destination `0xEE` соответствует `dji_wlm` (`0x0e07`), а не `dji_lte`
  (`0x0e06`);
- основная таблица набора `0x51` регистрируется в
  `duss_event_create_client_more_config` и имеет 82 слота (`0x00..0x51`):
  заполнено 33 на RC2 и 35 на RC Pro 2 v576 (`2E` netlink service и `2F` agent
  general control — только у RC Pro 2);
- дополнительно тремя таблицами регистрируется device-manager sync
  `30/31/32/33/35/36`, из которых варианты взаимоисключающие; итого
  одновременно активны 37–38 handler ID;
- около 90 кадров текущего sweep не имеют активного handler'а, а 37–38
  доходят до живых обработчиков и получают `00 00 00 + ASCII S/N`, не
  соответствующий их контракту; задеты в том числе link mode switch (`02`),
  route switch (`0F`), select target dev (`15`), power control (`1B`/`1D`),
  bind status (`22`);
- в наборе нет LTE **activation** handler, но есть `51:19`
  `wlm_modem_onoff_control` (power-control путь) и `51:42`
  `wlm_ability_nego_result_req` — приёмник для `lte_query_wlm_nego_result` из
  `dji_lte`; отдельно `dji_wlm` слушает три ID набора `0x18`
  (`37` peer state, `3B` rpt track, `47` i-frame for wifi);
- `NO_ACK_NEEDED` не позволяет отличить обработку от простой успешной записи
  в сокет.

`DERIVED`: текущий `51:00..7F` sweep не является реализацией штатного либо
стороннего 4G activation path и должен рассматриваться как неподходящий legacy
research profile.

`OBSERVED`: NLD и OpenFCC учитывают model/device identity; OpenFCC отдельно
использует aircraft serial и долговременную 4G-сессию.

`DERIVED`: текущая успешная запись 128 кадров FreeFCC подтверждает только
transport write. Она не подтверждает:

- снятие region/country gate;
- разрешение WLM service mode;
- dongle activation;
- authentication/subscription;
- pairing двух LTE endpoints;
- появление `lte_conn=1`/`lte_usable=1`;
- фактический video/control traffic через LTE.

По совокупности данных расширять `4g.json` дополнительными случайными
командами нецелесообразно. Сначала нужен plaintext capture работающего
стороннего решения либо официальный state/readback.

## Таблица ключевых выводов

| Claim | Level | Artifact/location | Evidence | Confidence |
|---|---|---|---|---|
| NLD имеет реальный DUML transport | `OBSERVED` | NLD APK/Dex/native | USB/AOA/RCLink, parser, response handling | Высокая |
| NLD поддерживает FCC примерно раз в 2 s | `OBSERVED` | `p3.o0` coroutine | loop вызывает профиль и делает delay `2000 ms` | Высокая |
| NLD действительно отправляет отдельную известную 4G-команду | `UNKNOWN` | `libnld-core.so` | plaintext профиля скрыт | Низкая |
| OpenFCC имеет отдельный Always-on 4G path | `OBSERVED` | OpenFCC APK/native | `FourGHeartbeatService`, 4G native relay, aircraft SN | Высокая |
| OpenFCC controller OTA открывает WLM gate | `OBSERVED` + `DERIVED` | desktop launcher | OTA flow и внутреннее описание; firmware не скачивалась | Средняя |
| Drone Tweaks меняет DJI Fly | `OBSERVED` | vendor FAQ | поставщик прямо описывает modified official app | Высокая |
| Drone-Hacks FCC мешает 4G | `VENDOR CLAIM` | Drone-Hacks Wiki | полевое предупреждение без RF evidence | Низкая/средняя |
| `09:27` — публичный минимальный FCC-примитив | `OBSERVED` | три open-source реализации | одинаковый payload `0xffff0048=2` | Высокая |
| 21-frame FreeFCC-USB профиль универсален | `HYPOTHESIS` | GitHub JSON/README | профиль открыт, но hardware test отсутствует | Низкая |
| FreeFCC 128-frame sweep включает Enhanced Transmission | `NEGATIVE` в проверенных RC2/RC Pro 2 v576/WA530 tables | `profiles/4g.json`, `WLM_CMDSET_51.md` | адресуется `dji_wlm`; LTE **activation** handler в наборе отсутствует, хотя sweep задевает `51:19` modem on/off и link/power-control ID | Высокая для проверенных builds |
| Штатный DJI LTE требует нескольких стадий | `OBSERVED` + `DERIVED` | MSDK, DJI FAQ, firmware docs | auth, capability, pairing, WLM, relay | Высокая |

## Следующий наиболее информативный эксперимент

Наибольшую неопределённость снимет один контролируемый plaintext capture
успешного 4G-включения NLD либо OpenFCC на лицензированном/пробном устройстве:

1. снять исходные country, WLM, dongle, pair и LTE states;
2. записать весь локальный DUSS/DUML stream до, во время и после нажатия 4G;
3. отдельно сохранить ACK/readback;
4. повторить только FCC без 4G и получить differential set;
5. перезагрузить controller/aircraft и проверить, что именно приложение
   восстанавливает;
6. не смешивать модели и controller firmware builds.

Такой capture позволит отделить:

- FCC-команды;
- Remote ID-команды;
- region/capability unlock;
- одноразовую 4G activation;
- периодический heartbeat;
- model/SN-dependent материал.

Без этого переносить скрытые профили сторонних приложений в FreeFCC было бы
гипотезой, а не воспроизводимой реализацией.
