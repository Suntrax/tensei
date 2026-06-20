package com.blissless.tensei.ui.screens.character

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.blissless.tensei.MainViewModel
import com.blissless.tensei.data.models.CharacterData
import com.blissless.tensei.data.models.StaffData

private val boldRegex = Regex("__(.+?)__")
private val italicRegex = Regex("_(.+?)_")
private val linkRegex = Regex("\\[(.+?)]\\((.+?)\\)")

private fun formatBioText(text: String, color: Color, primary: Color, animeTitles: Map<String, Int> = emptyMap()): AnnotatedString {
    val cleaned = text
        .replace("<br>", "\n").replace("<br/>", "\n")
        .replace("<b>", "").replace("</b>", "")
        .replace("<i>", "").replace("</i>", "")
        .replace("~", "")
        .replace(Regex("!(\\w+)!"), "$1")

    return buildAnnotatedString {
        var remaining = cleaned
        while (remaining.isNotEmpty()) {
            val boldMatch = boldRegex.find(remaining)
            val italicMatch = italicRegex.find(remaining)
            val linkMatch = linkRegex.find(remaining)

            val candidates = mutableListOf<Pair<Int, Regex>>()
            boldMatch?.let { candidates.add(it.range.first to boldRegex) }
            italicMatch?.let { candidates.add(it.range.first to italicRegex) }
            linkMatch?.let { candidates.add(it.range.first to linkRegex) }

            if (candidates.isEmpty()) {
                appendAnimeTitles(remaining, animeTitles, primary)
                break
            }

            candidates.sortBy { it.first }
            val (_, chosenRegex) = if (candidates.size > 1 && candidates[0].first == candidates[1].first) {
                val bold = candidates.find { it.second == boldRegex }
                bold ?: candidates.first()
            } else {
                candidates.first()
            }
            val match = chosenRegex.find(remaining)!!

            if (match.range.first > 0) {
                appendAnimeTitles(remaining.substring(0, match.range.first), animeTitles, primary)
            }

            when (chosenRegex) {
                boldRegex -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendWithAnimeLinks(match.groupValues[1], animeTitles, primary)
                    pop()
                }
                italicRegex -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendWithAnimeLinks(match.groupValues[1], animeTitles, primary)
                    pop()
                }
                linkRegex -> {
                    val url = match.groupValues[2]
                    pushStringAnnotation("URL", url)
                    pushStyle(SpanStyle(
                        color = primary,
                        textDecoration = TextDecoration.Underline
                    ))
                    append(match.groupValues[1])
                    pop()
                    pop()
                }
            }

            remaining = remaining.substring(match.range.last + 1)
        }
        addStyle(SpanStyle(color = color), 0, length)
    }
}

private fun AnnotatedString.Builder.appendWithAnimeLinks(text: String, animeTitles: Map<String, Int>, primary: Color) {
    if (animeTitles.isEmpty()) {
        append(text)
        return
    }
    val titleMatch = animeTitles.entries.firstOrNull { (title, _) ->
        text.equals(title, ignoreCase = true)
    }
    if (titleMatch != null) {
        pushStringAnnotation("ANIME", titleMatch.value.toString())
        pushStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline))
        append(text)
        pop()
        pop()
    } else {
        append(text)
    }
}

private fun AnnotatedString.Builder.appendAnimeTitles(text: String, animeTitles: Map<String, Int>, primary: Color) {
    if (animeTitles.isEmpty()) {
        append(text)
        return
    }
    var remaining = text
    while (remaining.isNotEmpty()) {
        val match = animeTitles.entries
            .mapNotNull { (title, id) ->
                val idx = remaining.indexOf(title, ignoreCase = true)
                if (idx >= 0) Triple(idx, title, id) else null
            }
            .minByOrNull { it.first }

        if (match == null) {
            append(remaining)
            break
        }
        if (match.first > 0) {
            append(remaining.substring(0, match.first))
        }
        pushStringAnnotation("ANIME", match.third.toString())
        pushStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline))
        append(match.second)
        pop()
        pop()
        remaining = remaining.substring(match.first + match.second.length)
    }
}

@Composable
fun CharacterScreen(
    characterId: Int,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateBack: () -> Unit = onDismiss,
    onAnimeClick: (Int) -> Unit,
    onCharacterClick: ((Int) -> Unit)? = null,
    onStaffClick: ((Int) -> Unit)? = null
) {
    var character by remember { mutableStateOf<CharacterData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()

    LaunchedEffect(characterId) {
        isLoading = true
        character = viewModel.fetchCharacter(characterId)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onNavigateBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            character?.let { char ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + navigationBarsPadding.calculateBottomPadding())
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp + statusBarsPadding.calculateTopPadding())
                        ) {
                            AsyncImage(
                                model = char.image?.large,
                                contentDescription = char.name?.full,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .padding(top = statusBarsPadding.calculateTopPadding() + 8.dp, start = 16.dp)
                                    .align(Alignment.TopStart)
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .zIndex(10f)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val shareText = buildString {
                                        char.name?.full?.let { append(it) }
                                        append("\n\n")
                                        append("https://anilist.co/character/${char.id}")
                                    }
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier
                                    .padding(top = statusBarsPadding.calculateTopPadding() + 8.dp, end = 16.dp)
                                    .align(Alignment.TopEnd)
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .zIndex(10f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = char.name?.full ?: "Unknown",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            char.name?.native?.let { native ->
                                Text(
                                    text = native,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!char.description.isNullOrEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "About",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val charAnimeTitles = char.anime?.nodes?.flatMap { node ->
                                        listOfNotNull(
                                            node.title?.romaji?.let { it to node.id },
                                            node.title?.english?.let { it to node.id }
                                        )
                                    }?.toMap() ?: emptyMap()
                                    val annotatedBio = formatBioText(
                                        char.description,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        MaterialTheme.colorScheme.primary,
                                        charAnimeTitles
                                    )
                                    @Suppress("DEPRECATION")
                                    ClickableText(
                                        text = annotatedBio,
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                        onClick = { offset ->
                                            val animeAnnot = annotatedBio.getStringAnnotations("ANIME", offset, offset).firstOrNull()
                                            if (animeAnnot != null) {
                                                onAnimeClick(animeAnnot.item.toInt())
                                            } else {
                                                val urlAnnot = annotatedBio.getStringAnnotations("URL", offset, offset).firstOrNull()
                                                if (urlAnnot != null) {
                                                    val url = urlAnnot.item
                                                    val charMatch = Regex("anilist\\.co/character/(\\d+)").find(url)
                                                    val staffMatch = Regex("anilist\\.co/staff/(\\d+)").find(url)
                                                    if (charMatch != null) {
                                                        onCharacterClick?.invoke(charMatch.groupValues[1].toInt())
                                                    } else if (staffMatch != null) {
                                                        onStaffClick?.invoke(staffMatch.groupValues[1].toInt())
                                                    } else {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, url.toUri())
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    char.anime?.nodes?.let { animeList ->
                        if (animeList.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Appears In",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(animeList) { anime ->
                                                    Column(
                                                        modifier = Modifier
                                                            .width(100.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable { onAnimeClick(anime.id) },
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Card(
                                                            shape = RoundedCornerShape(12.dp),
                                                            modifier = Modifier.aspectRatio(3f / 4f),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                            )
                                                        ) {
                                                            AsyncImage(
                                                                model = anime.coverImage?.extraLarge,
                                                                contentDescription = anime.title?.romaji,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            anime.title?.english ?: anime.title?.romaji ?: "Unknown",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onBackground,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                        )
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun StaffScreen(
    staffId: Int,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateBack: () -> Unit = onDismiss,
    onAnimeClick: (Int) -> Unit,
    onCharacterClick: ((Int) -> Unit)? = null,
    onStaffClick: ((Int) -> Unit)? = null
) {
    var staff by remember { mutableStateOf<StaffData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()

    LaunchedEffect(staffId) {
        isLoading = true
        loadError = null
        staff = viewModel.fetchStaff(staffId)
        if (staff == null) {
            loadError = "Failed to load staff data"
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onNavigateBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (staff != null) {
                staff?.let { staffData ->
                    LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + navigationBarsPadding.calculateBottomPadding())
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp + statusBarsPadding.calculateTopPadding())
                        ) {
                            AsyncImage(
                                model = staffData.image?.large,
                                contentDescription = staffData.name?.full,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .padding(top = statusBarsPadding.calculateTopPadding() + 8.dp, start = 16.dp)
                                    .align(Alignment.TopStart)
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .zIndex(10f)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val shareText = buildString {
                                        staffData.name?.full?.let { append(it) }
                                        append("\n\n")
                                        append("https://anilist.co/staff/${staffData.id}")
                                    }
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier
                                    .padding(top = statusBarsPadding.calculateTopPadding() + 8.dp, end = 16.dp)
                                    .align(Alignment.TopEnd)
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .zIndex(10f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = staffData.name?.full ?: "Unknown",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            staffData.name?.native?.let { native ->
                                Text(
                                    text = native,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!staffData.description.isNullOrEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Work,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "About",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val staffAnimeTitles = staffData.anime?.edges?.mapNotNull { edge ->
                                        edge.node
                                    }?.flatMap { node ->
                                        listOfNotNull(
                                            node.title?.romaji?.let { it to node.id },
                                            node.title?.english?.let { it to node.id }
                                        )
                                    }?.toMap() ?: emptyMap()
                                    val annotatedBio = formatBioText(
                                        staffData.description,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        MaterialTheme.colorScheme.primary,
                                        staffAnimeTitles
                                    )
                                    @Suppress("DEPRECATION")
                                    ClickableText(
                                        text = annotatedBio,
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                        onClick = { offset ->
                                            val animeAnnot = annotatedBio.getStringAnnotations("ANIME", offset, offset).firstOrNull()
                                            if (animeAnnot != null) {
                                                onAnimeClick(animeAnnot.item.toInt())
                                            } else {
                                                val urlAnnot = annotatedBio.getStringAnnotations("URL", offset, offset).firstOrNull()
                                                if (urlAnnot != null) {
                                                    val url = urlAnnot.item
                                                    val charMatch = Regex("anilist\\.co/character/(\\d+)").find(url)
                                                    val staffMatch = Regex("anilist\\.co/staff/(\\d+)").find(url)
                                                    if (charMatch != null) {
                                                        onCharacterClick?.invoke(charMatch.groupValues[1].toInt())
                                                    } else if (staffMatch != null) {
                                                        onStaffClick?.invoke(staffMatch.groupValues[1].toInt())
                                                    } else {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, url.toUri())
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    staffData.anime?.edges?.let { edges ->
                        if (edges.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Worked On",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(edges) { edge ->
                                                val anime = edge.node
                                                val role = edge.staffRole
                                                if (anime != null) {
                                                    Column(
                                                        modifier = Modifier
                                                            .width(110.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable { onAnimeClick(anime.id) },
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Card(
                                                            shape = RoundedCornerShape(12.dp),
                                                            modifier = Modifier.aspectRatio(3f / 4f),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                            )
                                                        ) {
                                                            AsyncImage(
                                                                model = anime.coverImage?.extraLarge,
                                                                contentDescription = anime.title?.romaji,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            anime.title?.english ?: anime.title?.romaji ?: "Unknown",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onBackground,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                        )
                                                        role?.let {
                                                            Text(
                                                                it,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            } else {
                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = loadError ?: "Could not load staff data",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

