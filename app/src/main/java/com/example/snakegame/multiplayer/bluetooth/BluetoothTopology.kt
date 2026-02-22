package com.example.snakegame.multiplayer.bluetooth

sealed interface BluetoothRole {
    data object Host : BluetoothRole
    data class Client(val hostAddress: String) : BluetoothRole
}

