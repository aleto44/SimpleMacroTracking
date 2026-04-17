package com.example.simplemacrotracking.ui.entry

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.simplemacrotracking.data.repository.FoodRepository
import com.example.simplemacrotracking.databinding.FragmentBarcodeEntryBinding
import com.example.simplemacrotracking.util.NetworkResult
import com.journeyapps.barcodescanner.BarcodeCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BarcodeEntryFragment : Fragment() {

    private var _binding: FragmentBarcodeEntryBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var foodRepository: FoodRepository

    private var isProcessing = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showScanningLayout()
        else showPermissionDenied()
    }

    companion object {
        fun newInstance(targetDate: String) = BarcodeEntryFragment().apply {
            arguments = bundleOf("targetDate" to targetDate)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBarcodeEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        checkCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission() && binding.layoutScanning.visibility == View.VISIBLE) {
            isProcessing = false
            binding.barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.barcodeView.pause()
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun checkCameraPermission() {
        if (hasCameraPermission()) showScanningLayout()
        else showPermissionDenied()
    }

    private var isScannerInitialized = false

    private fun showScanningLayout() {
        binding.layoutPermissionDenied.visibility = View.GONE
        binding.layoutScanning.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.tvScanStatus.text = "Point camera at a barcode to scan automatically"

        Log.d("BarcodeEntryFragment", "Initializing camera for barcode scanning")

        // Start scanning (only register callback once)
        if (!isScannerInitialized) {
            isScannerInitialized = true
            binding.barcodeView.decodeContinuous(BarcodeCallback { result ->
                Log.d("BarcodeEntryFragment", "Barcode callback triggered: ${result?.text}")
                if (result?.text != null && !isProcessing) {
                    isProcessing = true
                    binding.barcodeView.pause()
                    Log.d("BarcodeEntryFragment", "Barcode detected: ${result.text}")
                    handleBarcode(result.text)
                }
            })
        }
        binding.barcodeView.resume()
        Log.d("BarcodeEntryFragment", "Camera initialized successfully")
    }

    private fun showPermissionDenied() {
        binding.layoutPermissionDenied.visibility = View.VISIBLE
        binding.layoutScanning.visibility = View.GONE
    }


    private fun handleBarcode(barcode: String) {
        Log.d("BarcodeEntryFragment", "Handling barcode: $barcode")
        binding.tvScanStatus.text = "Looking up barcode…"
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = foodRepository.fetchByBarcode(barcode)) {
                is NetworkResult.Success -> {
                    Log.d("BarcodeEntryFragment", "Food item found: ${result.data.name}")
                    isProcessing = false
                    parentFragmentManager.setFragmentResult(
                        "food_saved",
                        bundleOf("foodItemId" to result.data.id)
                    )
                }
                is NetworkResult.Error -> {
                    Log.e("BarcodeEntryFragment", "Error fetching barcode: ${result.message}")
                    isProcessing = false
                    binding.progressBar.visibility = View.GONE
                    binding.tvScanStatus.text = "Error: ${result.message}. Tap to retry."
                    delay(3000)
                    if (isAdded) {
                        binding.tvScanStatus.text = "Point camera at a barcode to scan automatically"
                        binding.barcodeView.resume()
                    }
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

