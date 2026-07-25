package com.aladin.aladincamviewer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Monitors network connectivity changes.
 * Essential for handling TV boot delays and intermittent connection drops.
 */
class NetworkMonitor(context: Context, private val onNetworkStatusChanged: (Boolean) -> Unit) {

    private val tag = "ALADIN_NETWORK"

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(tag, "LAN available network=$network")
            mainHandler.post { onNetworkStatusChanged(true) }
        }

        override fun onLost(network: Network) {
            val connected = isCurrentlyConnected()
            Log.w(tag, "LAN lost network=$network anotherLanAvailable=$connected")
            mainHandler.post { onNetworkStatusChanged(connected) }
        }
    }

    fun start() {
        // Check initial state
        val initialStatus = isCurrentlyConnected()
        Log.d(tag, "LAN monitor start initialConnected=$initialStatus")
        onNetworkStatusChanged(initialStatus)

        val request = NetworkRequest.Builder()
            // Dedicated CCTV networks often have no internet route.
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        if (!isRegistered) {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isRegistered = true
        }
    }

    fun stop() {
        if (isRegistered) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
        }
    }

    private fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
