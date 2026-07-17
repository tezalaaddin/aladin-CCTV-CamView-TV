package com.aladin.aladincamviewer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

/**
 * Monitors network connectivity changes.
 * Essential for handling TV boot delays and intermittent connection drops.
 */
class NetworkMonitor(context: Context, private val onNetworkStatusChanged: (Boolean) -> Unit) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post { onNetworkStatusChanged(true) }
        }

        override fun onLost(network: Network) {
            mainHandler.post { onNetworkStatusChanged(false) }
        }
    }

    fun start() {
        // Check initial state
        val initialStatus = isCurrentlyConnected()
        onNetworkStatusChanged(initialStatus)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun stop() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
