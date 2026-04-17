package com.example.simplemacrotracking.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplemacrotracking.data.model.DiaryEntry
import com.example.simplemacrotracking.data.model.DiaryEntryWithFood
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import com.example.simplemacrotracking.data.repository.DiaryRepository
import com.example.simplemacrotracking.data.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class Macros(
    val calories: Float = 0f,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f
)

sealed class VoiceResult {
    object Idle : VoiceResult()
    data class Success(val foodName: String, val amount: Float, val unit: String) : VoiceResult()
    data class NoMatch(val reason: String) : VoiceResult()
}

data class DiaryUiState(
    val date: LocalDate = LocalDate.now(),
    val entries: List<DiaryEntryWithFood> = emptyList(),
    val consumed: Macros = Macros(),
    val goals: Macros = Macros(),
    val isLoading: Boolean = false,
    val voiceResult: VoiceResult = VoiceResult.Idle
)

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val foodRepository: FoodRepository,
    private val settingsPrefs: SettingsPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())

    init {
        loadGoals()
        observeEntries()
    }

    private fun loadGoals() {
        _uiState.update {
            it.copy(
                goals = Macros(
                    calories = settingsPrefs.calorieGoal.toFloat(),
                    proteinG = settingsPrefs.proteinGoal.toFloat(),
                    carbsG = settingsPrefs.carbsGoal.toFloat(),
                    fatG = settingsPrefs.fatGoal.toFloat()
                )
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeEntries() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                diaryRepository.getEntriesWithFoodForDate(date)
            }.collect { entries ->
                val consumed = entries.fold(Macros()) { acc, ewf ->
                    val scale = ewf.entry.actualAmount / ewf.food.baseAmount
                    acc.copy(
                        calories = acc.calories + ewf.food.calories * scale,
                        proteinG = acc.proteinG + ewf.food.proteinG * scale,
                        carbsG = acc.carbsG + ewf.food.carbsG * scale,
                        fatG = acc.fatG + ewf.food.fatG * scale
                    )
                }
                _uiState.update {
                    it.copy(
                        date = _selectedDate.value,
                        entries = entries,
                        consumed = consumed
                    )
                }
            }
        }
    }

    fun setDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    fun deleteDiaryEntry(id: Long) {
        viewModelScope.launch {
            diaryRepository.deleteDiaryEntryById(id)
        }
    }

    fun refreshGoals() = loadGoals()

    fun clearVoiceResult() {
        _uiState.update { it.copy(voiceResult = VoiceResult.Idle) }
    }

    fun processVoiceInput(text: String) {
        viewModelScope.launch {
            val parsed = parseVoiceText(text)
            if (parsed == null) {
                _uiState.update { it.copy(voiceResult = VoiceResult.NoMatch("Couldn't understand \"$text\". Try saying e.g. \"add milk 100 grams\"")) }
                return@launch
            }
            val (foodQuery, amount, unit) = parsed

            val allFoods = foodRepository.getAllFoodItems().first()
            if (allFoods.isEmpty()) {
                _uiState.update { it.copy(voiceResult = VoiceResult.NoMatch("No foods in your database yet.")) }
                return@launch
            }

            val best = fuzzyMatch(foodQuery, allFoods)
            if (best == null) {
                _uiState.update { it.copy(voiceResult = VoiceResult.NoMatch("No match found for \"$foodQuery\" in your food database.")) }
                return@launch
            }

            val entry = DiaryEntry(
                date = _selectedDate.value,
                foodItemId = best.id,
                actualAmount = amount,
                measurementType = best.measurementType
            )
            diaryRepository.insertDiaryEntry(entry)
            _uiState.update {
                it.copy(voiceResult = VoiceResult.Success(best.name, amount, best.measurementType))
            }
        }
    }

    // Returns Triple(foodName, amount, unit) or null if unparseable
    private fun parseVoiceText(text: String): Triple<String, Float, String>? {
        // First convert any word-form numbers to digits ("hundred fifty" → "150")
        val normalized = convertWordNumbers(text.lowercase().trim())

        // Find the first number (int or decimal)
        val numberRegex = Regex("""(\d+\.?\d*)""")
        val numberMatch = numberRegex.find(normalized) ?: return null
        val amount = numberMatch.groupValues[1].toFloatOrNull() ?: return null

        // Find optional unit after the number
        val unitRegex = Regex("""${Regex.escape(numberMatch.value)}\s*(grams?|g|kilograms?|kg|ounces?|oz|milliliters?|ml|liters?|l|lbs?|pounds?|cups?|tbsp|tsp|serving|servings|pieces?|pcs?|slices?)\b""")
        val unitMatch = unitRegex.find(normalized)
        val unit = unitMatch?.groupValues?.get(1) ?: "g"

        // Remove the number + unit from text to isolate the food name
        val consumed = unitMatch?.value ?: numberMatch.value
        var foodPart = normalized.replace(consumed, " ")

        // Remove common stopwords / command words / filler
        val stopwords = setOf(
            "add", "log", "track", "record", "i", "had", "ate", "eat", "have",
            "a", "an", "the", "of", "for", "or", "with", "today", "now",
            "please", "me", "some", "and", "just", "about", "roughly"
        )
        foodPart = foodPart.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in stopwords }
            .joinToString(" ")
            .trim()

        return if (foodPart.isBlank()) null else Triple(foodPart, amount, unit)
    }

    /**
     * Converts word-form numbers in a string to digit form.
     * e.g. "hundred fifty" → "150", "two hundred grams" → "200 grams"
     */
    private fun convertWordNumbers(text: String): String {
        val ones = mapOf(
            "zero" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L,
            "five" to 5L, "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L,
            "ten" to 10L, "eleven" to 11L, "twelve" to 12L, "thirteen" to 13L,
            "fourteen" to 14L, "fifteen" to 15L, "sixteen" to 16L,
            "seventeen" to 17L, "eighteen" to 18L, "nineteen" to 19L
        )
        val tensMap = mapOf(
            "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L,
            "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L
        )
        val numWords = ones.keys + tensMap.keys + setOf("hundred", "thousand")

        val words = text.split(Regex("\\s+"))
        val result = mutableListOf<String>()
        var current = 0L
        var inNumber = false

        for (word in words) {
            val w = word.trimEnd(',', '.', '!')
            when {
                w in ones -> { current += ones[w]!!; inNumber = true }
                w in tensMap -> { current += tensMap[w]!!; inNumber = true }
                w == "hundred" -> { current = (if (current == 0L) 1L else current) * 100L; inNumber = true }
                w == "thousand" -> { current = (if (current == 0L) 1L else current) * 1000L; inNumber = true }
                else -> {
                    if (inNumber) { result.add(current.toString()); current = 0L; inNumber = false }
                    result.add(word)
                }
            }
        }
        if (inNumber) result.add(current.toString())
        return result.joinToString(" ")
    }

    private fun fuzzyMatch(query: String, foods: List<FoodItem>): FoodItem? {
        if (foods.isEmpty()) return null

        val queryNorm = query.lowercase().trim()
        val scored = foods.map { food ->
            val nameNorm = food.name.lowercase().trim()
            val score = when {
                nameNorm == queryNorm -> 0
                nameNorm.contains(queryNorm) || queryNorm.contains(nameNorm) -> 1
                else -> levenshtein(queryNorm, nameNorm)
            }
            food to score
        }
        val best = scored.minByOrNull { it.second } ?: return null

        // Only accept if edit distance is ≤ 40% of the longer string's length.
        // This prevents "egg" matching "test" (distance=3, 3/4=75% — rejected).
        val maxLen = maxOf(queryNorm.length, best.first.name.lowercase().length)
        val threshold = (maxLen * 0.40).toInt().coerceAtLeast(1)
        return if (best.second <= threshold) best.first else null
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }
}
