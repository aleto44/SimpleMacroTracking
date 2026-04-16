package com.example.simplemacrotracking.ui.entry

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.simplemacrotracking.R
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.model.enums.FoodSource
import com.example.simplemacrotracking.data.network.GeminiApi
import com.example.simplemacrotracking.data.network.dto.GeminiContent
import com.example.simplemacrotracking.data.network.dto.GeminiPart
import com.example.simplemacrotracking.data.network.dto.GeminiRequest
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import com.example.simplemacrotracking.data.repository.FoodRepository
import com.example.simplemacrotracking.databinding.FragmentAiEntryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class AiEntryFragment : Fragment() {

    private var _binding: FragmentAiEntryBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var geminiApi: GeminiApi
    @Inject lateinit var settingsPrefs: SettingsPrefs
    @Inject lateinit var foodRepository: FoodRepository

    companion object {
        fun newInstance(targetDate: String) = AiEntryFragment().apply {
            arguments = bundleOf("targetDate" to targetDate)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val apiKey = settingsPrefs.aiApiKey
        if (apiKey.isBlank()) {
            binding.layoutNoKey.visibility = View.VISIBLE
            binding.layoutPrompt.visibility = View.GONE
            binding.btnGoToSettings.setOnClickListener {
                findNavController().navigate(R.id.settingsFragment)
            }
            return
        }

        binding.btnEstimate.setOnClickListener {
            val description = binding.etDescription.text.toString().trim()
            if (description.isBlank()) return@setOnClickListener
            callGemini(description)
        }

        binding.btnSaveConfirmation.setOnClickListener { saveConfirmedFood() }
    }

    private fun callGemini(description: String) {
        val apiKey = settingsPrefs.aiApiKey
        binding.btnEstimate.isEnabled = false
        binding.layoutLoading.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val prompt = """
                Estimate the nutritional content of: "$description"
                Respond ONLY with a single JSON object (no markdown fences, no extra text):
                {"name":"...","calories":0,"protein_g":0,"carbs_g":0,"fat_g":0,"amount":0,"unit":"g"}
            """.trimIndent()

            try {
                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                )
                val response = geminiApi.generateContent(apiKey, request)
                if (response.isSuccessful) {
                    val text = response.body()?.candidates
                        ?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (text != null) {
                        Log.d("AiEntryFragment", "AI Response: $text")
                        parseAndShowConfirmation(text)
                    } else {
                        showError("Empty response from AI")
                    }
                } else {
                    showError("API error: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AiEntryFragment", "Error calling Gemini API", e)
                showError(e.message ?: "Unknown error")
            }
        }
    }

    private fun parseAndShowConfirmation(text: String) {
        try {
            val json = extractJson(text)
            Log.d("AiEntryFragment", "Extracted JSON: $json")
            val obj = JSONObject(json)
            binding.layoutLoading.visibility = View.GONE
            binding.layoutPrompt.visibility = View.GONE
            binding.layoutConfirmation.visibility = View.VISIBLE

            binding.etConfirmName.setText(obj.optString("name", "AI Estimate"))
            binding.etConfirmCalories.setText("%.0f".format(obj.optDouble("calories", 0.0)))
            binding.etConfirmProtein.setText("%.1f".format(obj.optDouble("protein_g", 0.0)))
            binding.etConfirmCarbs.setText("%.1f".format(obj.optDouble("carbs_g", 0.0)))
            binding.etConfirmFat.setText("%.1f".format(obj.optDouble("fat_g", 0.0)))
            binding.etConfirmAmount.setText("%.0f".format(obj.optDouble("amount", 1.0)))
            binding.etConfirmUnit.setText(obj.optString("unit", "serving"))
        } catch (e: Exception) {
            Log.e("AiEntryFragment", "Error parsing AI response", e)
            showError("Could not parse AI response: ${e.message}")
        }
    }

    private fun extractJson(text: String): String {
        Log.d("AiEntryFragment", "Attempting to extract JSON from text (length: ${text.length})")
        try {
            // Strip markdown fences if present
            val fencedPattern = Regex("```(?:json)?\\s*(.*)\\s*```", RegexOption.DOT_MATCHES_ALL)
            fencedPattern.find(text)?.let {
                Log.d("AiEntryFragment", "Found markdown fences")
                return it.groupValues[1].trim()
            }
            // Find raw JSON object - look for first { to matching }
            val firstBrace = text.indexOf('{')
            if (firstBrace >= 0) {
                var depth = 0
                for (i in firstBrace until text.length) {
                    when (text[i]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                val extracted = text.substring(firstBrace, i + 1)
                                Log.d("AiEntryFragment", "Extracted JSON by brace matching")
                                return extracted
                            }
                        }
                    }
                }
            }
            Log.d("AiEntryFragment", "No JSON pattern found, returning original text")
            return text
        } catch (e: Exception) {
            Log.e("AiEntryFragment", "Error in extractJson", e)
            return text
        }
    }

    private fun showError(message: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.btnEstimate.isEnabled = true
        binding.etDescription.error = message
    }

    private fun saveConfirmedFood() {
        val name = binding.etConfirmName.text.toString().trim().ifBlank { "AI Estimate" }
        val calories = binding.etConfirmCalories.text.toString().toFloatOrNull() ?: 0f
        val proteinG = binding.etConfirmProtein.text.toString().toFloatOrNull() ?: 0f
        val carbsG = binding.etConfirmCarbs.text.toString().toFloatOrNull() ?: 0f
        val fatG = binding.etConfirmFat.text.toString().toFloatOrNull() ?: 0f
        val amount = binding.etConfirmAmount.text.toString().toFloatOrNull() ?: 1f
        val unit = binding.etConfirmUnit.text.toString().trim().ifBlank { "serving" }

        viewLifecycleOwner.lifecycleScope.launch {
            val food = FoodItem(
                name = name, baseAmount = amount, measurementType = unit,
                calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
                source = FoodSource.AI
            )
            val foodId = foodRepository.saveFoodItem(food)
            parentFragmentManager.setFragmentResult(
                "food_saved",
                bundleOf("foodItemId" to foodId)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

