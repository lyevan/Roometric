package com.infinity.roometric

import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.tabs.TabLayout
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
        
        // Get room info from intent
        currentRoomId = intent.getLongExtra("ROOM_ID", -1)
        currentRoomName = intent.getStringExtra("ROOM_NAME") ?: "Unknown Room"
        
        // Set toolbar title with room name
        supportActionBar?.title = currentRoomName
        
        // Setup tab layout
        setupTabs()
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