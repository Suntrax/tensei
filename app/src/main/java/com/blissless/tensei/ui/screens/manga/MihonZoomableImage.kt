package com.blissless.tensei.ui.screens.manga

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import okhttp3.Headers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Tachiyomi/Mihon-inspired zoomable image wrapper (ported 1:1 from oni).
 *
 * Behaviour summary (see inline docstrings for details):
 *  - Pinch zoom 1× → 5×, anchored on the centroid of the two fingers.
 *  - Double-tap toggles between 1× and 2× at the tap point.
 *  - Single-finger drag pans while zoomed in; at 1× it passes through to the
 *    parent so the LazyColumn (vertical reader) or HorizontalPager (paged
 *    reader) can handle it.
 *  - On release, if scale ended up out of bounds, snap back to the nearest
 *    bound and reset offset.
 *
 * Defensive measures against the "black screen" bug previously seen when
 * over-pinching:
 *
 *  - All `Float` math is guarded with `.isNaN()` / `.isInfinite()` checks.
 *    Compose's `calculateCentroid()` can return NaN when the two pointers are
 *    at the same position (e.g., during a fast lift), and that NaN was
 *    propagating through `focalAdjust` into the `graphicsLayer` transform,
 *    producing an invalid matrix and a fully clipped (black) image.
 *
 *  - If `layoutSize` hasn't been measured yet (`Size.Zero`), we refuse to
 *    apply any offset/scale changes — the gesture is dropped on the floor
 *    rather than risking a transform with no bounds. Once layout completes
 *    (typically on the next frame), gestures work normally.
 *
 *  - The snap-back runs whenever the released scale is at a bound OR whenever
 *    the offset/scale ended up NaN/Infinite for any reason — a hard reset to
 *    (1×, 0, 0) is always safe.
 */

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2f
private const val ANIM_DURATION_MS = 220

/** Sentinel: a value used in the gesture loop when we have no usable focal point. */
private val NO_FOCAL = Offset.Zero

@Composable
fun MihonZoomableImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /**
     * If true, the image is scaled to fill the available width (used by the
     * vertical scroll reader so pages span edge-to-edge). If false, the image
     * is fit inside the available bounds preserving aspect ratio (used by the
     * paged reader so a full page is always visible at 1x).
     */
    fillWidth: Boolean = false,
    /**
     * Called on single tap. The paged reader uses this to advance to the next
     * page; the vertical reader can ignore it (or use it to toggle the chrome).
     */
    onSingleTap: ((Offset) -> Unit)? = null,
    /**
     * When false, all gesture handling (tap, double-tap-zoom, pinch, pan) is
     * skipped. The image is displayed without any interactive behaviour. Used
     * by the vertical scroll reader where LazyColumn handles scrolling and
     * zoom gestures would conflict.
     */
    gesturesEnabled: Boolean = true,
    /**
     * Notifies the caller whenever the image's zoom state changes. `true` means
     * the user has zoomed in (scale > 1); `false` means back to 1x. The paged
     * reader uses this to disable HorizontalPager swipe while zoomed, so the
     * user can pan without flipping pages.
     */
    onZoomChanged: ((zoomed: Boolean) -> Unit)? = null,
    /**
     * External double-tap zoom trigger. Incrementing this value from outside
     * triggers the same zoom toggle animation as an actual double-tap. Used by
     * PagedMangaReader where the HorizontalPager's scroll handling intercepts
     * touch events before MihonZoomableImage's detectTapGestures can see them.
     */
    doubleTapTrigger: Int = 0
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Build the ImageRequest with both memory and disk caching disabled.
    //
    // Chapter pages are fetched fresh from the network every time they're
    // scrolled into view — no bitmap memory cache, no on-disk cache. This is
    // a deliberate choice (matching oni): the user explicitly wants zero
    // on-device storage of chapter content.
    //
    // Trade-off: scrolling back through already-seen pages re-downloads them.
    // On a fast connection this is usually imperceptible; on a slow connection
    // there may be a brief placeholder flicker. Preloading of adjacent pages
    // still works (Coil's prefetcher just bypasses the caches too).
    //
    // We deliberately DON'T crossfade here: the existing reader UX expects
    // pages to appear instantly when scrolled into view, and a crossfade on
    // every load would make scrolling feel laggy.
    val imageRequest = remember(imageUrl) {
        val referer = try {
            val url = java.net.URL(imageUrl)
            "${url.protocol}://${url.host}/"
        } catch (_: Exception) { null }
        ImageRequest.Builder(context)
            .data(imageUrl)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .apply {
                if (referer != null) {
                    headers(Headers.headersOf("Referer", referer))
                }
            }
            .build()
    }

    // Scale and offset are stored as plain mutableStateOf rather than Animatable.
    // Rationale: we drive programmatic animations (double-tap, snap-back) via
    // `animate()`, whose per-frame lambda is NOT a coroutine context — so we
    // can't call `Animatable.snapTo()` from inside it (it's a suspend function).
    // Plain mutableStateOf can be written from any context, and writing to it
    // from the animate lambda recomposes the graphicsLayer exactly as needed.
    //
    // Trade-off: we lose Animatable's "auto-cancel previous animation when a
    // new snapTo is called" behaviour. We compensate by tracking the currently
    // running animation Job and cancelling it before starting a new one — see
    // `animatedJob` below.
    var scale by remember { mutableStateOf(MIN_ZOOM) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Tracks the currently-running programmatic animation (double-tap or
    // snap-back). Cancelled when a new gesture starts, so the user's pinch
    // always takes precedence over a half-finished animation.
    var animatedJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun cancelRunningAnimation() {
        animatedJob?.cancel()
        animatedJob = null
    }

    // Tracks the actual laid-out size of the composable, used to compute pan
    // clamps. Updated via onSizeChanged below. Stays Size.Zero until the first
    // layout pass completes.
    var layoutSize by remember { mutableStateOf(Size.Zero) }

    // Tracks whether the most recent single-finger gesture was primarily
    // vertical. Used to prevent fast top-to-bottom swipes from being
    // misinterpreted as taps by detectTapGestures (which would trigger
    // page navigation in the paged reader).
    var lastGestureWasVertical by remember { mutableStateOf(false) }

    // Notify the caller when zoom state crosses the 1× boundary. We use a
    // snapshotFlow so this only fires on actual state changes, not on every
    // frame of a pinch animation.
    LaunchedEffect(onZoomChanged) {
        snapshotFlow { scale > 1.01f }
            .distinctUntilChanged()
            .collect { zoomed -> onZoomChanged?.invoke(zoomed) }
    }

    /**
     * @return true if the layout has been measured and we can safely compute
     * pan/zoom math. Before this returns true, gesture updates are dropped.
     */
    fun isReady(): Boolean = layoutSize.width > 0f && layoutSize.height > 0f

    /**
     * Compute the maximum pan offset in each direction such that the image
     * cannot be dragged outside the visible bounds. At scale <= 1 the max is 0
     * (no panning). At scale > 1 the max grows linearly with (scale - 1) * half-size.
     */
    fun panBounds(s: Float): Pair<Float, Float> {
        if (s <= 1f || !isReady()) return 0f to 0f
        val maxX = (layoutSize.width * (s - 1f)) / 2f
        val maxY = (layoutSize.height * (s - 1f)) / 2f
        return maxX.coerceAtLeast(0f) to maxY.coerceAtLeast(0f)
    }

    fun clampOffset(x: Float, y: Float, s: Float): Pair<Float, Float> {
        val (maxX, maxY) = panBounds(s)
        return x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)
    }

    /**
     * Adjust the offset so that the given focal point (in layout coordinates)
     * stays at the same on-screen position after a scale change. This is the
     * core of "focal-point zoom".
     *
     * Math: given a focal point F in layout coords, the on-screen position of F
     * is `center + offset + (F - center) * scale`. To keep that constant when
     * scale changes from s0 to s1, we need:
     *     offset1 = offset0 + (F - center) * (s0 - s1)
     */
    fun focalAdjust(focal: Offset, s0: Float, s1: Float, currentOffset: Offset): Offset {
        // Bail out on anything weird — better to skip the focal adjustment than
        // to propagate NaN into the graphicsLayer transform.
        if (focal.x.isNaN() || focal.y.isNaN() ||
            s0.isNaN() || s1.isNaN() ||
            currentOffset.x.isNaN() || currentOffset.y.isNaN()) {
            return currentOffset
        }
        val center = Offset(layoutSize.width / 2f, layoutSize.height / 2f)
        val delta = (focal - center) * (s0 - s1)
        return Offset(currentOffset.x + delta.x, currentOffset.y + delta.y)
    }

    /**
     * Hard reset to the 1× identity state. Used as a safety net whenever the
     * transform ends up in an invalid configuration.
     */
    fun resetTransform() {
        cancelRunningAnimation()
        scale = MIN_ZOOM
        offsetX = 0f
        offsetY = 0f
    }

    // External double-tap zoom trigger: toggles between 1x and DOUBLE_TAP_ZOOM
    // centred on the middle of the viewport.
    LaunchedEffect(doubleTapTrigger) {
        if (doubleTapTrigger == 0) return@LaunchedEffect
        if (!isReady()) return@LaunchedEffect
        cancelRunningAnimation()
        animatedJob = scope.launch {
            val s0 = scale
            val ox0 = offsetX
            val oy0 = offsetY
            val centre = Offset(layoutSize.width / 2f, layoutSize.height / 2f)
            val s1: Float
            val ox1: Float
            val oy1: Float
            if (s0 > 1.01f) {
                s1 = MIN_ZOOM; ox1 = 0f; oy1 = 0f
            } else {
                s1 = DOUBLE_TAP_ZOOM
                val target = focalAdjust(centre, s0, s1, Offset(ox0, oy0))
                val (cx, cy) = clampOffset(target.x, target.y, s1)
                ox1 = cx; oy1 = cy
            }
            animate(
                initialValue = 0f, targetValue = 1f,
                animationSpec = tween(ANIM_DURATION_MS)
            ) { p, _ ->
                scale = s0 + (s1 - s0) * p
                offsetX = ox0 + (ox1 - ox0) * p
                offsetY = oy0 + (oy1 - oy0) * p
            }
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { intSize ->
                layoutSize = intSize.toSize()
            }
            // Tap detector — always active so double-tap-to-zoom works even
            // when gesturesEnabled is false (vertical scroll reader).
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap ->
                        if (!lastGestureWasVertical) {
                            onSingleTap?.invoke(tap)
                        }
                    },
                    onDoubleTap = { tap ->
                        if (!isReady()) return@detectTapGestures
                        cancelRunningAnimation()
                        animatedJob = scope.launch {
                            val s0 = scale
                            val ox0 = offsetX
                            val oy0 = offsetY
                            val s1: Float
                            val ox1: Float
                            val oy1: Float
                            if (s0 > 1.01f) {
                                s1 = MIN_ZOOM; ox1 = 0f; oy1 = 0f
                            } else {
                                s1 = DOUBLE_TAP_ZOOM
                                val target = focalAdjust(tap, s0, s1, Offset(ox0, oy0))
                                val (cx, cy) = clampOffset(target.x, target.y, s1)
                                ox1 = cx; oy1 = cy
                            }
                            animate(
                                initialValue = 0f, targetValue = 1f,
                                animationSpec = tween(ANIM_DURATION_MS)
                            ) { progress, _ ->
                                scale = s0 + (s1 - s0) * progress
                                offsetX = ox0 + (ox1 - ox0) * progress
                                offsetY = oy0 + (oy1 - oy0) * progress
                            }
                        }
                    }
                )
            }
            // Pinch/pan gesture handler — only active when gestures are enabled
            // (paged reader). In vertical-scroll mode this is skipped so
            // pinch/pan don't conflict with LazyColumn scrolling.
            .then(
                if (gesturesEnabled) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var totalDragDistance = 0f
                            var totalHorizontalDistance = 0f
                            var totalVerticalDistance = 0f
                            var isDragging = false
                            do {
                                val event = awaitPointerEvent()
                                val pointers = event.changes.count { it.pressed }
                                val panChange = event.calculatePan()
                                totalDragDistance += kotlin.math.abs(panChange.x) + kotlin.math.abs(panChange.y)
                                totalHorizontalDistance += kotlin.math.abs(panChange.x)
                                totalVerticalDistance += kotlin.math.abs(panChange.y)
                                if (totalDragDistance > 8f) isDragging = true
                                val shouldConsume = pointers >= 2 || (scale > 1.01f && isDragging)
                                if (shouldConsume) {
                                    if (!isReady()) { event.changes.forEach { it.consume() }; continue }
                                    cancelRunningAnimation()
                                    val zoomChange = event.calculateZoom()
                                    val centroidRaw = event.calculateCentroid()
                                    val centroid = if (centroidRaw.x.isFinite() && centroidRaw.y.isFinite()) centroidRaw else NO_FOCAL
                                    val safeZoomChange = if (zoomChange.isFinite() && zoomChange > 0f) zoomChange else 1f
                                    val s0 = scale
                                    val s1 = (s0 * safeZoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    val focalOffset = focalAdjust(focal = centroid, s0 = s0, s1 = s1, currentOffset = Offset(offsetX, offsetY))
                                    val safePanX = if (panChange.x.isFinite()) panChange.x else 0f
                                    val safePanY = if (panChange.y.isFinite()) panChange.y else 0f
                                    val rawOffset = Offset(focalOffset.x + safePanX, focalOffset.y + safePanY)
                                    val (cx, cy) = clampOffset(rawOffset.x, rawOffset.y, s1)
                                    scale = s1; offsetX = cx; offsetY = cy
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            if (!isDragging) {
                                lastGestureWasVertical = totalVerticalDistance > totalHorizontalDistance * 1.5f
                            }
                            val finalScale = scale
                            val finalOffsetX = offsetX
                            val finalOffsetY = offsetY
                            val transformBroken = finalScale.isNaN() || finalScale.isInfinite() ||
                                finalOffsetX.isNaN() || finalOffsetX.isInfinite() ||
                                finalOffsetY.isNaN() || finalOffsetY.isInfinite()
                            when {
                                transformBroken -> resetTransform()
                                finalScale <= MIN_ZOOM + 0.01f -> {
                                    val s0 = finalScale; val ox0 = finalOffsetX; val oy0 = finalOffsetY
                                    animatedJob = scope.launch {
                                        animate(initialValue = 0f, targetValue = 1f, animationSpec = tween(ANIM_DURATION_MS)) { p, _ ->
                                            scale = s0 + (MIN_ZOOM - s0) * p; offsetX = ox0 * (1f - p); offsetY = oy0 * (1f - p)
                                        }
                                    }
                                }
                                finalScale >= MAX_ZOOM - 0.01f -> {
                                    val (cx, cy) = clampOffset(finalOffsetX, finalOffsetY, MAX_ZOOM)
                                    val s0 = finalScale; val ox0 = finalOffsetX; val oy0 = finalOffsetY
                                    animatedJob = scope.launch {
                                        animate(initialValue = 0f, targetValue = 1f, animationSpec = tween(ANIM_DURATION_MS)) { p, _ ->
                                            scale = s0 + (MAX_ZOOM - s0) * p; offsetX = ox0 + (cx - ox0) * p; offsetY = oy0 + (cy - oy0) * p
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .graphicsLayer {
                    // Using the lambda form gives us access to the
                    // GraphicsLayerScope, and importantly Compose will skip
                    // applying the layer entirely if any value is NaN/Infinite
                    // — which is exactly what we want as a final safety net.
                    // (If scale ended up NaN somehow, the layer is skipped and
                    // the image renders at its natural size/position instead
                    // of disappearing.)
                    val s = scale
                    val ox = offsetX
                    val oy = offsetY
                    if (s.isFinite() && s > 0f && ox.isFinite() && oy.isFinite()) {
                        scaleX = s
                        scaleY = s
                        translationX = ox
                        translationY = oy
                    }
                    // else: leave defaults (scaleX = 1, translationX/Y = 0)
                },
            contentScale = if (fillWidth) ContentScale.FillWidth else ContentScale.Fit,
            alignment = Alignment.Center
        )
    }
}
