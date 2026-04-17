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
import com.example.simplemacrotracking.BuildConfig
import com.example.simplemacrotracking.data.repository.FoodRepository
import com.example.simplemacrotracking.databinding.FragmentBarcodeEntryBinding
import com.example.simplemacrotracking.util.NetworkResult
import com.example.simplemacrotracking.util.NetworkUtils
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
    @Inject lateinit var networkUtils: NetworkUtils

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
        binding.btnRetryOffline.setOnClickListener {
            binding.layoutOffline.visibility = View.GONE
            checkCameraPermission()
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
        binding.layoutOffline.visibility = View.GONE
        binding.layoutScanning.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.tvScanStatus.text = "Point camera at a barcode to scan automatically"

        if (BuildConfig.DEBUG) Log.d("BarcodeEntryFragment", "Initializing camera for barcode scanning")

        // Start scanning (only register callback once)
        if (!isScannerInitialized) {
            isScannerInitialized = true
            binding.barcodeView.decodeContinuous(BarcodeCallback { result ->
                if (BuildConfig.DEBUG) Log.d("BarcodeEntryFragment", "Barcode callback triggered: ${result?.text}")
                if (result?.text != null && !isProcessing) {
                    isProcessing = true
                    binding.barcodeView.pause()
                    if (BuildConfig.DEBUG) Log.d("BarcodeEntryFragment", "Barcode detected: ${result.text}")
                    handleBarcode(result.text)
                }
            })
        }
        binding.barcodeView.resume()
        if (BuildConfig.DEBUG) Log.d("BarcodeEntryFragment", "Camera initialized successfully")
    }

    private fun showPermissionDenied() {
        binding.layoutPermissionDenied.visibility = View.VISIBLE
        binding.layoutScanning.visibility = View.GONE
        binding.layoutOffline.visibility = View.GONE
    }

    private fun showOffline() {
        binding.layoutOffline.visibility = View.VISIBLE
        binding.layoutScanning.visibility = View.GONE
        binding.layoutPermissionDenied.visibility = View.GONE
    }


    private fun handleBarcode(barcode: String) {
        if (BuildConfig.DEBUG) Log.d("BarcodeEntryFragment", "Handling barcode: $barcode")

        // Fast offline check before hitting the network
        if (!networkUtils.isOnline()) {
            // Check local cache first — user may have scanned this barcode before
            viewLifecycleOwner.lifecycleScope.launch {
                val cached = foodRepository.getFoodItemByBarcode(barcode)
                if (cached != null) {
                        if (BuildConfig.DEBUG) Log.d("BarcodeEntryFragment", "Offline hit from cache: ${cached.name}")
                    isProcessing = false
                    parentFragmentManager.setFragmentResult(
                        "food_saved",
                        bundleOf("foodItemId" to cached.id)
                    )
                } else {
                    isProcessing = false
                    binding.progressBar.visibility = View.GONE
                    binding.barcodeView.pause()
                    showOffline()
                }
            }
            return
        }

        binding.tvScanStatus.text = "Looking up barcode…"
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = foodRepository.fetchByBarcode(barcode)) {
                is NetworkResult.Success -> {
                    if (BuildConfig.DEBUG) Log.d("BarcodeEntryFragment", "Food item found: ${result.data.name}")
                    isProcessing = false
                    parentFragmentManager.setFragmentResult(
                        "food_saved",
                        bundleOf("foodItemId" to result.data.id)
                    )
                }
                is NetworkResult.Error -> {
                    if (BuildConfig.DEBUG) Log.e("BarcodeEntryFragment", "Error fetching barcode: ${result.message}")
                    isProcessing = false
                    binding.progressBar.visibility = View.GONE
                    if (result.message.contains("internet", ignoreCase = true) ||
                        result.message.contains("connect", ignoreCase = true)) {
                        binding.barcodeView.pause()
                        showOffline()
                    } else {
                        binding.tvScanStatus.text = "❌ ${result.message}. Tap to retry."
                        delay(3000)
                        if (isAdded) {
                            binding.tvScanStatus.text = "Point camera at a barcode to scan automatically"
                            binding.barcodeView.resume()
                        }
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

