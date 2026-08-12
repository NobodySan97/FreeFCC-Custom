# Разбор «switch-таблиц 10:58» в dji_perception wa341 — вердикт: ложное срабатывание (.eh_frame_hdr)

Дата: 2026-08-12. Исполнитель: статический анализ (Ghidra 12.1.2 headless + python/rizin кросс-проверки).

**Краткий вердикт:** структуры `{u32 case_value, u32 code_label}` по смещениям
`0xbe4c90` (EMMC_Data_M4) и `0x439f20` (EMMC_Data_5_64gb) — это **не DUML-диспетчеризация**,
а 4-байтно-смещённое (misaligned) чтение **binary-search таблицы `.eh_frame_hdr`**
(метаданные unwind'ера C++-исключений). «case value» `0x00100058` — это просто
смещение FDE-записи от начала `.eh_frame_hdr`; совпадение с LE32-упаковкой
DUML-команды 10:58 — случайность. Статического обработчика 10:58 в этих местах нет.

## 1. Идентичность артефактов — VERIFIED

| Платформа | Путь | Размер | SHA-256 | Build ID |
|---|---|---|---|---|
| v1_wa341 (RC Pro, FW 10.00.02.37) | `.scratch/emmc/EMMC_Data_M4/root/system/bin/dji_perception` | 68 324 720 | `3e28ae8cfac05cffaaf77582e329f4cd7604abf97178b210304f4f3781ebc641` ✓ | `c75f179b2a262887` |
| eagle3_wa341 (FW 10.00.08.02) | `.scratch/emmc/EMMC_Data_5_64gb/root/system_bin/dji_perception` | 19 634 144 | `4d71a6e4006be6158d61f64b48e8a45610ca569e94f15320d91cbc6738ca0818` ✓ | `266444a25ffb307a` ✓ |

Оба ELF64 DYN arm64, vaddr == file offset. Адреса далее — raw VA (= file offset);
Ghidra загружает оба бинаря с image base 0x100000 (в её адресах +0x100000).

## 2. Где на самом деле лежат «таблицы»

Карта секций (readelf -SW):

- **M4**: `.eh_frame_hdr` @ `0xbc4330`, size `0xa9b94` → `[0xbc4330, 0xc6dec4)`;
  `.eh_frame` @ `0xc6dec8`..`0xf7ac00`; `.text` @ `0xf80000`..`0x3d84e10`.
  «Таблица» @ `0xbe4c90` — **внутри `.eh_frame_hdr`**.
- **D5**: `.eh_frame_hdr` @ `0x4175a0`, size `0x31554` → `[0x4175a0, 0x448af4)`;
  `.eh_frame` @ `0x448af8`..`0x530eb8`; `.text` @ `0x540000`..`0x123fc20`.
  «Таблица» @ `0x439f20` — **внутри `.eh_frame_hdr`**.

Обе точки покрыты сегментом `GNU_EH_FRAME` (readelf -lW): M4 `0xbc4330+0xa9b94`,
D5 `0x4175a0+0x31554`. Эти данные читает unwind'ер (libunwind/libgcc) через
PT_GNU_EH_FRAME — из кода программы на них **нет ни одной ссылки** (см. §4).

## 3. Структурное доказательство

Заголовок `.eh_frame_hdr` (оба бинаря): `version=1, eh_frame_ptr_enc=0x1b,
fde_count_enc=0x03, table_enc=0x3b`, далее eh_frame_ptr (sdata4, pcrel) и fde_count.

- M4: `fde_count = 86897`, grid таблицы @ `0xbc433c`, конец grid `0xbc433c + 8*86897 = 0xc6dec4` — **точно** конец секции.
- D5: `fde_count = 25257`, grid @ `0x4175ac`, конец `0x4175ac + 8*25257 = 0x448af4` — **точно** конец секции.

Смещения «таблиц 10:58» относительно grid:

- M4: `0xbe4c90 − 0xbc433c = 0x20954 = 8*16682 + 4`
- D5: `0x439f20 − 0x4175ac = 0x22974 = 8*17710 + 4`

Т.е. чтение велось со сдвигом **+4 байта** от сетки 8-байтных записей
`{s32 pc_rel, s32 fde_rel}` (оба — смещения от начала `.eh_frame_hdr`).
При таком сдвиге «case» = `fde_rel` записи k, а «label» = `pc_rel` записи k+1.

Контроль целостности: `pc_rel` строго возрастает по всей таблице —
**0 инверсий из 86897** (M4) и **0 из 25257** (D5); для таблицы
`{case,label}` возрастающие «label»-ы не нужны, а для поисковой таблицы
unwind'ера сортировка обязательна.

### Реальные записи вокруг точки «10:58» (M4)

```
 [16680] pc_rel=0x00b56d70 -> PC 0x171b0a0 (в .text ✓)  fde_rel=0x00100010 -> FDE 0xcc4340 (в .eh_frame ✓)
 [16681] pc_rel=0x00b56e94 -> PC 0x171b1c4            fde_rel=0x00100038 -> FDE 0xcc4368
 [16682] pc_rel=0x00b56ee4 -> PC 0x171b214            fde_rel=0x00100058 -> FDE 0xcc4388   ← «case 10:58»
 [16683] pc_rel=0x00b57008 -> PC 0x171b338            fde_rel=0x00100080 -> FDE 0xcc43b0   ← «label 0xb57008»
 [16684] pc_rel=0x00b5705c -> PC 0x171b38c            fde_rel=0x002be7c8 -> FDE 0xe82af8   ← «case 2b:e7c8»
```

### Реальные записи вокруг точки «10:58» (D5)

```
 [17709] pc_rel=0x00a5ba8c -> PC 0xe7302c   fde_rel=0x00100030 -> FDE 0x5175d0
 [17710] pc_rel=0x00a5bb34 -> PC 0xe730d4   fde_rel=0x00100058 -> FDE 0x5175f8   ← «case 10:58»
 [17711] pc_rel=0x00a5ca98 -> PC 0xe74038   fde_rel=0x00100090 -> FDE 0x517630   ← «label 0xa5ca98»
```

Декодирование самих FDE (length, CIE ptr, pc_begin pcrel|sdata4) подтверждает
интерпретацию — `pc_begin` совпадает с PC из таблицы:

- M4 FDE @ `0xcc4388` (fde_rel=`0x100058`): `pc_begin = 0x171b214`, range `0x124` ✓
- M4 FDE @ `0xcc43b0` (запись 16683): `pc_begin = 0x171b338`, range `0x54` ✓
- D5 FDE @ `0x5175f8` (fde_rel=`0x100058`): `pc_begin = 0xe730d4`, range `0xf64` ✓
- D5 FDE @ `0x517630` (запись 17711): `pc_begin = 0xe74038`, range `0x140` ✓

### Откуда взялись «соседние case-values»

«Семейства» `0x00100010/038/080/0a0/...` (M4) и `0x0008c108/120/138`,
`0x00100030/090/0b8` (D5) — это `fde_rel` соседних FDE: типичный шаг
`+0x18/+0x20/+0x28/+0x40` — плотная упаковка FDE-записей (length 36→запись 0x28
и т.п.), с редкими большими скачками (`0x2be7xx`, `0x000fffd0`) на границах
блоков `.eh_frame`. Ничего DUML-специфичного в этих значениях нет.

Полнотекстовый поиск LE32 `0x00100058` по всему файлу: в M4 (68 МБ) встречается
**ровно один раз** — по адресу `0xbe4c90`; в D5 — два раза (`0x439f20` в таблице
и `0x474d42`, невыровненный, внутри данных `.eh_frame`). Побайтовый поиск
упакованной команды гарантированно находил именно эти точки — отсюда и ложное
срабатывание.

## 4. У «таблиц» нет потребителей в коде

Проверено на обоих бинарях (python-скан .text, 481 285 `adrp` в M4 / 89 448 в D5):

- ни одного `adrp` на страницы `0xbd0000`–`0xc00000` (M4) / `0x420000`–`0x450000` (D5);
- ни одного 64-битного указателя (relocated addend) из `.data`/`.data.rel.ro`/
  `.got` в окрестность ±0x8000 вокруг «таблиц» и их границ.

Код программы эти области не адресует вообще — их читает только unwind'ер
через PT_GNU_EH_FRAME. Для настоящей switch-таблицы это невозможно.

## 5. Ghidra: что реально находится по этим адресам

Метод: `analyzeHeadless -import -noanalysis` + targeted-скрипт
`CaseDecomp.java` (followFlow disassembly + CreateFunctionCmd + DecompInterface;
adrp/add-трекинг строк). Проекты в `/tmp/perc1058/` (analyzeHeadless не принимает
пути с `.scratch`), скрипт и логи: `FreeFCC/.scratch/perception_1058_20260812/`
(`scripts/CaseDecomp.java`, `out/m4_realpc.txt`, `out/d5_realpc.txt`, логи прогонов).

### M4, PC 0x171b214 — функция, чей FDE дал «case 0x00100058»

Обычный код модуля depth perception (строки `.rodata`, извлечены скриптом):

```
 0x638b9d: "depth_fusion"
 0x5b4aa8: "[DF][common]: depth input buffer read lock"
 0x59b8f3: "read_lock"
 0x698f5d: "POSIX error code: %d"
```

```c
undefined8 FUN_0181b214(long param_1)   // Ghidra-адрес = raw 0x171b214 + base 0x100000
{
  iVar1 = func_0x03e82470(&UNK_0012c22c + param_1);      // pthread_mutex_lock thunk
  if (iVar1 == 0) {
    uVar3 = 0;
    if (((&UNK_0012c229)[param_1] & 1) != 0) {
      do {
        uVar2 = func_0x0245a318();                        // монотонные часы (мс)
        if ((uVar2 <= uRam...0c8bc - 1) || (uRam...0c8bc + 3000 <= uVar2)) {
          uRam...0c8bc = uVar2;
          func_0x0245a5d8(0x30000000, ..., &UNK_00638b9d, &UNK_005b4aa8);  // лог "depth input buffer read lock"
        }
        func_0x03e824a0(&UNK_0012c254 + param_1, &UNK_0012c22c + param_1); // pthread_cond_wait thunk
      } while ((&UNK_0012c229)[param_1] & 1);
    }
  } else {
    uVar3 = 0x5000001d;                                   // DJI status code
    func_0x0245a5d8(0x5000001d, &UNK_0059b8f3, 0x66, &UNK_00638b9d, &UNK_00698f5d, iVar1);
  }
  return uVar3;
}
```

Это блокирующее ожидание входного depth-буфера (mutex + cond_wait, rate-limited
лог раз в 3000 мс). Никакого отношения к DUML.

### M4, PC 0x171b338 — «label 0xb57008»

```c
undefined8 FUN_0181b338(long param_1)
{
  *(undefined4 *)(&UNK_0012c229 + param_1) = 1;   // set flag
  func_0x03e82f00(&UNK_0012c254 + param_1);        // pthread_cond_signal thunk
  func_0x03e82480(&UNK_0012c22c + param_1);        // pthread_mutex_unlock thunk
  return 0;
}
```

Парная «signal + unlock» для того же depth-буфера. Размер по Ghidra = 84 = 0x54 —
**точно** совпадает с `range=0x54` из FDE записи 16683 (лишнее подтверждение grid).

### D5, PC 0xe730d4 — функция, чей FDE дал «case 0x00100058»

Большая (4252 адреса) C++-функция teardown/очистки объекта: ручная балансировка
`std::_Rb_tree` (std::map), удаление массивов (`_ZdaPv`), рядом в rodata —
`rocksdb` TLS (`_ZTHN7rocksdb10perf_levelE`). Вызывает маленькие функции по
соседним FDE (`0xe74038`, `0xe74178`, `0xe74304` — именно они дали «labels»).
Диспетчеризации команд (сравнений с 0x001000xx) в ней нет.

### D5, PC 0xe74038 — «label 0xa5ca98»

Шаблонный C++-код присваивания small-string/буфера через виртуальный вызов
(`(**(code **)(*(long *)*param_2 + 0x68))(...)`), копирование 6 байт payload,
освобождение старого буфера. Обычный helper.

## 6. Выводы

1. «Статическая диспетчеризация 10:58 как case-value в switch-таблице» в
   `dji_perception` wa341-поколения — **артефакт misaligned-чтения `.eh_frame_hdr`**.
   Доказано тремя независимыми способами: (а) секционная геометрия + заголовок
   + сетка + сортировка без единой инверсии; (б) декодирование FDE с точным
   совпадением `pc_begin`; (в) Ghidra-декомпиляция реальных PC — обычный
   perception/utility код без командной логики.
2. Семантики «10:58 на M4 vs Data_5» по этим двум хитам сравнивать нечего:
   они не являются обработчиками. Совпадающее значение `0x00100058` на обеих платформах — совпадающее
   *смещение FDE* в плотно упакованном `.eh_frame`, что для бинарей одного
   поколения SDK неудивительно. Статический userspace-обработчик `10:58` в рамках
   этой проверки не локализован; его глобальное отсутствие не доказано.
3. Результат согласуется с ранее зафиксированными негативами: WA530 и WA234 —
   `10:58` среди статически разрешённых регистраций `vp_message_set_req_callback`
   нет (см. `PERCEPTION_DUML_HANDLER_MAP.md`, `.scratch/reverify_20260811/wa530_perception_1058_recheck.md`).
4. Методологическая заметка на будущее: кандидатов в «switch-таблицы
   {case,label}», найденных побайтовым поиском packed-команды, обязательно
   проверять на (а) вхождение в `.eh_frame_hdr`/`.eh_frame`/`.gcc_except_table`,
   (б) сортированность «labels», (в) наличие code/data xref. Здесь все три
   проверки сразу дают отрицательный результат.

## Воспроизведение

- Скрипт Ghidra: `FreeFCC/.scratch/perception_1058_20260812/scripts/CaseDecomp.java`
- Логи/дампы декомпиляции: `FreeFCC/.scratch/perception_1058_20260812/out/{m4_realpc.txt,d5_realpc.txt,*_run2.log}`
- Проверки таблиц/FDE: inline python из истории сессии (readelf -SW/-lW +
  разбор заголовка `.eh_frame_hdr`, grid-математика, декод FDE).
