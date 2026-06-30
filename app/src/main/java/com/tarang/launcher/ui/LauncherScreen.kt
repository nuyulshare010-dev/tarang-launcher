package com.tarang.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.R
import com.tarang.launcher.data.FrameSource
import com.tarang.launcher.data.LauncherSettings
import com.tarang.launcher.di.AppContainer
import com.tarang.launcher.home.HomeSetup
import com.tarang.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Cap the frosted-glass backdrop re-capture at ~20fps (an animated wallpaper would otherwise drive it
// at 60fps; the blurred backdrop doesn't need that, and the capture is the heaviest per-frame cost).
private const val BACKDROP_CAPTURE_INTERVAL_NS = 50_000_000L

/**
 * Top-level launcher UI: an animated wallpaper behind a clean app grid, with a top bar holding the
 * clock and settings (tune) button. No content rows. Tapping a tile launches the app directly.
 */
@Composable
fun LauncherScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val viewModel: LauncherViewModel = viewModel(
        factory = LauncherViewModel.provideFactory(
            container.appRepository,
            container.favoritesStore,
            container.settingsStore,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val tuneFocus = remember { FocusRequester() }

    // Image wallpaper: a built-in browser (no external gallery app needed) returns a Uri, which we
    // copy into app storage and set as the wallpaper.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var pickerForFrame by remember { mutableStateOf(false) } // the photo picker is shared: wallpaper vs. frame photo
    var showFolderPicker by remember { mutableStateOf(false) }
    fun applyPickedImage(uri: Uri) {
        val forFrame = pickerForFrame
        showPicker = false
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                copyImageToInternal(context, uri, subdir = if (forFrame) "frame" else "wallpaper")
            }
            if (path != null) {
                if (forFrame) viewModel.setFrameImage(path) else viewModel.setImageWallpaper(path)
            }
        }
    }
    val pickImage: () -> Unit = { pickerForFrame = false; showPicker = true }
    val pickFramePhoto: () -> Unit = { pickerForFrame = true; showPicker = true }
    val pickFrameFolder: () -> Unit = { showFolderPicker = true }
    var showTvProbe by remember { mutableStateOf(false) }

    // Filter the hidden apps once (not on every recomposition) so the grid list stays stable.
    val visibleGrid = remember(uiState.gridApps, settings.hiddenApps) {
        uiState.gridApps.filterNot { it.packageName in settings.hiddenApps }
    }
    val preset = WallpaperPresets.getOrElse(settings.wallpaperId) { WallpaperPresets.first() }
    val imagePath = settings.wallpaperImagePath
    val showImage = settings.useImageWallpaper && imagePath != null && remember(imagePath) { File(imagePath).exists() }

    // While hovering an opted-in favorite, its app artwork plays as the wallpaper; clears (back to the
    // selected wallpaper) when focus leaves the dock. Not while the settings page is up.
    var favoriteHover by remember { mutableStateOf<String?>(null) }
    // Frame Art ("painting") mode. Declared here so the wallpaper selection below falls back to the
    // base wallpaper while it's on (no hover artwork bleeding into the frame).
    var frameOn by remember { mutableStateOf(false) }
    // Owns D-pad focus while Frame Art is showing (see the capture layer below), so the grid behind it
    // can't be navigated or clicked (which would silently launch an app).
    val frameCaptureFocus = remember { FocusRequester() }
    val artworkApp = if (!showSettings && !frameOn) {
        favoriteHover?.takeIf { settings.useAppArtwork && it in settings.artworkApps }
    } else {
        null
    }

    // Record the wallpaper into a layer so the dock can re-draw it blurred as a frosted backdrop.
    val backdrop = rememberGraphicsLayer()
    // Wall-clock of the last backdrop capture (a plain holder, not state — read/written in draw without
    // triggering recomposition), used to throttle the capture to ~20fps for an animated wallpaper.
    val lastBackdropCapture = remember { longArrayOf(0L) }
    val isDark = rememberIsDark(settings.theme)
    val colors = if (isDark) DarkLauncherColors else LightLauncherColors

    // "Choose Home app" is only offered when the device actually exposes a Home-app chooser (often
    // absent on Google TV, where the redirect accessibility service is the real mechanism).
    val chooseHomeApp: (() -> Unit)? = remember(context) {
        if (HomeSetup.canOpenHomeSettings(context)) {
            { HomeSetup.openHomeSettings(context) }
        } else {
            null
        }
    }

    // Frame Art auto-start: bump [interaction] on every key to restart the idle timer; when it elapses
    // (only while resumed, so it won't kick in behind another app) enter Frame Art. The next key exits.
    val lifecycleOwner = LocalLifecycleOwner.current
    var interaction by remember { mutableIntStateOf(0) }
    var wakingUp by remember { mutableStateOf(false) }
    val autoStartMs = settings.frameAutoStartSec * 1000L
    LaunchedEffect(interaction, autoStartMs, lifecycleOwner) {
        if (autoStartMs <= 0L) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            kotlinx.coroutines.delay(autoStartMs)
            frameOn = true
        }
    }

    // Smooth frame transition: 0 = launcher chrome present, 1 = full Frame Art (chrome gone, big clock
    // shown). [frameOn] is the target; this animates toward it so the chrome can scale up + slide out
    // and the clock fade in, then reverse on exit.
    val frameProgress = remember { Animatable(0f) }
    LaunchedEffect(frameOn) {
        frameProgress.animateTo(
            if (frameOn) 1f else 0f,
            // A calm, deliberate chrome transition — same speed both ways so enter/exit feel symmetric.
            tween(durationMillis = 1700, easing = FastOutSlowInEasing),
        )
    }
    // The two chrome layers leave/return on their own timelines for a layered feel — the dock leads,
    // the top bar trails. The master [frameProgress] above still drives the clock + wallpaper crossfade
    // and all the gating (chromePresent / frameSettled / focus trap), so these only shape the motion.
    val dockProgress = remember { Animatable(0f) }
    val topBarProgress = remember { Animatable(0f) }
    LaunchedEffect(frameOn) {
        dockProgress.animateTo(if (frameOn) 1f else 0f, tween(durationMillis = 1200, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(frameOn) {
        topBarProgress.animateTo(if (frameOn) 1f else 0f, tween(durationMillis = 1500, easing = FastOutSlowInEasing))
    }
    val frameSettled by remember { derivedStateOf { frameProgress.value > 0.999f } }
    val chromePresent by remember { derivedStateOf { frameProgress.value < 0.999f } }
    val framePartly by remember { derivedStateOf { frameProgress.value > 0.001f } }
    // Mid-transition: used to drop the glass edge-refraction while the chrome is moving (cheaper, and
    // the bevel is imperceptible in motion).
    val frameMoving by remember { derivedStateOf { frameProgress.value > 0.001f && frameProgress.value < 0.999f } }

    // When Frame Art turns on, pull focus onto the capture layer so nothing behind it stays focused
    // (otherwise D-pad keys reach the grid — moving focus, or launching the focused app on OK). On
    // exit the grid recomposes from scratch and reclaims focus via its own first-card requester.
    LaunchedEffect(frameOn) {
        if (frameOn) runCatching { frameCaptureFocus.requestFocus() }
    }

    // Is Frame Art configured to a real source (folder/single)? The "current wallpaper" source has no
    // art of its own — it just shows the wallpaper full-screen — so there's nothing to overlay there.
    val frameArtConfigured = (settings.frameSource == FrameSource.FOLDER && settings.frameFolderId != null) ||
        (settings.frameSource == FrameSource.SINGLE && settings.frameImagePath != null)
    val frameArtIsWallpaper = settings.useFrameArtWallpaper && frameArtConfigured

    // The drift is composed the whole time Frame Art is present (not just when settled); its amplitude
    // follows the transition progress, read at draw time, so the float eases in on entry and glides
    // back to centre on exit rather than snapping when a key flips it off. At rest (chrome up) it's
    // disposed entirely — a still wallpaper costs nothing. Cycling still only runs when fully settled.
    val motionOn = settings.frameMotion && !settings.reduceMotion
    val artDrift = framePartly && motionOn
    val artDriftAmount: () -> Float = { frameProgress.value }
    val artCycle = frameSettled

    // App launch / return choreography. The system grows the app window out of the tapped tile while the
    // launcher chrome drops/rises away on its own staggered timelines — the dock leads, the top bar
    // trails. 0 = home, 1 = launched (chrome gone). Skipped when Reduce motion is on.
    val dockLaunch = remember { Animatable(0f) }
    val topBarLaunch = remember { Animatable(0f) }
    var awaitingReturn by remember { mutableStateOf(false) }
    var returnTick by remember { mutableIntStateOf(0) }
    var launchTick by remember { mutableIntStateOf(0) }
    // True while a launch/return is animating — used to freeze the (GPU-heavy) frosted glass and the
    // per-frame wallpaper capture so the animation itself stays smooth on weak TV hardware. The top bar
    // is the longer of the two, so it's the last to settle.
    val transitioning by remember { derivedStateOf { topBarLaunch.value > 0.001f } }

    fun launchApp(packageName: String) {
        // No window scale-up — the app opens with the system default while the launcher chrome does the
        // dock-drop / bar-rise dissolve (the same motion as entering Frame Art).
        val launched = viewModel.launchApp(packageName, null)
        if (launched && !settings.reduceMotion) {
            awaitingReturn = true
            launchTick++
        }
    }

    // Coming back from a launched app: ease the grid in from the tile we left through.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingReturn) {
                awaitingReturn = false
                returnTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(launchTick) {
        if (launchTick > 0) {
            // Leaving for the app: ease IN (start slow, accelerate away). Dock leads (600ms), top bar
            // trails (900ms). Run them in parallel so each keeps its own duration.
            launch { dockLaunch.animateTo(1f, tween(durationMillis = 600, easing = EaseIn)) }
            launch { topBarLaunch.animateTo(1f, tween(durationMillis = 900, easing = EaseIn)) }
        }
    }
    LaunchedEffect(returnTick) {
        if (returnTick > 0) {
            // Returning from the app: the reverse — start from the launched state and ease OUT (rush in,
            // decelerate to rest), same staggered durations.
            dockLaunch.snapTo(1f)
            topBarLaunch.snapTo(1f)
            launch { dockLaunch.animateTo(0f, tween(durationMillis = 600, easing = EaseOut)) }
            launch { topBarLaunch.animateTo(0f, tween(durationMillis = 900, easing = EaseOut)) }
        }
    }

    CompositionLocalProvider(LocalLauncherColors provides colors) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                when {
                    // Frame Art is up: the first key exits it and is swallowed (whole press).
                    frameOn -> {
                        if (e.type == KeyEventType.KeyDown) {
                            frameOn = false
                            wakingUp = true
                            interaction++
                        }
                        true
                    }
                    // Swallow the key-up of the press that exited the frame (no stray click/launch).
                    wakingUp -> {
                        if (e.type == KeyEventType.KeyUp) wakingUp = false
                        true
                    }
                    else -> {
                        if (e.type == KeyEventType.KeyDown) interaction++ // any key resets the idle timer
                        false
                    }
                }
            },
    ) {
        // Frame Art owns Back too: consume it to wake the launcher instead of letting the system act
        // on it (which flashes the stock launcher / can finish this activity).
        BackHandler(enabled = frameOn || framePartly) {
            frameOn = false
            interaction++
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Skip the per-frame frosted backdrop capture during a launch zoom, once any Frame
                    // Art is showing, or when glass blur is turned off entirely (no one samples it then)
                    // — draw straight to the screen.
                    if (transitioning || framePartly || !settings.glassBlur) {
                        drawContent()
                    } else {
                        // Throttle the (full-screen, GPU-heavy) capture to ~20fps: an animated wallpaper
                        // invalidates every frame, but the blurred backdrop behind the glass doesn't need
                        // 60fps. Always composite the (possibly slightly stale) layer to the screen.
                        val now = System.nanoTime()
                        if (now - lastBackdropCapture[0] >= BACKDROP_CAPTURE_INTERVAL_NS) {
                            backdrop.record { this@drawWithContent.drawContent() }
                            lastBackdropCapture[0] = now
                        }
                        drawLayer(backdrop)
                    }
                },
        ) {
            // Hover artwork (when App artwork is on) takes priority over everything — including Frame
            // Art as the wallpaper — then falls back to the frame art / image / gradient base.
            Crossfade(targetState = artworkApp, animationSpec = tween(700), label = "wallpaper") { app ->
                when {
                    app != null -> AppArtworkWallpaper(
                        packageName = app,
                        blurred = settings.blurred,
                        isDark = isDark,
                        reduceMotion = settings.reduceMotion,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Frame Art as the base wallpaper (a still frame on home; alive in frame mode).
                    frameArtIsWallpaper -> FrameArtContent(
                        settings,
                        drift = artDrift,
                        driftAmount = artDriftAmount,
                        cycle = artCycle,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                    )

                    showImage && imagePath != null -> ImageWallpaper(
                        path = imagePath,
                        blurred = settings.blurred,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> AnimatedWallpaper(
                        preset = preset,
                        animated = false, // the wallpaper is always a still gradient now
                        blurred = settings.blurred,
                        ambient = null,
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // Frame mode with a configured source that ISN'T already the wallpaper: fade the art in over
            // the current wallpaper as the chrome leaves. (The "current wallpaper" frame source has no
            // art of its own, so nothing overlays — it just shows the wallpaper full-screen.)
            if (framePartly && frameArtConfigured && !frameArtIsWallpaper) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = frameProgress.value }) {
                    FrameArtContent(settings, drift = artDrift, driftAmount = artDriftAmount, cycle = artCycle, isDark = isDark, modifier = Modifier.fillMaxSize())
                }
            }
        }

        // Big Frame Art clock. Its entrance (fade + slight scale on the text, fade-only on the scrim) is
        // sequenced to arrive in the back half of the transition, as the chrome clears.
        if (framePartly && settings.frameClock) {
            FrameClock(
                position = settings.frameClockPosition,
                size = settings.frameClockSize,
                showDate = settings.frameShowDate,
                reveal = { ((frameProgress.value - 0.45f) / 0.55f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Launcher chrome (settings page or the grid). Hidden entirely in Frame Art, which is a pure,
        // chrome-free "painting".
        if (chromePresent) {
            // The settings page takes over the whole screen (not a modal), so D-pad focus can't reach
            // the launcher behind it; the launcher isn't composed while settings is open.
            if (showSettings) {
                SettingsScreen(
                    settings = settings,
                    onWallpaper = viewModel::setWallpaper,
                    onBlurred = viewModel::setBlurred,
                    onGlassBlur = viewModel::setGlassBlur,
                    onColumns = viewModel::setColumns,
                    onPickImage = pickImage,
                    onUseImage = { viewModel.setUseImageWallpaper(true) },
                    onScanTvContent = { showTvProbe = true },
                    favoriteApps = uiState.dockApps,
                    onUseAppArtwork = viewModel::setUseAppArtwork,
                    onToggleArtworkApp = viewModel::setArtworkApp,
                    theme = settings.theme,
                    onTheme = viewModel::setTheme,
                    reduceMotion = settings.reduceMotion,
                    onReduceMotion = viewModel::setReduceMotion,
                    hiddenApps = uiState.allApps.filter { it.packageName in settings.hiddenApps },
                    onUnhideApp = { viewModel.setAppHidden(it, false) },
                    onFrameSource = viewModel::setFrameSource,
                    onPickFrameFolder = pickFrameFolder,
                    onPickFramePhoto = pickFramePhoto,
                    onFrameInterval = viewModel::setFrameInterval,
                    onFrameAutoStart = viewModel::setFrameAutoStart,
                    onFrameClock = viewModel::setFrameClock,
                    onFrameClockPosition = viewModel::setFrameClockPosition,
                    onFrameClockSize = viewModel::setFrameClockSize,
                    onFrameShowDate = viewModel::setFrameShowDate,
                    onFrameMotion = viewModel::setFrameMotion,
                    onFrameShuffle = viewModel::setFrameShuffle,
                    onUseFrameArtWallpaper = viewModel::setUseFrameArtWallpaper,
                    onOpenAccessibilitySettings = { HomeSetup.openAccessibilitySettings(context) },
                    onChooseHomeApp = chooseHomeApp,
                    onClose = { showSettings = false },
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top bar rises up and off the top — driven by BOTH the frame transition and an app
                    // launch (topBarLaunch), so launching an app uses the same chrome choreography.
                    Box(
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            val p = maxOf(topBarProgress.value, topBarLaunch.value)
                            translationY = -p * (size.height + 48f)
                            alpha = 1f - (p * 1.7f).coerceAtMost(1f)
                        },
                    ) {
                        TopBar(
                            onOpenSettings = { showSettings = true },
                            onEnterFrame = { frameOn = true },
                            tuneFocus = tuneFocus,
                            backdrop = backdrop,
                            // Keep the glass live through the Frame Art transition (the big full-screen
                            // backdrop capture is already skipped while framePartly), so the chrome stays
                            // frosted as it slides out and re-captures immediately as it returns. Only an
                            // app-launch/return zoom freezes it (drawing the last frosted slice).
                            glassLive = !transitioning,
                            // Full quality (edge refraction) only at rest; drop it while anything moves.
                            glassRefract = !transitioning && !frameMoving,
                            glassBlur = settings.glassBlur,
                        )
                    }
                    // Dock + grid scale up and drop off the bottom — same choreography for frame mode and
                    // for app launch (so the two share one motion language).
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                val p = maxOf(dockProgress.value, dockLaunch.value)
                                val s = 1f + 0.28f * p
                                scaleX = s
                                scaleY = s
                                translationY = p * size.height * 0.55f
                                alpha = 1f - (p * 1.7f).coerceAtMost(1f)
                                transformOrigin = TransformOrigin(0.5f, 0.85f)
                            },
                    ) {
                        when {
                            uiState.isLoading -> Centered { Text("Loading apps…", color = colors.text, fontSize = 20.sp) }
                            uiState.allApps.isEmpty() -> Centered { Text("No apps found", color = colors.text, fontSize = 20.sp) }
                            else -> LauncherContent(
                                dockApps = uiState.dockApps,
                                gridApps = visibleGrid,
                                iconLoader = container.iconLoader,
                                onAppFocused = viewModel::onAppFocused,
                                onAppClicked = { pkg -> launchApp(pkg) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onReorder = viewModel::setFavoritesOrder,
                                columns = settings.columns,
                                backdrop = backdrop,
                                topFocusRequester = tuneFocus,
                                onFavoriteHover = { favoriteHover = it },
                                reduceMotion = settings.reduceMotion,
                                onHideApp = { viewModel.setAppHidden(it, true) },
                                onAppInfo = { viewModel.openAppInfo(it) },
                                onUninstall = { viewModel.uninstallApp(it) },
                                glassLive = !transitioning,
                                glassRefract = !transitioning && !frameMoving,
                                glassBlur = settings.glassBlur,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        if (showPicker) {
            ImagePickerDialog(onPick = { applyPickedImage(it) }, onDismiss = { showPicker = false })
        }

        if (showFolderPicker) {
            FolderPickerDialog(
                onPick = { id, name ->
                    showFolderPicker = false
                    viewModel.setFrameFolder(id, name)
                },
                onDismiss = { showFolderPicker = false },
            )
        }

        if (showTvProbe) {
            TvProbeDialog(onDismiss = { showTvProbe = false })
        }

        // Transparent focus trap, on top while Frame Art is showing: it owns D-pad focus so the grid
        // (still composed during the entry/exit transition, removed once settled) can't be navigated
        // or clicked behind the art. The root key handler above turns the first key into "wake", so
        // this only needs to hold focus — it has no key logic of its own.
        if (frameOn || framePartly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(frameCaptureFocus)
                    .focusable(),
            )
        }
    }
    }
}

@Composable
private fun TopBar(
    onOpenSettings: () -> Unit,
    onEnterFrame: () -> Unit,
    tuneFocus: FocusRequester,
    backdrop: GraphicsLayer,
    glassLive: Boolean,
    glassRefract: Boolean,
    glassBlur: Boolean,
) {
    val context = LocalContext.current
    val net = rememberNetStatus()
    val colors = LocalLauncherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Each free-floating element is its own frosted-glass container, so text stays legible over
        // any wallpaper without scrimming the whole image.
        Clock(
            modifier = Modifier
                .frostedGlass(backdrop, RoundedCornerShape(18.dp), tint = if (glassBlur) colors.textBackdrop else colors.textBackdropOpaque, live = glassLive, refract = glassRefract, blur = glassBlur)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // Status pill: Wi-Fi indicator + Android settings + launcher (tune) settings.
        Row(
            modifier = Modifier
                .frostedGlass(backdrop, RoundedCornerShape(percent = 50), tint = if (glassBlur) colors.textBackdrop else colors.textBackdropOpaque, live = glassLive, refract = glassRefract, blur = glassBlur)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(onClick = { openWifiSettings(context) }, contentDescription = "Wi-Fi settings") {
                WifiIndicator(status = net, tint = colors.text, modifier = Modifier.size(22.dp))
            }
            PillButton(onClick = onEnterFrame, contentDescription = "Frame Art") {
                Image(
                    painterResource(R.drawable.ic_frame),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(colors.text),
                )
            }
            PillButton(
                onClick = onOpenSettings,
                contentDescription = "Launcher settings",
                modifier = Modifier.focusRequester(tuneFocus),
            ) {
                Image(
                    painterResource(R.drawable.ic_tune),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(colors.text),
                )
            }
            PillButton(onClick = { openAndroidSettings(context) }, contentDescription = "Android settings") {
                Image(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(colors.text),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PillButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalLauncherColors.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = colors.text.copy(alpha = 0.22f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

private fun openWifiSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openAndroidSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * Renders the configured Frame Art (folder slideshow or single photo) — used both as the live home
 * wallpaper and as the full-screen frame. [drift] enables the slow parallax float; [cycle] enables
 * the folder slideshow's advance. The "current wallpaper" source renders nothing (the wallpaper shows
 * through instead).
 */
@Composable
private fun FrameArtContent(
    settings: LauncherSettings,
    drift: Boolean,
    driftAmount: () -> Float,
    cycle: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        settings.frameSource == FrameSource.SINGLE && settings.frameImagePath != null ->
            Box(modifier = modifier.frameParallax(drift, driftAmount)) {
                ImageWallpaper(
                    path = settings.frameImagePath!!,
                    blurred = false,
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize(),
                )
            }

        settings.frameSource == FrameSource.FOLDER && settings.frameFolderId != null ->
            FrameSlideshow(
                folderId = settings.frameFolderId!!,
                intervalSec = settings.frameIntervalSec,
                drift = drift,
                driftAmount = driftAmount,
                cycle = cycle,
                shuffle = settings.frameShuffle,
                modifier = modifier,
            )
    }
}
