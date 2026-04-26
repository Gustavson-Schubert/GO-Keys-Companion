package com.companion.gokeys

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.companion.gokeys.ui.App
import com.companion.gokeys.ui.theme.GoKeysTheme
import com.companion.gokeys.viewmodel.CompanionViewModel

class MainActivity : ComponentActivity() {

    private val vm: CompanionViewModel by viewModels { CompanionViewModel.Factory(application) }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureBluetoothPermissions()
        setContent {
            GoKeysTheme {
                App(vm)
            }
        }
    }

    private fun ensureBluetoothPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
    }
}
