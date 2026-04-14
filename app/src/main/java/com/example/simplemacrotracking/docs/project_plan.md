# Simple Macro Tracking — Android App Project Plan
> Language: Kotlin | Min SDK: API 26 (Android 8.0) | Build: Gradle (Kotlin DSL)

---

## Tech Stack

| Layer | Library / Tool |
|---|---|
| Language | Kotlin |
| Architecture | MVVM + Repository pattern |
| Local database | Room (with KSP annotation processor) |
| Reactive state | Kotlin Flow + StateFlow + ViewModel |
| Dependency injection | Hilt |
| Networking | Retrofit 2 + Moshi (or Gson) |
| Barcode scanning | ZXing Android Embedded (`journeyapps/zxing-android-embedded`) |
| Weight graph | MPAndroidChart |
| CSV import/export | Hand-rolled (`BufferedReader` / `BufferedWriter` — no external library) |
| Coroutines | Kotlin Coroutines (`kotlinx-coroutines-android`) |
| Navigation | Navigation Component (single-Activity, Fragment-based) |
| UI | View system (XML layouts) |

No Firebase or cloud sync — fully local-first.

---

## Data Model (Room Entities)

### `FoodItem`
Reusable catalog entry. Nutrition values are stored **per base amount**.

```kotlin
@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,              // null for manual/AI entries
    val baseAmount: Float,                    // e.g. 100
    val measurementType: String,              // "g", "oz", or custom e.g. "taco"
    val calories: Float,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val source: FoodSource = FoodSource.MANUAL
)

enum class FoodSource { MANUAL, BARCODE, AI }
```

### `DiaryEntry`
Links a `FoodItem` to a specific date with the user's actual measured amount. Nutrition is **calculated at query time** via scaling, so editing a `FoodItem` retroactively updates history.

```kotlin
@Entity(
    tableName = "diary_entries",
    foreignKeys = [ForeignKey(
        entity = FoodItem::class,
        parentColumns = ["id"],
        childColumns = ["foodItemId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,                     // stored as ISO string via TypeConverter
    val foodItemId: Long,
    val actualAmount: Float,
    val measurementType: String              // copied from FoodItem at save time
)
```

**Scaling formula** (applied in ViewModel **only** — DAOs return raw data; scaling is business logic):
```
scale = actualAmount / item.baseAmount
calories = item.calories * scale
protein  = item.proteinG * scale
carbs    = item.carbsG   * scale
fat      = item.fatG     * scale
```

### Room primer (new to Kotlin?)
**Room** is Android's database library built on top of SQLite (the database that lives on the user's phone). Instead of writing raw SQL by hand, you annotate regular Kotlin data classes and Room generates all the repetitive database plumbing for you at compile time.

- **`@Entity`** — marks a data class as a database *table*. Each property = one column.
- **`@Dao`** (Data Access Object) — an interface where you declare queries as Kotlin functions. Room generates the actual SQL + boilerplate.
- **`@Database`** — the top-level class that ties everything together and gives you access to your DAOs.

### `DiaryEntryWithFood` (Query Result)

When showing the diary list, you need both the diary row *and* the food's name/nutrition — that means joining two tables. Room handles this via `@Embedded` + `@Relation`:

```kotlin
data class DiaryEntryWithFood(
    @Embedded val entry: DiaryEntry,    // DiaryEntry columns are read directly ("embedded")
    @Relation(
        parentColumn = "foodItemId",    // FK on DiaryEntry
        entityColumn = "id"             // PK on FoodItem
    )
    val food: FoodItem                  // Room automatically fetches and attaches the matching FoodItem
)
```

- **`@Embedded`** flattens all `DiaryEntry` columns into the query result — no nesting in SQL.
- **`@Relation`** tells Room to run a second query for the related `FoodItem` and stitch it together.
- **`@Transaction`** is required on the DAO function whenever `@Relation` is used, so both rows are read in the same database snapshot.

```kotlin
// In DiaryEntryDao
@Transaction
@Query("SELECT * FROM diary_entries WHERE date = :date")
fun getEntriesWithFoodForDate(date: String): Flow<List<DiaryEntryWithFood>>
```

The `DiaryUiState` in the ViewModel holds `List<DiaryEntryWithFood>`, so your adapter can reference `item.food.name`, `item.entry.actualAmount`, etc.

---

### `WeightEntry`

```kotlin
@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val value: Float,
    val unit: WeightUnit                    // LB or KG
)

enum class WeightUnit { LB, KG }
```

### `AppSettings`
Stored in `SharedPreferences` via a Kotlin wrapper (`SettingsPrefs.kt`). Fields: `calorieGoal`, `proteinGoal`, `carbsGoal`, `fatGoal`, `preferredWeightUnit`, `aiApiKey`.

---

## Screen Breakdown

### App Shell (all screens)
Every screen lives inside `MainActivity` with a `BottomNavigationView` at the bottom and a `NavHostFragment` filling the rest. The app bar title changes per destination. A **settings gear icon** is pinned as a `MenuItem` in the top-right of the `Toolbar` on every screen — tapping it navigates to the Settings destination regardless of which tab is active. The bottom-nav Settings tab and the toolbar gear icon both navigate to the same destination.

```
╔══════════════════════════════════╗
║  SimpleMacro                 ⚙   ║  ← Toolbar: title left, gear icon right (always)
╠══════════════════════════════════╣
║                                  ║
║                                  ║
║        [screen content]          ║
║                                  ║
║                                  ║
╠══════════════════════════════════╣
║  🏠 Diary  🍎 Foods  📊 Weight ⚙️ ║  ← BottomNavigationView (4 items)
╚══════════════════════════════════╝
```

---

### 1. Diary Screen (Home)
- Shows the **selected date** (default: today on launch; remembered for the current session) with **prev/next arrows** on either side of the date label
- **Tapping the date label** opens a `MaterialDatePicker` (single-date mode) so the user can jump to any date in any month or year
- Four **progress indicators** at the top: Calories, Protein, Carbs, Fat
    - Each displays: `consumed / goal`
    - Color interpolates red → amber → green as the user approaches their goal (see color logic below)
    - Exceeding goal transitions back toward red
- `RecyclerView` of `DiaryEntry` items for the day, each showing food name, amount, and macro summary
    - **Tap** an entry to re-open the Item Action Sheet to edit the logged amount
    - **Long-press** an entry shows a confirmation dialog: *"Remove [food name] from diary?"* with **Cancel** / **Delete** buttons
- **FAB navigates to the Food Database in Picker Mode** — the user searches for or browses existing foods, taps one to set the amount and log it, or taps the in-screen FAB to create a brand new food via Barcode / Manual / AI

```
╔══════════════════════════════════╗
║  SimpleMacro                 ⚙   ║
╠══════════════════════════════════╣
║  ◀      Mon, Apr 13, 2026  ▶    ║  ← tap label → MaterialDatePicker
║  ────────────────────────────── ║
║  Calories              1450/2000 ║
║  ████████████████░░░░░░░   73%  ║  ← green (near goal)
║  Protein                82/150g ║
║  ███████████░░░░░░░░░░░░   55%  ║  ← amber
║  Carbs                 120/200g ║
║  ████████████████░░░░░░░   60%  ║  ← amber
║  Fat                    44/ 65g ║
║  █████████████████░░░░░░   68%  ║  ← amber/green
║  ────────────────────────────── ║
║  ┌────────────────────────────┐ ║
║  │ Greek Yogurt               │ ║
║  │ 200 g · Cal 118 · P 10g    │ ║  ← tap to edit amount, long-press delete
║  └────────────────────────────┘ ║
║  ┌────────────────────────────┐ ║
║  │ Chicken Breast             │ ║
║  │ 150 g · Cal 248 · P 46g    │ ║
║  └────────────────────────────┘ ║
║  ┌────────────────────────────┐ ║
║  │ Brown Rice                 │ ║
║  │ 100 g · Cal 216 · C 45g    │ ║
║  └────────────────────────────┘ ║
║                                  ║
║                          ⊕       ║  ← FAB → Food Database (Picker Mode)
╠══════════════════════════════════╣
║  🏠 Diary  🍎 Foods  📊 Weight ⚙️ ║
╚══════════════════════════════════╝
```

#### Date Picker (tap the date label)
Standard `MaterialDatePicker` dialog from the Material Components library:
```
╔══════════════════════════════════╗
║  Select Date              ╳      ║
║  ────────────────────────────── ║
║  ◀        April 2026       ▶    ║
║                                  ║
║   Su  Mo  Tu  We  Th  Fr  Sa    ║
║               1   2   3   4     ║
║    5   6   7   8   9  10  11    ║
║   12 [13] 14  15  16  17  18    ║  ← today highlighted/selected
║   19  20  21  22  23  24  25    ║
║   26  27  28  29  30            ║
║                                  ║
║             [ CANCEL ]  [ OK ]  ║
╚══════════════════════════════════╝
```

#### Delete Confirmation Dialog (long-press a diary entry)
```
╔══════════════════════════════════╗
║  Remove from diary?              ║
║                                  ║
║  Remove "Greek Yogurt" from      ║
║  your log for Apr 13?            ║
║                                  ║
║           [ CANCEL ]  [ DELETE ] ║
╚══════════════════════════════════╝
```

#### Progress Bar Color Logic
```kotlin
fun getRatioColor(ratio: Float): Int {
    val clamped = ratio.coerceIn(0f, 2f)
    return when {
        clamped <= 1f -> {
            // Red (0%) → Amber (70%) → Green (100%)
            val hue = clamped * 120f           // 0 = red, 120 = green
            Color.HSVToColor(floatArrayOf(hue, 0.75f, 0.85f))
        }
        else -> {
            // Over goal: green fades back to red as ratio approaches 2x
            val hue = (2f - clamped) * 120f
            Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.80f))
        }
    }
}
```

---

### 2. Food Database Screen
The Food Database serves two purposes depending on how it was opened:

- **Standalone mode** (bottom-nav tap): browse/manage the full food catalog.
- **Picker Mode** (Diary screen FAB): toolbar title changes to *"Add Food to Diary"*; selecting a food logs it to the currently selected diary date and navigates back.

In both modes:
- `RecyclerView` of all saved `FoodItem` records with a real-time search bar
- **Tap** a food item → **Item Action Sheet** (see wireframe below) — lets the user set the amount, preview scaled macros, and add to diary
- **Long-press** shows a delete confirmation dialog
- **FAB** opens the **Add-Entry bottom sheet** (Barcode / Manual / AI) to create a new food. After saving, the new food item lands back in the list so the user can immediately tap it to set the amount

```
╔══════════════════════════════════╗
║  Add Food to Diary           ⚙   ║  ← Picker Mode title (standalone: "Food Database")
╠══════════════════════════════════╣
║  ┌──────────────────────────┐   ║
║  │ 🔍  Search foods...      │   ║  ← real-time filter
║  └──────────────────────────┘   ║
║  ────────────────────────────── ║
║  ┌────────────────────────────┐ ║
║  │ Greek Yogurt      MANUAL   │ ║
║  │ Chobani · per 100 g        │ ║  ← tap → Item Action Sheet
║  │ Cal 59 · P 10g · C 3g     │ ║  ← long-press → delete confirm
║  └────────────────────────────┘ ║
║  ┌────────────────────────────┐ ║
║  │ Chicken Breast    BARCODE  │ ║
║  │ per 100 g                  │ ║
║  │ Cal 165 · P 31g · F 4g    │ ║
║  └────────────────────────────┘ ║
║  ┌────────────────────────────┐ ║
║  │ 2 Scrambled Eggs  AI       │ ║
║  │ per 1 serving              │ ║
║  │ Cal 180 · P 12g · F 14g   │ ║
║  └────────────────────────────┘ ║
║                                  ║
║                          ⊕       ║  ← FAB → AddEntryBottomSheet (new food)
╠══════════════════════════════════╣
║  🏠 Diary  🍎 Foods  📊 Weight ⚙️ ║
╚══════════════════════════════════╝
```

#### Item Action Sheet (tap a food item)
The amount field and live macro preview are built directly into this sheet — no separate dialog needed. The **Edit** pencil icon is an icon button in the top-right corner.

```
╔══════════════════════════════════╗
║            ────                  ║
║  Greek Yogurt                ✏   ║  ← ✏ icon → opens Manual Entry pre-filled
║  Chobani · per 100 g             ║
║  ────────────────────────────── ║
║  Amount                          ║
║  ┌──────────────────────┐  g     ║  ← EditText (numeric) + unit from FoodItem
║  │ 100                  │        ║
║  └──────────────────────┘        ║
║                                  ║
║  ┌──────────────────────────┐   ║
║  │  Calories:         59    │   ║  ← live macro preview
║  │  Protein:          10 g  │   ║  ← updates on every keystroke (TextWatcher)
║  │  Carbs:             3 g  │   ║
║  │  Fat:               0 g  │   ║
║  └──────────────────────────┘   ║
║                                  ║
║  [ CANCEL ]   [ ADD TO DIARY ]   ║  ← ADD disabled until amount > 0
╚══════════════════════════════════╝
```

#### Delete Confirmation Dialog (long-press a food item)
```
╔══════════════════════════════════╗
║  Delete food item?               ║
║                                  ║
║  "Greek Yogurt" will be removed  ║
║  from your food database. Diary  ║
║  entries using it will also be   ║
║  deleted.                        ║
║                                  ║
║           [ CANCEL ]  [ DELETE ] ║
╚══════════════════════════════════╝
```

---

### 3. Weight Log Screen
- **Add Entry** button opens a dialog for the weight value — unit is taken from Settings (no picker in the dialog)
- MPAndroidChart `LineChart` displayed below
- Time range chips: **1W / 1M / 3M / 1Y / All**
- Y-axis auto-scales to the filtered data range
- Unit toggle (lb ↔ kg) in Settings **converts and re-saves all existing `WeightEntry` records** to the new unit — `WeightRepository` exposes a `convertAllEntries(from: WeightUnit, to: WeightUnit)` suspend function that `SettingsViewModel` calls immediately after persisting the new preference. The chart and history always display in the currently selected unit with no on-the-fly math needed.

```
╔══════════════════════════════════╗
║  Weight Log              [+ Add] ║  ← Add button top-right
╠══════════════════════════════════╣
║  [ 1W ][ 1M ][·3M·][ 1Y ][ All ]║  ← time range chips (3M selected)
║  ────────────────────────────── ║
║  lb                              ║
║  200 ┤                           ║
║  195 ┤      ╭──╮                 ║
║  190 ┤ ╭────╯  ╰──╮              ║
║  185 ┤─╯           ╰──╮          ║
║  180 ┤                 ╰──       ║
║      └──────────────────────── ▶ ║
║        Jan      Feb      Mar     ║
║                                  ║
║  ────────────────────────────── ║
║  Latest:   183.5 lb  (Apr 13)    ║
║  Change:  ↓ −6.5 lb in 3 months ║
║                                  ║
╠══════════════════════════════════╣
║  🏠 Diary  🍎 Foods  📊 Weight ⚙️ ║
╚══════════════════════════════════╝
```

#### Add Weight Entry Dialog
The unit (lb or kg) is read directly from `SettingsPrefs` — no dropdown needed here.
```
╔══════════════════════════════════╗
║  Add Weight Entry                ║
║  ────────────────────────────── ║
║  Weight (lb)                     ║  ← label reflects unit from Settings
║  ┌──────────────────────────┐   ║
║  │ 183.5                    │   ║  ← numeric input only, no unit picker
║  └──────────────────────────┘   ║
║                                  ║
║  Date                            ║
║  ┌──────────────────────────┐   ║
║  │ Apr 13, 2026          📅 │   ║  ← tappable, defaults to today
║  └──────────────────────────┘   ║
║                                  ║
║  [ CANCEL ]            [ SAVE ]  ║
╚══════════════════════════════════╝
```

---

### 4. Settings Screen
- Numeric goal inputs: Calories / Protein / Carbs / Fat (per day)
- Weight unit toggle (lb / kg) — **changing this triggers a one-time bulk conversion of all stored `WeightEntry` rows** via `WeightRepository.convertAllEntries()`; a brief loading indicator is shown while the conversion runs
- AI API key field (masked input, with a **Test** button that fires a minimal API call)
- **Export** and **Import** buttons (see CSV section)

```
╔══════════════════════════════════╗
║  Settings                        ║
╠══════════════════════════════════╣
║  Daily Goals                     ║
║  ────────────────────────────── ║
║  Calories                        ║
║  ┌──────────────────────────┐   ║
║  │ 2000                     │   ║
║  └──────────────────────────┘   ║
║                                  ║
║  Protein (g)      Carbs (g)      ║
║  ┌────────────┐  ┌────────────┐  ║
║  │ 150        │  │ 200        │  ║
║  └────────────┘  └────────────┘  ║
║                                  ║
║  Fat (g)                         ║
║  ┌────────────┐                  ║
║  │ 65         │                  ║
║  └────────────┘                  ║
║  ────────────────────────────── ║
║  Weight Unit                     ║
║  ◉ lb   ○ kg                     ║
║  ────────────────────────────── ║
║  AI API Key                      ║
║  ┌──────────────────────┐        ║
║  │ ●●●●●●●●●●●●●●●●●●  │[Test]  ║  ← masked; Test fires a minimal call
║  └──────────────────────┘        ║
║  ────────────────────────────── ║
║  Data                            ║
║  [ Export Diary CSV    ]         ║
║  [ Export Weight CSV   ]         ║
║  [ Import CSV          ]         ║
╠══════════════════════════════════╣
║  🏠 Diary  🍎 Foods  📊 Weight ⚙️ ║
╚══════════════════════════════════╝
```

---

## Navigation Graph

Single `nav_graph.xml` used with `NavHostFragment` inside `MainActivity`. The bottom navigation bar drives the four top-level destinations. Entry-mode fragments are **not** nav destinations — they live inside a `ViewPager2` managed by `AddEntryBottomSheet`.

```
nav_graph.xml
│
├── DiaryFragment               ← startDestination (bottom-nav: Home)
│   └── action → FoodDatabaseFragment  (arg: pickerMode=true, targetDate: String)
│
├── FoodDatabaseFragment        ← bottom-nav (also reachable from Diary FAB)
│   ├── action → AddEntryBottomSheet   [<dialog>]  (FAB — create new food)
│   └── action → ItemActionSheet       [<dialog>]  (tap a food item)
│
├── WeightFragment              ← bottom-nav
│   └── action → AddWeightDialogFragment [<dialog>]
│
└── SettingsFragment            ← bottom-nav (also reachable from toolbar ⚙ icon)

─── Shared <dialog> destinations ───────────────────────────────
AddEntryBottomSheet             ← BottomSheetDialogFragment
    (hosts ViewPager2 with 3 tab Fragments — NOT nav destinations)
    ├── BarcodeEntryFragment      (tab 0, managed by ViewPager2)
    ├── ManualEntryFragment       (tab 1, managed by ViewPager2)
    └── AiEntryFragment           (tab 2, managed by ViewPager2)
    After a food is created → navigates to ItemActionSheet

ItemActionSheet                 ← BottomSheetDialogFragment
    args: foodItemId: Long, targetDate: String (ISO)
    (amount input + live macro preview + Add to Diary / Cancel)
    On save → setFragmentResult → caller pops back to DiaryFragment

AddWeightDialogFragment         ← DialogFragment
```

**Key rules:**
- The Diary FAB no longer opens `AddEntryBottomSheet` directly. It navigates to `FoodDatabaseFragment` with `pickerMode=true` so the user always picks or creates a food before logging an amount.
- `BarcodeEntryFragment`, `ManualEntryFragment`, and `AiEntryFragment` are managed entirely by `ViewPager2` inside `AddEntryBottomSheet`. After creating a food they fire `setFragmentResult` → `AddEntryBottomSheet` navigates to `ItemActionSheet`.
- `ItemActionSheet` is the single shared component for setting amount + previewing macros. It replaces the old `QuantityDialogFragment`.

---

## Entry Modes

### Add-Entry Bottom Sheet Architecture

**Chosen approach: Option A — single `BottomSheetDialogFragment` with `ViewPager2` + `TabLayout`** (standard Material Design pattern, fewer taps, swipeable between modes).

```
╔══════════════════════════════════╗
║            ────                  ║  ← drag handle (peek / full expand)
║  ┌─────────┬──────────┬────────┐ ║
║  │ BARCODE │  MANUAL  │   AI   │ ║  ← TabLayout (3 tabs)
║  └─────────┴──────────┴────────┘ ║
║  ────────────────────────────────║
║                                  ║
║   [ViewPager2 content area]      ║
║                                  ║
║   Tab 0 – Barcode:               ║
║     Full ZXing camera preview    ║
║     Auto-advances on scan        ║
║                                  ║
║   Tab 1 – Manual:                ║
║     Scrollable entry form        ║
║     [Save] button at bottom      ║
║                                  ║
║   Tab 2 – AI:                    ║
║     Multi-line text prompt       ║
║     [Estimate] button            ║
║     Spinner/loading indicator    ║
║                                  ║
╚══════════════════════════════════╝
```

**Why not Option B** (picker sheet → separate dedicated sheets per mode): it requires an extra tap and makes the camera tab feel disconnected from the other two. Option A lets the user swipe between modes and is the standard pattern seen in apps like MyFitnessPal.

The `ViewPager2` uses a `FragmentStateAdapter`. Swiping is **disabled** on the Barcode tab while the camera is active (to avoid accidental dismissal mid-scan). Each tab fragment sends its result back to `AddEntryBottomSheet` via `setFragmentResult`, which then navigates to `QuantityDialogFragment`.

---

### Barcode Scanner
1. ZXing captures a barcode string from the camera
2. Check local `FoodItem` table for a matching barcode — use cached result if found
3. If not cached, fire a Retrofit call:
   ```
   GET https://world.openfoodfacts.org/api/v0/product/{barcode}.json
   ```
4. Parse `product.nutriments`:
    - `energy-kcal_100g` → calories
    - `proteins_100g` → protein
    - `carbohydrates_100g` → carbs
    - `fat_100g` → fat
5. Default base amount: **100 g**
6. Save as `FoodItem` with `source = BARCODE`
7. Open **quantity dialog** for the user to enter their actual amount

```
╔══════════════════════════════════╗
║            ────                  ║
║  ┌─────────┬──────────┬────────┐ ║
║  │ BARCODE │  MANUAL  │   AI   │ ║
║  └─────────┴──────────┴────────┘ ║
║  ────────────────────────────── ║
║                                  ║
║  ┌──────────────────────────┐   ║
║  │                          │   ║
║  │   ┌──────────────────┐   │   ║
║  │   │                  │   │   ║
║  │   │  aim barcode at  │   │   ║  ← ZXing camera preview
║  │   │    this box      │   │   ║
║  │   └──────────────────┘   │   ║
║  │                          │   ║
║  └──────────────────────────┘   ║
║                                  ║
║  Point camera at a barcode to    ║
║  scan automatically              ║
║                                  ║
╚══════════════════════════════════╝
```

### Manual Entry
Form fields:
- Name (required)
- Brand (optional)
- Base amount (number field, required)
- Measurement type — `Spinner` pre-populated with `g` and `oz`, but accepts typed custom values (e.g. "taco", "slice", "scoop")
- Calories, Protein (g), Carbs (g), Fat (g) — all per base amount

```
╔══════════════════════════════════╗
║            ────                  ║
║  ┌─────────┬──────────┬────────┐ ║
║  │ BARCODE │  MANUAL  │   AI   │ ║
║  └─────────┴──────────┴────────┘ ║
║  ────────────────────────────── ║
║  Name *                          ║
║  ┌──────────────────────────┐   ║
║  │ e.g. Greek Yogurt        │   ║
║  └──────────────────────────┘   ║
║  Brand (optional)                ║
║  ┌──────────────────────────┐   ║
║  │ e.g. Chobani             │   ║
║  └──────────────────────────┘   ║
║  Base Amount *    Unit *         ║
║  ┌────────────┐  ┌────────────┐  ║
║  │ 100        │  │ g        ▼ │  ║  ← spinner: g / oz / custom typed
║  └────────────┘  └────────────┘  ║
║  Calories *       Protein (g) *  ║
║  ┌────────────┐  ┌────────────┐  ║
║  │            │  │            │  ║
║  └────────────┘  └────────────┘  ║
║  Carbs (g) *      Fat (g) *      ║
║  ┌────────────┐  ┌────────────┐  ║
║  │            │  │            │  ║
║  └────────────┘  └────────────┘  ║
║                                  ║
║           [      SAVE     ]      ║
╚══════════════════════════════════╝
```

### AI Estimate
- Only available if an API key is set in Settings; otherwise shows a deep-link prompt
- Recommended free model: **Google Gemini Flash** (`generativelanguage.googleapis.com`) — generous free tier, no credit card required
- User types a plain-English description, e.g.:
  > "2 scrambled eggs with butter and a cup of OJ"
- System prompt instructs the model to respond **only** with JSON:
  ```json
  {
    "name": "Scrambled eggs with OJ",
    "calories": 380,
    "protein_g": 15,
    "carbs_g": 28,
    "fat_g": 18,
    "amount": 1,
    "unit": "serving"
  }
  ```
- Response is parsed and pre-fills a **confirmation dialog** the user can edit before saving
- Wrap JSON extraction in a try/catch — models occasionally wrap output in markdown fences even when instructed otherwise

```
╔══════════════════════════════════╗
║            ────                  ║
║  ┌─────────┬──────────┬────────┐ ║
║  │ BARCODE │  MANUAL  │   AI   │ ║
║  └─────────┴──────────┴────────┘ ║
║  ────────────────────────────── ║
║  Describe what you ate:          ║
║  ┌──────────────────────────┐   ║
║  │ 2 scrambled eggs with    │   ║
║  │ butter and a cup of OJ   │   ║
║  │                          │   ║
║  └──────────────────────────┘   ║
║                                  ║
║        [    ESTIMATE    ]        ║
║                                  ║
║  ┄ while waiting ┄┄┄┄┄┄┄┄┄┄┄┄  ║
║  ◌  Estimating nutrition...      ║  ← progress indicator
║                                  ║
╚══════════════════════════════════╝
```

> **No API key set** state (replaces content area above):
> ```
> ╔══════════════════════════════════╗
> ║  ┌─────────┬──────────┬────────┐ ║
> ║  │ BARCODE │  MANUAL  │   AI   │ ║
> ║  └─────────┴──────────┴────────┘ ║
> ║  ────────────────────────────── ║
> ║                                  ║
> ║  AI estimation requires an API   ║
> ║  key. Add one in Settings.       ║
> ║                                  ║
> ║        [ GO TO SETTINGS ]        ║
> ║                                  ║
> ╚══════════════════════════════════╝
> ```

---

## ItemActionSheet Spec

`ItemActionSheet` is a **`BottomSheetDialogFragment`** that replaces the old `QuantityDialogFragment`. It is the single shared component for setting an amount and logging a food — used by:
- Food Database item tap (both standalone and picker mode)
- After creating a food via Barcode / Manual / AI in `AddEntryBottomSheet`
- Diary entry tap (re-opens with the previously logged amount pre-filled for editing)

### Layout
```
╔══════════════════════════════════╗
║            ────                  ║
║  Greek Yogurt                ✏   ║  ← ✏ navigates to Manual Entry pre-filled
║  Chobani · per 100 g             ║  ← food info (read-only)
║  ────────────────────────────── ║
║  Amount                          ║
║  ┌──────────────────────┐  g     ║  ← EditText (numeric) + unit from FoodItem
║  │ 200                  │        ║
║  └──────────────────────┘        ║
║                                  ║
║  ┌──────────────────────────┐   ║
║  │  Calories:        118    │   ║  ← live macro preview
║  │  Protein:          10 g  │   ║  ← TextWatcher recomputes on every keystroke
║  │  Carbs:             8 g  │   ║
║  │  Fat:               3 g  │   ║
║  └──────────────────────────┘   ║
║                                  ║
║  [ CANCEL ]   [ ADD TO DIARY ]   ║  ← button label is "SAVE CHANGES" when editing
╚══════════════════════════════════╝
```

### Behaviour
- Receives `foodItemId: Long` and `targetDate: String` as arguments (passed by the caller)
- Loads the `FoodItem` from the repository on open to display name/brand/base amount
- A `TextWatcher` on the amount field recomputes the scaled macros in real-time
- When opened to **edit** an existing diary entry, the amount field is pre-filled with the previously logged value and the button reads **"Save Changes"**
- **Add to Diary / Save Changes** button is disabled (greyed out) until the amount field contains a valid positive number
- On confirm: inserts or updates the `DiaryEntry` in the DB, then calls `setFragmentResult("item_action_result", bundle)` so the caller can react (e.g. navigate back to Diary, dismiss `AddEntryBottomSheet`)
- On cancel: simply dismiss — no DB write

---

## CSV Import / Export

Accessible from the Settings screen. Export uses Android's `ShareCompat` intent (writes to the app's cache dir first). No external library — uses standard Java `BufferedWriter` (export) and `BufferedReader` (import).

**Export** — iterate the list, write each field separated by commas; wrap any field containing a comma or quote in double-quotes and escape inner quotes as `""`.

**Import** — read line by line with `BufferedReader.readLine()`; split on commas with a simple state-machine parser that respects quoted fields.

### Diary export
```
date,food_name,amount,unit,calories,protein_g,carbs_g,fat_g
2025-04-10,Greek yogurt,200,g,118,10,8,3
```

### Weight export
```
date,weight,unit
2025-04-10,185.5,LB
```

### Import behaviour
- Validate column headers on first row; reject file if missing required columns
- Upsert strategy: `(date + food_name)` as natural key for diary; `date` for weight
- Bad/malformed rows are skipped
- Show a summary Snackbar: `"Imported 42 entries, 2 rows skipped"`

---

## Project Structure

```
app/
├── SimpleMacroApp.kt            ← @HiltAndroidApp Application class (required by Hilt)
├── MainActivity.kt
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   │   ├── FoodItemDao.kt
│   │   │   ├── DiaryEntryDao.kt
│   │   │   └── WeightEntryDao.kt
│   │   └── converters/
│   │       └── LocalDateConverter.kt
│   ├── model/
│   │   ├── FoodItem.kt
│   │   ├── DiaryEntry.kt
│   │   ├── WeightEntry.kt
│   │   └── enums/
│   │       ├── FoodSource.kt
│   │       └── WeightUnit.kt
│   ├── network/
│   │   ├── OpenFoodFactsApi.kt
│   │   ├── GeminiApi.kt
│   │   └── dto/
│   │       ├── OpenFoodResponse.kt
│   │       └── GeminiResponse.kt
│   ├── repository/
│   │   ├── FoodRepository.kt
│   │   ├── DiaryRepository.kt
│   │   └── WeightRepository.kt
│   └── prefs/
│       └── SettingsPrefs.kt
├── di/
│   └── AppModule.kt             ← Hilt module (see AppModule spec below)
├── ui/
│   ├── diary/
│   │   ├── DiaryFragment.kt
│   │   ├── DiaryViewModel.kt
│   │   └── DiaryAdapter.kt
│   ├── fooddb/
│   │   ├── FoodDatabaseFragment.kt
│   │   └── FoodDatabaseViewModel.kt
│   ├── weight/
│   │   ├── WeightFragment.kt
│   │   └── WeightViewModel.kt
│   ├── settings/
│   │   ├── SettingsFragment.kt
│   │   └── SettingsViewModel.kt
│   ├── entry/
│   │   ├── AddEntryBottomSheet.kt   ← BottomSheetDialogFragment hosting ViewPager2
│   │   ├── BarcodeEntryFragment.kt
│   │   ├── ManualEntryFragment.kt
│   │   └── AiEntryFragment.kt
│   └── shared/
│       ├── ItemActionSheet.kt       ← BottomSheetDialogFragment: amount input + macro preview
│       ├── AddWeightDialogFragment.kt
│       └── MacroProgressView.kt     ← custom view for color-interpolated bar
├── util/
│   ├── ColorUtil.kt
│   ├── CsvExporter.kt
│   ├── CsvImporter.kt
│   └── NetworkResult.kt         ← sealed class for wrapping network responses
```

---

## AppModule (Hilt DI)

`AppModule.kt` is annotated with `@Module` + `@InstallIn(SingletonComponent::class)` and provides all app-wide singletons.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Database ──────────────────────────────────────────────────
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "macro_db").build()

    @Provides fun provideFoodItemDao(db: AppDatabase) = db.foodItemDao()
    @Provides fun provideDiaryEntryDao(db: AppDatabase) = db.diaryEntryDao()
    @Provides fun provideWeightEntryDao(db: AppDatabase) = db.weightEntryDao()

    // ── Network (two separate base URLs, distinguished by @Named) ─
    @Provides @Singleton @Named("openfoodfacts")
    fun provideOpenFoodFactsRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl("https://world.openfoodfacts.org/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    @Provides @Singleton @Named("gemini")
    fun provideGeminiRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideOpenFoodFactsApi(@Named("openfoodfacts") r: Retrofit): OpenFoodFactsApi =
        r.create(OpenFoodFactsApi::class.java)

    @Provides @Singleton
    fun provideGeminiApi(@Named("gemini") r: Retrofit): GeminiApi =
        r.create(GeminiApi::class.java)

    // ── Prefs ──────────────────────────────────────────────────────
    @Provides @Singleton
    fun provideSettingsPrefs(@ApplicationContext ctx: Context) = SettingsPrefs(ctx)
}
```

---

## Kotlin-Specific Patterns to Use

**Coroutines in ViewModels** — all DB and network calls run in `viewModelScope`:
```kotlin
fun loadDiaryEntries(date: LocalDate) {
    viewModelScope.launch {
        val entries = diaryRepository.getEntriesForDate(date)
        _uiState.update { it.copy(entries = entries) }
    }
}
```

**Flow for live DB updates** — Room returns `Flow<List<T>>` which the Fragment collects:
```kotlin
// DAO
@Query("SELECT * FROM diary_entries WHERE date = :date")
fun getEntriesForDate(date: String): Flow<List<DiaryEntry>>

// Fragment
viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.entries.collect { entries -> adapter.submitList(entries) }
    }
}
```

**Data classes for UI state** — use a sealed `UiState` or a single data class with `StateFlow`:
```kotlin
data class DiaryUiState(
    val date: LocalDate = LocalDate.now(),
    val entries: List<DiaryEntryWithFood> = emptyList(),
    val consumed: Macros = Macros(),
    val goals: Macros = Macros(),
    val isLoading: Boolean = false
)
```

**Extension functions** for measurement conversion:
```kotlin
fun Float.kgToLb(): Float = this * 2.20462f
fun Float.lbToKg(): Float = this / 2.20462f
```

**Network Result wrapper** — all repository functions that touch the network return a `NetworkResult<T>` sealed class, defined from day one so error handling is consistent across phases:
```kotlin
// util/NetworkResult.kt
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()
}

// Usage in a repository
suspend fun fetchByBarcode(barcode: String): NetworkResult<FoodItem> = try {
    val response = openFoodFactsApi.getProduct(barcode)
    if (response.status == 1) NetworkResult.Success(response.toFoodItem())
    else NetworkResult.Error("Product not found")
} catch (e: IOException) {
    NetworkResult.Error("No internet connection")
} catch (e: HttpException) {
    NetworkResult.Error("Server error", e.code())
}

// Usage in a ViewModel
fun lookupBarcode(barcode: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        when (val result = foodRepository.fetchByBarcode(barcode)) {
            is NetworkResult.Success -> { /* navigate to quantity dialog */ }
            is NetworkResult.Error   -> _uiState.update { it.copy(error = result.message, isLoading = false) }
            is NetworkResult.Loading -> Unit
        }
    }
}
```

---

## Build Phases

### Phase 1 — Core scaffold
- Gradle setup with Kotlin DSL (`build.gradle.kts`)
- Room schema: all entities + DAOs + TypeConverters
- Hilt setup (`AppModule` providing DB and Retrofit instances)
- Navigation graph (four bottom-nav destinations)
- Settings screen saving goals
- Diary screen with hardcoded data and color-interpolating progress bars

### Phase 2 — Entry modes
- Food Database picker mode (launched from Diary FAB, passes `targetDate` arg)
- `AddEntryBottomSheet` launched from Food Database FAB
- Manual entry form with measurement spinner + custom type input
- `ItemActionSheet` with live nutrition scaling preview
- Food database screen standalone mode with search

### Phase 3 — Barcode + OpenFoodFacts
- ZXing integration + camera permission handling
- Retrofit client for OpenFoodFacts
- Barcode cache check before network call
- Response parsing → `FoodItem` → quantity dialog

### Phase 4 — AI entry
- Settings API key field + test button
- Gemini Retrofit client
- Prompt construction + JSON response parsing
- Confirmation/edit dialog before saving

### Phase 5 — Weight log
- `WeightEntry` entity + DAO
- `WeightRepository.convertAllEntries(from, to)` for unit migration
- Add entry dialog (unit pulled from `SettingsPrefs`)
- `SettingsViewModel` triggers `convertAllEntries` on unit toggle
- MPAndroidChart `LineChart` with time range filtering

### Phase 6 — CSV import/export
- `CsvExporter` and `CsvImporter` hand-rolled with `BufferedWriter` / `BufferedReader` (standard Java stdlib, no extra dependency)
- `ShareCompat` intent for export
- File picker for import (`ActivityResultContracts.GetContent`)
- Header validation + upsert logic + summary Snackbar

### Phase 7 — Polish
- Empty states for all screens
- Offline handling (Retrofit error → cached result or user-friendly message)
- Over-goal colour behaviour on progress bars
- ProGuard/R8 rules for ZXing, Retrofit, Moshi
- Room migration strategy (version `autoMigrations` or manual)

---

## Key Dependencies (`libs.versions.toml` / `build.gradle.kts`)

> **JitPack repo required** for MPAndroidChart. Add to `settings.gradle.kts`:
> ```kotlin
> dependencyResolutionManagement {
>     repositories {
>         google(); mavenCentral()
>         maven { url = uri("https://jitpack.io") }   // ← required for MPAndroidChart
>     }
> }
> ```

> **ViewBinding** — enable in the `android {}` block of `app/build.gradle.kts`:
> ```kotlin
> buildFeatures { viewBinding = true }
> ```

```kotlin
// Kotlin + KSP (annotation processor used by Room and Hilt)
kotlin("android") version "2.1.20"
id("com.google.devtools.ksp") version "2.1.20-1.0.31"

// Room
implementation("androidx.room:room-runtime:2.7.0")
implementation("androidx.room:room-ktx:2.7.0")
ksp("androidx.room:room-compiler:2.7.0")

// Hilt
implementation("com.google.dagger:hilt-android:2.56.1")
ksp("com.google.dagger:hilt-compiler:2.56.1")

// Retrofit + Moshi
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")   // ← KSP codegen required for @JsonClass

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

// Navigation
implementation("androidx.navigation:navigation-fragment-ktx:2.8.9")
implementation("androidx.navigation:navigation-ui-ktx:2.8.9")

// ZXing (barcode)
implementation("com.journeyapps:zxing-android-embedded:4.3.0")

// MPAndroidChart (served from JitPack — see repo note above)
implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

// CSV — no external library; hand-rolled with standard Java BufferedReader/BufferedWriter

// ViewModel + Lifecycle
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
```