# Фоновая статистика использования

## Статус

Реализация находится в отдельной ветке и не входит в опубликованный релиз
`v1.5.60`. Android-клиент реализован, но отправка по умолчанию отключена:
`BuildConfig.STATISTICS_ENDPOINT` остаётся пустым, пока при сборке не задан
`FREEFCC_STATISTICS_ENDPOINT` или Gradle property `statisticsEndpoint`.

Backend source, фактический адрес, порты, конфигурация и эксплуатационные данные
сервера намеренно не хранятся в этом публичном репозитории.

Пользователь не видит отдельного уведомления, экрана или переключателя и не
может отключить статистику из приложения. Поэтому репозиторий прямо описывает
сбор до выпуска такой сборки. Статистика содержит полный заводской S/N пульта и
не называется анонимной.

## Что отправляется

Один JSON snapshot `schema_version=1` содержит:

- случайный `installation_id`, создаваемый при первой установке;
- монотонный `report_sequence` для безопасной повторной отправки;
- `app_version_name` и `app_version_code` SkylabFCCfree;
- полный `controller_serial`, прочитанный из системной идентичности пульта, и
  точный `controller_serial_source`;
- Android `Build.DEVICE` и `Build.MODEL` пульта;
- версию DJI Fly (`versionName` и `longVersionCode`) установленного пакета
  `dji.go.v5`;
- последнее известное название и product code дрона, уже прочитанные штатным
  accessibility/пассивным identity flow приложения;
- настройки `auto_fcc_mode`, `home_point_accessibility_enabled` и
  `lan_control_enabled`;
- абсолютные счётчики действий по каждой версии SkylabFCCfree:
  `manual_fcc`, оба режима Auto FCC и выключение, `gps_on`, `gps_off`,
  `led_on`, `led_off`, `four_g_activate`, `launch_dji_fly`.

S/N пульта нормализуется в верхний регистр, но не хешируется. Он является
постоянным идентификатором устройства. `installation_id` отличает переустановки
на одном пульте; принимающая сторона может группировать их по
`controller_serial`.

## Что не отправляется

- S/N дрона, батареи, камеры, подвеса или модема;
- координаты, Home Point, маршрут и flight records;
- содержимое DUML frames и raw socket traffic;
- логи SkylabFCCfree, DJI Fly или Android;
- Android ID, IMEI, MAC, SSID, локальные/публичные IP в JSON payload;
- файлы пользователя и сведения о других приложениях.

Source IP не является полем payload, но неизбежно виден принимающему HTTPS
endpoint во время сетевого соединения.

## Когда отправляется

1. Счётчик увеличивается локально только после принятия соответствующего
   действия; busy/rejected нажатия не учитываются.
2. При старте процесса выполняется один upload, если с последнего успешного
   прошло не менее 24 часов.
3. Действия не создают отдельный HTTP request: они лишь обновляют локальный
   абсолютный счётчик и используют тот же суточный rate limit.
4. Впервые прочитанный или изменившийся S/N пульта разрешает один внеочередной
   snapshot.
5. После ошибки сети следующая обычная попытка разрешается не чаще раза в час.
   Ошибка молча откладывает данные и не влияет на функции приложения.

Клиент повторяет один `installation_id + report_sequence`, пока не получит
успешный HTTP 2xx, и увеличивает sequence только после успеха. Принимающий
backend обязан обрабатывать такой повтор идемпотентно; его реализация ведётся
приватно.

## Источник S/N и версии DJI Fly

Основной путь не требует открывать окно `Информация` и не меняет интерфейс DJI
Fly. В фоновом потоке проверяются, по порядку:

1. совместимый `Build.SERIAL`;
2. read-only свойства `ro.serialno` и `ro.boot.serialno` через
   `/system/bin/getprop`;
3. доступные read-only USB gadget serial files.

Заглушки Android (`UNKNOWN`, нули и стандартные emulator serials), короткие
model codes и строки без смеси букв и цифр отклоняются. Источник сохраняется в
`controller_serial_source`: `build_serial`, `getprop_ro_serialno`,
`getprop_ro_boot_serialno` или `usb_gadget`.

Автоматический системный probe выполняется ровно один раз на установку. Флаг
попытки записывается до чтения; даже если системные источники пусты, повторных
системных запросов не будет. После успешного получения S/N он хранится в
`SharedPreferences` и больше не читается. Сброс возможен только очисткой данных
или переустановкой приложения.

Accessibility service остаётся fallback. Если до получения системного S/N
пользователь когда-либо откроет `Информация`, parser принимает русскую подпись
`Серийный номер пульта` и английские `Remote Controller S/N`,
`Remote Controller Serial Number` или `RC SN`, но исключает соседние S/N дрона,
flight controller, камеры, стабилизатора и батареи. Такое значение получает
источник `dji_fly_ui`.

Live-пассивный capture на RC2 дал `rc331` на порту `40009`, а на `40007` — код
и S/N дрона; полный S/N самого пульта в DUML-потоке не появился. Контрольный
cold-boot capture 2026-08-03 без DUML request/write подтвердил тот же результат:
`40009` — 36 CRC-valid frames и только `rc331` среди identity strings; `40007`
— 11 frames без serial-shaped ASCII; `8902` — 0 распознанных DUML frames. Это
ограниченные выборки, а не доказательство отсутствия поля во всех состояниях.
Read-only `00:51` к RC destinations также не вернул matching response, поэтому
DUML не используется как источник controller S/N. Перед release требуется
подтвердить, что первый доступный системный source на RC2 совпадает с S/N из DJI
Fly.

Версия DJI Fly всегда читается автоматически через Android `PackageManager`;
для неё экран `Информация` не нужен.

## Формат payload

Пример с вымышленными значениями:

```json
{
  "schema_version": 1,
  "installation_id": "439e9436-d52c-4a43-a49c-645d3fb1cc73",
  "report_sequence": 7,
  "app_version_name": "1.5.60",
  "app_version_code": 77,
  "controller_serial": "5WTBH123456789",
  "controller_serial_source": "getprop_ro_serialno",
  "controller_device": "rc331",
  "controller_model": "DJI RC 2",
  "dji_fly_version_name": "1.21.4",
  "dji_fly_version_code": 1021040,
  "aircraft_model_code": "WA530",
  "aircraft_model_name": "DJI Avata 360",
  "settings": {
    "auto_fcc_mode": "home_point_text",
    "home_point_accessibility_enabled": true,
    "lan_control_enabled": false
  },
  "usage_by_app_version": {
    "1.5.60": {
      "gps_on": 4,
      "gps_off": 3
    }
  }
}
```

Размер payload ограничен клиентом; неизвестные поля и действия не формируются.

## Включение endpoint при сборке

Обычная локальная и CI-сборка без переменной не отправляет статистику. Адрес не
публикуется в репозитории и передаётся только доверенной release-сборке:

```bash
FREEFCC_STATISTICS_ENDPOINT='https://<private-statistics-endpoint>' \
  ./gradlew assembleRelease
```

Перед merge/release обязательны live-проверки HTTPS, повторной отправки, смены
S/N, суточного rate limit и точного соответствия README фактическому payload.
