package com.infinity.roometric

import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.tabs.TabLayout
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infinity.roometric.databinding.ActivityMainBinding
import com.infinity.roometric.viewmodel.ViewModel

class MainActivity : AppCompatActivity() {
    val viewModel : ViewModel by viewModels()
    private lateinit var binding : ActivityMainBinding
    
    // Room info from intent
    var currentRoomId: Long = -1
    var currentRoomName: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Check ARCore availability before proceeding
        if (!checkArCoreAvailability()) {
            // If ARCore is not available, show error and close activity
            finish()
            return
        }
        
        // Get room info from intent
        currentRoomId = intent.getLongExtra("ROOM_ID", -1)
        currentRoomName = intent.getStringExtra("ROOM_NAME") ?: "Unknown Room"
        
        // Set toolbar title with room name
        supportActionBar?.title = currentRoomName
        
        // Setup tab layout
        setupTabs()
    }
    
    private fun checkArCoreAvailability(): Boolean {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(this)
            when (availability) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED,
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    // ARCore is supported, continue normally
                    true
                }
                ArCoreApk.Availability.UNKNOWN_ERROR,
                ArCoreApk.Availability.UNKNOWN_CHECKING,
                ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> {
                    // Show a warning but allow app to continue
                    MaterialAlertDialogBuilder(this)
                        .setTitle("ARCore Status Unknown")
                        .setMessage("Unable to verify ARCore compatibility. Some features may not work properly.")
                        .setPositiveButton("Continue") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show()
                    true
                }
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    // Device doesn't support ARCore - show error and return to home
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Device Not Compatible")
                        .setMessage("Your device doesn't support ARCore, which is required for AR measurements. Please use a compatible device.")
                        .setPositiveButton("Return to Home") { _, _ ->
                            finish()
                        }
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setCancelable(false)
                        .show()
                    false
                }
            }
        } catch (e: UnavailableException) {
            MaterialAlertDialogBuilder(this)
                .setTitle("ARCore Error")
                .setMessage("There was an error checking ARCore compatibility. Please try again.")
                .setPositiveButton("Return to Home") { _, _ ->
                    finish()
                }
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .show()
            false
        }
    }
    
    private fun setupTabs() {
        // Add tabs programmatically
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Measure"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Estimate"))
        
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showMeasureFragment()
                    1 -> showEstimateFragment()
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun showMeasureFragment() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHostFragment?.navController
        
        if (navController?.currentDestination?.id != R.id.arFragment) {
            navController?.navigate(R.id.arFragment)
        }
    }
    
    private fun showEstimateFragment() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHostFragment?.navController
        
        if (navController?.currentDestination?.id != R.id.estimateFragment) {
            navController?.navigate(R.id.estimateFragment)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            val toast = Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
            toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0)
            toast.show()
            if (!shouldShowRequestPermissionRationale(this.toString())) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }
}