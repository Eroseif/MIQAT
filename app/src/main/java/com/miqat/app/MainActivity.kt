package com.miqat.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.miqat.app.core.LocationTracker
import com.miqat.app.ui.MiqatRoot
import com.miqat.app.ui.MiqatTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.any { it }
            if (!granted) vm.permissionDenials++
            vm.setPermission(granted)
        }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (vm.permissionDenials >= 2) {
            // Android stops showing the dialog after repeated denials; send the user to app settings.
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK)
        )
        vm.setPermission(hasLocationPermission())
        if (!vm.askedForPermission && !hasLocationPermission()) {
            vm.askedForPermission = true
            requestLocation()
        }

        // Foreground-only GPS: collected only while the activity is STARTED.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.map { it.permissionGranted }.distinctUntilChanged().collectLatest { granted ->
                    if (granted) {
                        LocationTracker(applicationContext).updates().collect { vm.onLocation(it.latitude, it.longitude) }
                    }
                }
            }
        }

        setContent {
            MiqatTheme {
                MiqatRoot(vm = vm, onRequestPermission = { requestLocation() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setPermission(hasLocationPermission())
        vm.refreshIfNewDay()
    }
}
