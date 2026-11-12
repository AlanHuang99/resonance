package com.resonance.music.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Finds Google Cast devices over mDNS (`_googlecast._tcp`), which Android can do with
 * [NsdManager] — no multicast library needed.
 *
 * Resolving is serialised because NsdManager rejects overlapping resolves on one listener,
 * and each resolve is one TXT lookup on the LAN.
 */
class CastDiscovery(private val context: Context) {

    suspend fun discover(timeoutMs: Long = 4000): List<CastDevice> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(NsdManager::class.java) ?: return@withContext emptyList()
        val found = LinkedHashMap<String, NsdServiceInfo>()
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w("CastDiscovery", "start failed: $errorCode")
                runCatching { manager.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(found) { found.remove(serviceInfo.serviceName) }
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(found) { found[serviceInfo.serviceName] = serviceInfo }
            }
        }
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
            .onFailure { return@withContext emptyList() }

        // Give discovery its window, then resolve whatever turned up before it closes.
        withTimeoutOrNull(timeoutMs) { kotlinx.coroutines.delay(timeoutMs) }
        runCatching { manager.stopServiceDiscovery(discovery) }

        val services = synchronized(found) { found.values.toList() }
        services.mapNotNull { resolve(manager, it) }.distinctBy { it.id }
    }

    private suspend fun resolve(manager: NsdManager, info: NsdServiceInfo): CastDevice? =
        withTimeoutOrNull(3000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (!continuation.isActive) return
                        val host = serviceInfo.host?.hostAddress ?: return continuation.resume(null)
                        val name = text(serviceInfo, "fn") ?: serviceInfo.serviceName
                        val id = text(serviceInfo, "id") ?: serviceInfo.serviceName
                        continuation.resume(
                            CastDevice(
                                id = id,
                                name = name,
                                kind = CastDevice.Kind.CAST,
                                host = host,
                                port = serviceInfo.port
                            )
                        )
                    }
                }
                try {
                    manager.resolveService(info, listener)
                } catch (error: IllegalArgumentException) {
                    Log.w("CastDiscovery", "resolve rejected: ${error.message}")
                    continuation.resume(null)
                }
            }
        }

    private fun text(info: NsdServiceInfo, key: String): String? =
        info.attributes?.get(key)?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }

    private companion object {
        const val SERVICE_TYPE = "_googlecast._tcp"
    }
}
