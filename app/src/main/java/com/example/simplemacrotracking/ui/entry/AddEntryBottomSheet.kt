package com.example.simplemacrotracking.ui.entry

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.simplemacrotracking.R
import com.example.simplemacrotracking.databinding.FragmentAddEntryBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

/**
 * Phase 2: Will host ViewPager2 with Barcode / Manual / AI tabs.
 */
@AndroidEntryPoint
class AddEntryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAddEntryBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val targetDate: String by lazy {
        arguments?.getString("targetDate")?.ifBlank { null } ?: LocalDate.now().toString()
    }
    private val editFoodId: Long by lazy {
        arguments?.getLong("editFoodId", -1L) ?: -1L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEntryBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = EntryPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "BARCODE"
                1 -> "MANUAL"
                2 -> "AI"
                else -> ""
            }
        }.attach()

        // Always default to Manual tab; stay there when editing too
        binding.viewPager.setCurrentItem(1, false)

        // Disable swipe on barcode tab to avoid accidental dismissal during camera
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.viewPager.isUserInputEnabled = position != 0
            }
        })

        // Listen for food saved from any child tab fragment
        childFragmentManager.setFragmentResultListener("food_saved", viewLifecycleOwner) { _, bundle ->
            val foodItemId = bundle.getLong("foodItemId", -1L)
            if (foodItemId < 0) return@setFragmentResultListener

            if (editFoodId > 0) {
                // Editing an existing food — pop back to ItemActionSheet
                findNavController().popBackStack()
            } else {
                // New food created — navigate to ItemActionSheet to set amount
                findNavController().navigate(
                    R.id.action_addEntryBottomSheet_to_itemActionSheet,
                    bundleOf(
                        "foodItemId" to foodItemId,
                        "targetDate" to targetDate
                    )
                )
            }
        }
    }

    inner class EntryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> BarcodeEntryFragment.newInstance(targetDate)
            1 -> ManualEntryFragment.newInstance(targetDate, editFoodId)
            2 -> AiEntryFragment.newInstance(targetDate)
            else -> throw IllegalArgumentException("Invalid tab position $position")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
