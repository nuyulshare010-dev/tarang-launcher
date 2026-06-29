package com.tarang.launcher.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext

enum class NetKind { WIFI, ETHERNET, CELLULAR, NONE }

/** Active-connection summary for the status indicator. [wifiLevel] is 0..3 (Wi-Fi only). */
data class NetStatus(val kind: NetKind, val wifiLevel: Int, val online: Boolean)

/** Observes the default network and re-reads on every change. */
@Composable
fun rememberNetStatus(): NetStatus {
    val appContext = LocalContext.current.applicationContext
    val cm = remember { appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    val wm = remember { appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    var status by remember { mutableStateOf(readStatus(cm, wm)) }

    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { status = readStatus(cm, wm) }
            override fun onLost(network: Network) { status = readStatus(cm, wm) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                status = readStatus(cm, wm)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return status
}

private fun readStatus(cm: ConnectivityManager, wm: WifiManager?): NetStatus {
    val network = cm.activeNetwork ?: return NetStatus(NetKind.NONE, 0, false)
    val caps = cm.getNetworkCapabilities(network) ?: return NetStatus(NetKind.NONE, 0, false)
    val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val rssi = wifiRssi(caps, wm)
            val level = if (rssi != null) signalLevel(rssi) else 3
            NetStatus(NetKind.WIFI, level, online)
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetStatus(NetKind.ETHERNET, 3, online)
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetStatus(NetKind.CELLULAR, 3, online)
        else -> NetStatus(NetKind.NONE, 0, online)
    }
}

@Suppress("DEPRECATION") // connectionInfo only needed as the API 28 fallback (no location data read)
private fun wifiRssi(caps: NetworkCapabilities, wm: WifiManager?): Int? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val strength = caps.signalStrength
        if (strength != Int.MIN_VALUE) return strength
    }
    return wm?.connectionInfo?.rssi
}

@Suppress("DEPRECATION") // static calculateSignalLevel is the only minSdk-28-safe variant
private fun signalLevel(rssi: Int): Int = WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)

/**
 * A small Wi-Fi glyph (dot + three arcs) that lights up arcs to match the signal level. Ethernet/
 * cellular show full; no connection dims everything and adds a slash.
 */
@Composable
fun WifiIndicator(status: NetStatus, modifier: Modifier = Modifier, tint: Color = Color.White) {
    val bright = tint
    val dim = tint.copy(alpha = 0.25f)
    val online = status.online && status.kind != NetKind.NONE
    val arcsOn = if (online) status.wifiLevel.coerceIn(0, 3) else 0

    Canvas(modifier = modifier) {
        val s = size.minDimension
        val cx = size.width / 2f
        val cy = size.height * 0.80f
        val stroke = s * 0.075f
        val radii = floatArrayOf(s * 0.22f, s * 0.37f, s * 0.52f)
        radii.forEachIndexed { i, r ->
            drawArc(
                color = if (arcsOn >= i + 1) bright else dim,
                startAngle = 215f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        drawCircle(color = if (online) bright else dim, radius = s * 0.055f, center = Offset(cx, cy))
        if (!online) {
            drawLine(
                color = bright,
                start = Offset(s * 0.20f, s * 0.20f),
                end = Offset(s * 0.80f, s * 0.80f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
