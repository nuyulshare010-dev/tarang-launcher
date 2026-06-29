package com.tarang.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A quiet time + date readout for the top bar. Updates once a minute (it ticks on the minute
 * boundary, not every second, so it costs almost nothing). Honors the device's 12/24h setting.
 */
@Composable
fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val use24h = remember { DateFormat.is24HourFormat(context) }
    val timeFormat = remember(use24h) { SimpleDateFormat(if (use24h) "HH:mm" else "h:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }

    var now by remember { mutableStateOf(Date(System.currentTimeMillis())) }
    LaunchedEffectMinuteTick { now = Date(System.currentTimeMillis()) }

    val colors = LocalLauncherColors.current
    Column(modifier = modifier) {
        Text(
            text = timeFormat.format(now),
            color = colors.text,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = dateFormat.format(now),
            color = colors.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Runs [onTick] now and then on every wall-clock minute boundary. */
@Composable
private fun LaunchedEffectMinuteTick(onTick: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            onTick()
            val millis = System.currentTimeMillis()
            delay(60_000L - (millis % 60_000L)) // sleep until the next minute
        }
    }
}
