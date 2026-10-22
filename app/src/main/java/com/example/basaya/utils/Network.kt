package com.example.basaya.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Network {

    /** True when the phone has a connection that actually reaches the internet. */
    fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
