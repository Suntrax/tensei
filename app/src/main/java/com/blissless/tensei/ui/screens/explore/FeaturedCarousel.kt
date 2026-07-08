package com.blissless.tensei.ui.screens.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.blissless.tensei.data.models.ExploreAnime
import com.blissless.tensei.ui.theme.StatusCompleted
import com.blissless.tensei.ui.theme.StatusCurrent
import com.blissless.tensei.ui.theme.StatusDropped
import com.blissless.tensei.ui.theme.StatusPaused
import com.blissless.tensei.ui.theme.StatusPlanning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import com.blissless.tensei.util.ErrorHandler

@Composable
fun FeaturedCarousel(
    animeList: List<ExploreAnime>,
    onStatusClick: (ExploreAnime) -> Unit,
    onPlayClick: (ExploreAnime) -> Unit,
    onInfoClick: (ExploreAnime) -> Unit,
    onSearchClick: () -> Unit = {},
    animeStatusMap: Map<Int, String> = emptyMap(),
    preferEnglishTitles: Boolean = true,
    isDialogOpen: Boolean = false,
    autoScrollEnabled: Boolean = true,
    isVisible: Boolean = true
) {
    if (animeList.isEmpty()) return

    val actualCount = animeList.size
    val pagerState = rememberPagerState(
        initialPage = actualCount * 100,
        pageCount = { actualCount * 200 }
    )
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var headerVisible by remember { mutableStateOf(true) }
    var pageWhenScrollStarted by remember { mutableIntStateOf(pagerState.currentPage) }
    var isHeaderSwiping by remember { mutableStateOf(false) }
    var timerResetSignal by remember { mutableIntStateOf(0) }

    val currentPageOffsetFraction by remember { derivedStateOf { pagerState.currentPageOffsetFraction } }

    LaunchedEffect(pagerState.isScrollInProgress, currentPageOffsetFraction) {
        if (pagerState.isScrollInProgress) {
            pageWhenScrollStarted = pagerState.currentPage
        } else if (pagerState.currentPage != pageWhenScrollStarted) {
            headerVisible = false
            delay(80.milliseconds)
            headerVisible = true
            pageWhenScrollStarted = pagerState.currentPage
        }
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            timerResetSignal++
        }
    }

    LaunchedEffect(autoScrollEnabled, isVisible, isHeaderSwiping, isDialogOpen, timerResetSignal) {
        if (autoScrollEnabled && isVisible && !isHeaderSwiping && !isDialogOpen) {
            while (true) {
                delay(4500.milliseconds)

                headerVisible = false
                delay(80.milliseconds)
                headerVisible = true
                
                autoScrollJob = scope.launch {
                    try {
                        val targetPage = pagerState.currentPage + 1
                        pagerState.animateScrollToPage(targetPage)
                    } catch (e: Exception) { ErrorHandler.ignore("FeaturedCarousel", "best-effort operation failed", e) }
                }
                autoScrollJob?.join()
                
                delay(300.milliseconds)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { autoScrollJob?.cancel() }
    }

    Box(modifier = Modifier.fillMaxWidth().height(560.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            userScrollEnabled = true,
            beyondViewportPageCount = 0
        ) { page ->
            val anime = animeList[page % actualCount]
            
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(anime.cover)
                        .memoryCacheKey(anime.cover)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Top header with app logo and search icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(start = 20.dp, end = 20.dp, top = 32.dp)
                    .align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = com.blissless.tensei.R.mipmap.ic_launcher_round,
                                    contentDescription = "App",
                                    modifier = Modifier.size(28.dp).clip(CircleShape)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentPage = pagerState.currentPage % actualCount
                            repeat(actualCount) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(if (index == currentPage) 16.dp else 5.dp, 5.dp)
                                        .background(
                                            if (index == currentPage) Color.White
                                            else Color.White.copy(alpha = 0.4f),
                                            RoundedCornerShape(3.dp)
                                        )
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp),
                            onClick = onSearchClick
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).align(Alignment.BottomCenter),
                contentAlignment = Alignment.BottomCenter
            ) {
            val currentAnime by remember {
                derivedStateOf { animeList[pagerState.currentPage % actualCount] }
            }

            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            animationSpec = tween(400, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 2 }
                        ),
                exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                        slideOutVertically(
                            animationSpec = tween(150, easing = FastOutSlowInEasing),
                            targetOffsetY = { it / 2 }
                        )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val displayTitle = (if (preferEnglishTitles && currentAnime.titleEnglish != null) currentAnime.titleEnglish else currentAnime.title) ?: "Unknown"
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val avgScore = currentAnime.averageScore
                        val format = currentAnime.format
                        currentAnime.year?.let { year ->
                            Text(text = year.toString(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                            Text(text = " • ", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                        }
                        val formatText = when (format?.uppercase()) {
                            "MOVIE" -> "Movie"
                            "ONA", "OVA", "TV" -> "Series"
                            else -> "Series"
                        }
                        Text(text = formatText, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                        if (avgScore != null) {
                            Text(text = " • ", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                            val scoreValue = avgScore / 10.0
                            Text(
                                text = "★ ${"%.1f".format(scoreValue)}",
                                color = Color(0xFFFFD700),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentStatus = animeStatusMap[currentAnime.id]
                        val isSaved = currentStatus != null
                        val statusColor = when (currentStatus) {
                            "COMPLETED" -> StatusCompleted
                            "CURRENT" -> StatusCurrent
                            "PLANNING" -> StatusPlanning
                            "PAUSED" -> StatusPaused
                            "DROPPED" -> StatusDropped
                            else -> Color.White
                        }
                        
                        IconButton(
                            onClick = { onStatusClick(currentAnime) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = (if (isSaved) statusColor else Color.White).copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Save",
                                        tint = if (isSaved) statusColor else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        
                        Button(
                            onClick = { onPlayClick(currentAnime) },
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Watch",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Watch Now", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                        
                        IconButton(
                            onClick = { onInfoClick(currentAnime) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = "Info",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

