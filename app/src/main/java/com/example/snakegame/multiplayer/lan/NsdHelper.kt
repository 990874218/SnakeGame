package com.example.snakegame.multiplayer.lan

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_SERVICE_TYPE = "_snakegame._tcp."

/**
 * 封装 NSD（Network Service Discovery）流程，负责服务注册与发现。
 */
class NsdHelper(
    context: Context,
    private val scope: CoroutineScope,
    private val serviceType: String = DEFAULT_SERVICE_TYPE,
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _services = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val services: StateFlow<List<NsdServiceInfo>> = _services

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun registerService(
        port: Int,
        serviceName: String,
        attributes: Map<String, ByteArray> = emptyMap(),
        onRegistered: (NsdServiceInfo) -> Unit = {},
    ) {
        unregisterService()

        val serviceInfo =
            NsdServiceInfo().apply {
                serviceType = this@NsdHelper.serviceType
                this.serviceName = serviceName
                this.port = port
                attributes.forEach { (key, value) ->
                    setAttribute(key, Base64.encodeToString(value, Base64.NO_WRAP))
                }
            }

        registrationListener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    scope.launch { onRegistered(serviceInfo) }
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    scope.launch { _errors.emit("注册服务失败: $errorCode") }
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    scope.launch { _errors.emit("注销服务失败: $errorCode") }
                }
            }

        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }.onFailure { e ->
            scope.launch { _errors.emit(e.message ?: "注册服务异常") }
        }
    }

    fun startDiscovery() {
        stopDiscovery()
        _services.value = emptyList()

        discoveryListener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType != serviceType) return
                    runCatching {
                        nsdManager.resolveService(
                            serviceInfo,
                            object : NsdManager.ResolveListener {
                                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                    scope.launch {
                                        val list = _services.value.toMutableList()
                                        if (
                                            list.none {
                                                it.serviceName == resolvedInfo.serviceName &&
                                                    it.host == resolvedInfo.host &&
                                                    it.port == resolvedInfo.port
                                            }
                                        ) {
                                            list.add(resolvedInfo)
                                            _services.value = list
                                        }
                                    }
                                }

                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                    scope.launch { _errors.emit("解析服务失败: $errorCode") }
                                }
                            },
                        )
                    }.onFailure { e ->
                        scope.launch { _errors.emit(e.message ?: "解析服务异常") }
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    scope.launch {
                        val list = _services.value.toMutableList()
                        list.removeAll { it.serviceName == serviceInfo.serviceName && it.host == serviceInfo.host }
                        _services.value = list
                    }
                }

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    scope.launch { _errors.emit("发现服务失败: $errorCode") }
                    stopDiscovery()
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    scope.launch { _errors.emit("停止发现失败: $errorCode") }
                    stopDiscovery()
                }
            }

        runCatching {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure { e ->
            scope.launch { _errors.emit(e.message ?: "发现服务异常") }
        }
    }

    fun stopDiscovery() {
        runCatching {
            discoveryListener?.let {
                nsdManager.stopServiceDiscovery(it)
            }
        }
        discoveryListener = null
    }

    fun unregisterService() {
        runCatching { registrationListener?.let { nsdManager.unregisterService(it) } }
        registrationListener = null
    }

    @SuppressLint("MissingPermission")
    fun tearDown() {
        stopDiscovery()
        unregisterService()
        _services.value = emptyList()
    }
}

