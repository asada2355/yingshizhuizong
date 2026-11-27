@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.yingshizhuizong.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.yingshizhuizong.data.TrailerLink
import com.example.yingshizhuizong.data.entity.Note
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.ui.components.PosterImage
import com.example.yingshizhuizong.ui.components.label
import com.example.yingshizhuizong.util.formatDate
import com.example.yingshizhuizong.util.openYouTube

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onPlayTrailer: (Long) -> Unit,
) {
    val vm: DetailViewModel = viewModel()
    val item by vm.item.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val trailerLoading by vm.trailerLoading.collectAsStateWithLifecycle()
    val trailerLookupFailed by vm.trailerLookupFailed.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val openTrailerExternally by vm.openTrailerExternally.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddNote by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(item?.title ?: "详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { item?.let { onEdit(it.id) } }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
            )
        }
    ) { padding ->
        item?.let { current ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { InfoCard(current) }
                item { OverviewCard(details) }
                item {
                    TrailerCard(
                        item = current,
                        loading = trailerLoading,
                        lookupFailed = trailerLookupFailed,
                        onPlay = {
                            val youtubeId = current.trailerKey.ifBlank {
                                TrailerLink.youtubeId(current.trailerUrl).orEmpty()
                            }
                            if (openTrailerExternally && youtubeId.isNotBlank()) {
                                openYouTube(context, youtubeId)
                            } else {
                                onPlayTrailer(current.id)
                            }
                        },
                        onRetry = vm::retryTrailerLookup,
                    )
                }
                item { CreditsCard(details, vm::retryDetails) }
                item { ReminderCard(current.reminderEnabled, vm::toggleReminder) }
                item { NoteSection(notes, vm::deleteNote, onAdd = { showAddNote = true }) }
            }
        }
    }

    if (showAddNote) {
        AddNoteDialog(
            onDismiss = { showAddNote = false },
            onSave = { content, episode ->
                vm.addNote(content, episode)
                showAddNote = false
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除该影片？") },
            text = { Text("删除后其观影笔记也会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteItem(onBack)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun InfoCard(item: WatchItem) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            PosterImage(
                item.posterUrl,
                Modifier.width(100.dp).height(142.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
                if (item.originalTitle.isNotBlank()) {
                    Text(
                        item.originalTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                InfoRow("类型", item.type.label())
                InfoRow("分类", item.category.label)
                if (item.releaseDate != null) InfoRow("上映", formatDate(item.releaseDate))
                if (item.rating > 0f) InfoRow("评分", "★ ${item.rating}")
                if (item.comment.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(item.comment, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            Modifier.width(56.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 预告片卡片：有封面就显示 YouTube 缩略图（没有则退回海报），点击进入播放页。
 * 没有预告片时点击会重新查找，也可以去编辑页手动填写链接。
 */
@Composable
private fun TrailerCard(
    item: WatchItem,
    loading: Boolean,
    lookupFailed: Boolean,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
) {
    val manualUrl = item.trailerUrl.trim()
    val youtubeId = TrailerLink.youtubeId(manualUrl) ?: item.trailerKey.ifBlank { null }
    val playable = manualUrl.isNotEmpty() || item.trailerKey.isNotBlank()
    val thumbnail = when {
        youtubeId != null -> TrailerLink.thumbnail(youtubeId)
        item.posterUrl.isNotBlank() -> item.posterUrl
        else -> null
    }
    val subtitle = when {
        loading -> "正在从 TMDB 查找预告片…"
        playable && item.trailerName.isNotBlank() -> item.trailerName
        playable && manualUrl.isNotEmpty() -> "使用手动填写的链接"
        playable -> "点击播放"
        lookupFailed -> "没找到（可能网络访问不了 TMDB），可在编辑页填写链接"
        else -> "点击查找预告片"
    }

    Card(
        onClick = { if (playable) onPlay() else if (!loading) onRetry() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = "预告片封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                }
                when {
                    loading -> CircularProgressIndicator(color = Color.White)
                    playable -> Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = "播放预告片",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                    else -> Icon(
                        Icons.Filled.Movie,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (playable || loading) "预告片" else "暂无预告片",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (playable) {
                    TextButton(onClick = onPlay) { Text("播放") }
                } else if (!loading) {
                    TextButton(onClick = onRetry) { Text("查找") }
                }
            }
        }
    }
}

/** 简介卡片：显示 TMDB 的剧情简介 */
@Composable
private fun OverviewCard(state: DetailsUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("简介", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            when (state) {
                is DetailsUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "正在获取简介…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is DetailsUiState.Ready -> Text(
                    state.details.overview.ifBlank { "暂无简介" },
                    style = MaterialTheme.typography.bodyMedium
                )
                is DetailsUiState.Unavailable -> Text(
                    "暂时获取不到简介（网络访问不了 TMDB，或 TMDB 未收录）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 演职员卡片：导演 / 制片 / 主要演员 */
@Composable
private fun CreditsCard(state: DetailsUiState, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "演职员",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (state is DetailsUiState.Unavailable) {
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
            when (state) {
                is DetailsUiState.Loading -> {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "正在获取演职员…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is DetailsUiState.Unavailable -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "获取失败（可能网络访问不了 TMDB）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is DetailsUiState.Ready -> {
                    val info = state.details
                    if (info.directors.isEmpty() && info.producers.isEmpty() && info.cast.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "暂无演职员信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        if (info.directors.isNotEmpty()) {
                            CreditLine("导演", info.directors.joinToString("、"))
                            Spacer(Modifier.height(4.dp))
                        }
                        if (info.producers.isNotEmpty()) {
                            CreditLine("制片", info.producers.joinToString("、"))
                            Spacer(Modifier.height(4.dp))
                        }
                        if (info.cast.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "主要演员",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            info.cast.forEach { member ->
                                Row(Modifier.padding(vertical = 2.dp)) {
                                    Text(member.name, style = MaterialTheme.typography.bodyMedium)
                                    if (member.character.isNotBlank()) {
                                        Text(
                                            " 饰 ${member.character}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun CreditLine(label: String, value: String) {
    Row {
        Text(
            label,
            Modifier.width(56.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReminderCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("上映提醒", style = MaterialTheme.typography.titleMedium)
                Text(
                    "上映前会发送通知提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun NoteSection(
    notes: List<Note>,
    onDelete: (Note) -> Unit,
    onAdd: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "观影笔记",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAdd) { Text("添加") }
            }
            if (notes.isEmpty()) {
                Text(
                    "还没有笔记",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                notes.forEach { note ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (note.episode.isNotBlank()) {
                                Text(
                                    "第${note.episode}集 ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                note.content,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDelete(note) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除笔记",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            formatDate(note.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AddNoteDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var content by remember { mutableStateOf("") }
    var episode by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加笔记") },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = episode,
                    onValueChange = { episode = it },
                    label = { Text("集数 / 进度（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(content, episode) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
