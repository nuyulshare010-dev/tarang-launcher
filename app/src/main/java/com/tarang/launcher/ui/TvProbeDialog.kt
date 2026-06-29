package com.tarang.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.TV_LISTINGS_PERMISSION
import com.tarang.launcher.data.TvContentProbe
import com.tarang.launcher.data.TvProbeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Diagnostic overlay for the "can apps feed a content carousel?" spike. Runs [TvContentProbe] and
 * reports, in plain language, whether the TvProvider rows are readable, blocked, or empty on this
 * device. The detail area is D-pad scrollable (focus it and press up/down).
 */
@Composable
fun TvProbeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        var reloadKey by remember { mutableIntStateOf(0) }
        var granted by remember { mutableStateOf(false) }
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted = it; reloadKey++ }

        val result by produceState<TvProbeResult?>(initialValue = null, key1 = reloadKey) {
            value = withContext(Dispatchers.IO) { TvContentProbe.run(context) }
        }
        val firstFocus = remember { FocusRequester() }
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        var detailFocused by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141417))
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("TV content probe", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

                val r = result
                if (r == null) {
                    Text("Scanning…", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                } else {
                    val verdict = verdictOf(r)
                    Text(verdict.first, color = verdict.second, fontSize = 16.sp, fontWeight = FontWeight.Medium)

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProbeButton("Rescan", Modifier.focusRequester(firstFocus)) { reloadKey++ }
                        if (!r.permissionGranted) {
                            ProbeButton("Request permission") { permLauncher.launch(TV_LISTINGS_PERMISSION) }
                        }
                        ProbeButton("Close") { onDismiss() }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (detailFocused) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp),
                            )
                            .onFocusChanged { detailFocused = it.isFocused }
                            .onKeyEvent { handleScrollKey(it, scroll, scope) }
                            .focusable()
                            .verticalScroll(scroll)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Mono("READ_TV_LISTINGS: ${if (r.permissionGranted) "granted" else "NOT granted"}")
                        Mono("channels: ${fmt(r.previewChannels)}    programs: ${fmt(r.previewPrograms)}    watch-next: ${fmt(r.watchNext)}")
                        Mono(
                            "of ${fmt(r.previewPrograms)} programs →  video: ${r.withVideo}    poster: ${r.withPoster}    intent: ${r.withIntent}",
                            Color(0xFF80E27E),
                        )
                        r.error?.let { Mono("error: $it", Color(0xFFFF8A80)) }

                        if (r.perPackage.isNotEmpty()) {
                            Mono("")
                            Mono("by app:")
                            r.perPackage.forEach { (pkg, n) -> Mono("  • $pkg — $n") }
                        }
                        if (r.samples.isNotEmpty()) {
                            Mono("")
                            Mono("samples [P=poster V=video I=intent]:")
                            r.samples.forEach { s ->
                                val flags = buildString {
                                    if (s.poster) append("P"); if (s.video) append("V"); if (s.intent) append("I")
                                }
                                Mono("  • [${flags.ifEmpty { "-" }}] ${s.pkg}: ${s.title.take(44)}")
                            }
                        }
                    }

                    Text(
                        if (detailFocused) "▲ ▼ scroll details   ·   Back to close" else "Focus the panel and press ▼ to scroll   ·   Back to close",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
}

/** Scroll the detail panel on D-pad up/down; return false at the ends so focus can leave the panel. */
private fun handleScrollKey(
    e: KeyEvent,
    scroll: androidx.compose.foundation.ScrollState,
    scope: CoroutineScope,
): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val step = 260f
    return when (e.key) {
        Key.DirectionDown -> if (scroll.canScrollForward) { scope.launch { scroll.animateScrollBy(step) }; true } else false
        Key.DirectionUp -> if (scroll.canScrollBackward) { scope.launch { scroll.animateScrollBy(-step) }; true } else false
        else -> false
    }
}

@Composable
private fun Mono(text: String, color: Color = Color.White.copy(alpha = 0.75f)) {
    Text(text, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
}

private fun fmt(n: Int) = if (n < 0) "n/a" else n.toString()

private fun verdictOf(r: TvProbeResult): Pair<String, Color> {
    val red = Color(0xFFFF8A80)
    val amber = Color(0xFFFFE082)
    val green = Color(0xFF80E27E)
    return when {
        r.blocked ->
            "Blocked: this launcher can't read other apps' rows here (permission restricted)." to red
        r.previewPrograms > 0 ->
            "Works — ${r.previewPrograms} programs from ${r.perPackage.size} app(s). A carousel is feasible here." to green
        !r.permissionGranted ->
            "Grant READ_TV_LISTINGS, then rescan (the read needs it)." to amber
        else ->
            "Readable, but nothing published yet (apps haven't added rows, or Google TV serves them server-side)." to amber
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProbeButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(percent = 50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2E),
            focusedContainerColor = Color(0xFF3A6FF2),
        ),
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}
