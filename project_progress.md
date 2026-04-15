# SimpleMacroTracking — Project Progress

> Last updated: 2026-04-15

---

## Phase Overview

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 1 | Core scaffold | ✅ Complete |
| Phase 2 | Entry modes (Manual, Food DB Picker, ItemActionSheet) | ✅ Complete |
| Phase 3 | Barcode + OpenFoodFacts | ✅ Complete |
| Phase 4 | AI entry (Gemini) | ✅ Complete |
| Phase 5 | Weight log + MPAndroidChart | ✅ Complete |
| Phase 6 | CSV import/export | ✅ Complete |
| Phase 7 | Polish & edge cases | ⏳ Not Started |

---

## Phase 1 — Core Scaffold ✅

All items previously completed.

---

## Phase 2 — Entry Modes ✅

### Food Database
- [x] `FoodDatabaseFragment.kt` — full search, RecyclerView, picker mode, title update
- [x] `FoodDatabaseViewModel.kt` — full implementation with debounced search
- [x] `item_food.xml`

### Bottom Sheet (Add Entry)
- [x] `AddEntryBottomSheet.kt` — ViewPager2 + TabLayout with 3 tabs
- [x] `BarcodeEntryFragment.kt` — ZXing camera + permission handling
- [x] `ManualEntryFragment.kt` — manual entry form + save (insert/update)
- [x] `AiEntryFragment.kt` — Gemini AI prompt + confirmation form
- [x] `fragment_add_entry_bottom_sheet.xml` — ViewPager2 + TabLayout
- [x] `fragment_manual_entry.xml`
- [x] `fragment_barcode_entry.xml`
- [x] `fragment_ai_entry.xml`

### Shared
- [x] `ItemActionSheet.kt` — amount input, live macro preview, Add/Save, edit pencil
- [x] `fragment_item_action_sheet.xml` — with edit pencil icon

### Navigation
- [x] `nav_graph.xml` — `editFoodId` arg on addEntryBottomSheet; action addEntryBottomSheet→itemActionSheet; action itemActionSheet→addEntryBottomSheet

### Bug Fixes
- [x] `DiaryFragment.kt` — refreshGoals() on onResume
- [x] `FoodDatabaseFragment.kt` — toolbar title "Add Food to Diary" in picker mode

---

## Phase 3 — Barcode + OpenFoodFacts ✅

- [x] `FoodRepository.kt` — `fetchByBarcode(barcode)` with local cache check → OpenFoodFacts fallback
- [x] `FoodItemDao.kt` — `getFoodItemByName()` added
- [x] Camera permission handling in `BarcodeEntryFragment`
- [x] `BarcodeEntryFragment.kt` — full ZXing embedded scanner integration

---

## Phase 4 — AI Entry (Gemini) ✅

- [x] `AiEntryFragment.kt` — full Gemini API integration, JSON parsing, confirmation form
- [x] Settings API key Test button wired up (`SettingsViewModel.testApiKey()`)
- [x] `SettingsViewModel.kt` — GeminiApi injected + testApiKey()
- [x] JSON extraction handles markdown fences

---

## Phase 5 — Weight Log ✅

- [x] `WeightFragment.kt` — MPAndroidChart LineChart + ChipGroup time range + stats
- [x] `WeightViewModel.kt` — `TimeRange` enum + filtering logic
- [x] `fragment_weight.xml` — full layout with chart, chips (1W/1M/3M/1Y/All), stats
- [x] `AddWeightDialogFragment.kt` — date picker via MaterialDatePicker
- [x] `dialog_add_weight.xml` — date field added
- [x] `WeightRepository.kt` — `getAllWeightEntriesOnce()` exposed, `getEntryForDate()`

---

## Phase 6 — CSV Import/Export ✅

- [x] `CsvExporter.kt` — BufferedWriter + ShareCompat intent + FileProvider
- [x] `CsvImporter.kt` — BufferedReader + header detection + diary/weight upsert
- [x] `SettingsFragment.kt` — Export Diary, Export Weight, Import CSV buttons wired
- [x] `SettingsViewModel.kt` — getAllDiaryEntries(), getAllWeightEntries() for export
- [x] `AndroidManifest.xml` — FileProvider declared
- [x] `res/xml/file_paths.xml` — cache-path for CSV exports
- [x] `DiaryEntryDao.kt` — `getAllEntriesWithFood()`, `getEntryForDateAndFood()` added
- [x] `WeightEntryDao.kt` — `getEntryForDate()` added
- [x] `DiaryRepository.kt` — `getAllEntriesWithFood()`, `getEntryForDateAndFood()` exposed

---

## Phase 7 — Polish

- [ ] Empty states on Diary, Food DB, Weight screens
- [ ] Offline/network error handling in repositories
- [ ] Over-goal color behavior on progress bars
- [ ] ProGuard/R8 rules (ZXing, Retrofit, Moshi)
- [ ] Room migration strategy
- [ ] Final QA pass

---

## Notes / Decisions

- Architecture: MVVM + Repository, single-Activity with Navigation Component
- All network calls wrapped in `NetworkResult<T>` sealed class
- Nutrition scaling done in ViewModel only; DAOs return raw data
- Barcode tab in `AddEntryBottomSheet` disables ViewPager2 swiping while camera is active
- Unit conversion (lb↔kg) bulk-converts all `WeightEntry` rows via `WeightRepository.convertAllEntries()`
- AI model: Google Gemini Flash (`generativelanguage.googleapis.com`) — free tier
- `ItemActionSheet` edit pencil: navigates to `AddEntryBottomSheet` (edit mode); on return, `onStart` reloads food info
- CSV importer auto-detects diary vs weight from header columns
