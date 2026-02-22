package com.example.snakegame.multiplayer.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothPermissionHelper {

    val scanPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

    fun hasScanPermissions(context: Context): Boolean =
        scanPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    fun isBluetoothEnabled(): Boolean =
        BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
}

