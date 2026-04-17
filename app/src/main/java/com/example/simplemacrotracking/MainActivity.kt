package com.example.simplemacrotracking

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.simplemacrotracking.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // No toolbar — hide the default action bar entirely
        supportActionBar?.hide()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // Show FAB only on Diary and Foods tabs; show mic FAB only on Diary
        val fabDestinations = setOf(R.id.diaryFragment, R.id.foodDatabaseFragment)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in fabDestinations) {
                binding.fab.show()
            } else {
                binding.fab.hide()
            }
            if (destination.id == R.id.diaryFragment) {
                binding.fabMic.show()
            } else {
                binding.fabMic.hide()
            }
        }
    }

    fun getFab(): FloatingActionButton = binding.fab
    fun getMicFab(): FloatingActionButton = binding.fabMic

    fun selectFoodsTab() {
        binding.bottomNav.selectedItemId = R.id.foodDatabaseFragment
    }
}
