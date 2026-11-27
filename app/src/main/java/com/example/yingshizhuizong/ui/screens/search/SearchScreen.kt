@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.yingshizhuizong.ui.screens.search

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshizhuizong.data.TmdbApi
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.ui.components.PosterImage
import com.example.yingshizhuizong.ui.components.label
import com.example.yingshizhuizong.util.formatDate

/**
 * 搜索页，两种范围：
 * - 我的看单：直接查数据库（片名 / 原名 / 备注），不受列表筛选影响
 * - 在线搜索：调 TMDB 搜片，支持中文片名，点一下直接加入看单
 */
@Composable
fun SearchScreen(onBack: () -> Unit, onItemClick: (Long) -> Unit) {
    val vm: SearchViewModel = viewModel()
    val query by vm.query.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val localResults by vm.localResults.collectAsStateWithLifecycle()
    val totalCount by vm.totalCount.collectAsStateWithLifecycle()
    val onlineState by vm.online.collectAsStateWithLifecycle()
    val addedIds by vm.addedIds.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    // 进来就聚焦输入框，方便直接打字
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("搜索影片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        if (mode == SearchMode.LOCAL) {
                            "搜索我看单（支持中文、拼音 liulang）"
                        } else {
                            "输入中文 / 英文片名，在线搜索 TMDB"
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    } else {
                        // 模拟器键盘常常打不出中文，给一个一键粘贴入口
                        IconButton(onClick = {
                            val pasted = pasteFromClipboard(context) { text -> vm.onQueryChange(text) }
                            if (!pasted) {
                                Toast.makeText(context, "剪贴板里没有文字", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "粘贴")
                        }
                    }
                },
                singleLine = true
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mode == SearchMode.LOCAL,
                    onClick = { vm.setMode(SearchMode.LOCAL) },
                    label = { Text("我的看单") }
                )
                FilterChip(
                    selected = mode == SearchMode.ONLINE,
                    onClick = { vm.setMode(SearchMode.ONLINE) },
                    label = { Text("在线搜索") }
                )
            }

            if (mode == SearchMode.LOCAL) {
                LocalResults(
                    modifier = Modifier.weight(1f),
                    results = localResults,
                    keyword = query.trim(),
                    totalCount = totalCount,
                    suggestions = suggestions,
                    onPick = { vm.onQueryChange(it) },
                    onItemClick = { onItemClick(it.id) }
                )
            } else {
                OnlineResults(
                    modifier = Modifier.weight(1f),
                    state = onlineState,
                    addedIds = addedIds,
                    keyword = query.trim(),
                    onRetry = { vm.retryOnline() },
                    onAdd = { item ->
                        vm.addToWatchlist(item) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalResults(
    modifier: Modifier,
    results: List<WatchItem>,
    keyword: String,
    totalCount: Int,
    suggestions: List<String>,
    onPick: (String) -> Unit,
    onItemClick: (WatchItem) -> Unit,
) {
    if (keyword.isEmpty()) {
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "输入片名，搜索库里全部 $totalCount 部影片\n片名、原名、备注、拼音都会参与匹配",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "英文键盘也能搜：全拼 liulang，或首字母 lldq",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "点一下直接搜",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(suggestions) { title ->
                        FilterChip(
                            selected = false,
                            onClick = { onPick(title) },
                            label = { Text(title) }
                        )
                    }
                }
            }
        }
        return
    }
    if (results.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "没有找到「$keyword」",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Column(modifier.fillMaxWidth()) {
        Text(
            "找到 ${results.size} 部",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.id }) { item ->
                SearchResultCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun OnlineResults(
    modifier: Modifier,
    state: OnlineState,
    addedIds: Set<Int>,
    keyword: String,
    onRetry: () -> Unit,
    onAdd: (TmdbApi.SearchItem) -> Unit,
) {
    when (state) {
        is OnlineState.Idle -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "输入片名，从 TMDB 在线搜索（支持中文）\n搜到后点一下就能加入看单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        is OnlineState.Loading -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is OnlineState.Empty -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "TMDB 没有搜到「$keyword」",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is OnlineState.Error -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }

        is OnlineState.Results -> Column(modifier.fillMaxWidth()) {
            Text(
                "找到 ${state.items.size} 部 · 点一下加入看单",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { "${it.type}_${it.tmdbId}" }) { item ->
                    OnlineResultCard(
                        item = item,
                        added = item.tmdbId in addedIds,
                        onClick = { if (!addedIds.contains(item.tmdbId)) onAdd(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(item: WatchItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterImage(
                item.posterUrl,
                Modifier.size(width = 48.dp, height = 68.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.originalTitle.isNotBlank()) {
                    Text(
                        item.originalTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                val meta = buildList {
                    add(item.type.label())
                    add(item.category.label)
                    add(item.status.label())
                    item.releaseDate?.let { add(formatDate(it)) }
                    if (item.rating > 0f) add("★ ${item.rating}")
                }.joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 从剪贴板取文字（模拟器里可以从电脑复制中文，再点粘贴） */
private fun pasteFromClipboard(context: Context, onText: (String) -> Unit): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    val clip = clipboard.primaryClip ?: return false
    if (clip.itemCount == 0) return false
    val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
    if (text.isEmpty()) return false
    onText(text)
    return true
}

@Composable
private fun OnlineResultCard(
    item: TmdbApi.SearchItem,
    added: Boolean,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterImage(
                item.posterUrl,
                Modifier.size(width = 56.dp, height = 80.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.originalTitle.isNotBlank() && item.originalTitle != item.title) {
                    Text(
                        item.originalTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                val meta = buildList {
                    add(item.type.label())
                    add(item.category.label)
                    item.releaseDate?.let { add(formatDate(it)) }
                    if (item.rating > 0f) add("TMDB ${"%.1f".format(item.rating)}")
                }.joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.overview.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (added) "已添加" else "添加",
                style = MaterialTheme.typography.labelLarge,
                color = if (added) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}
