@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.yingshizhuizong.ui.screens.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchItem
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType
import com.example.yingshizhuizong.ui.components.PosterImage
import com.example.yingshizhuizong.ui.components.color
import com.example.yingshizhuizong.ui.components.label
import com.example.yingshizhuizong.util.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onItemClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val vm: WatchlistViewModel = viewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()

    var statusFilter by remember { mutableStateOf<WatchStatus?>(null) }
    val listState = rememberLazyListState()
    // 新条目出现在列表顶部时（例如刚添加完返回），自动滚回顶部
    LaunchedEffect(items) {
        if (vm.shouldScrollToTop(items)) {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("看单") },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { vm.refreshPosters() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新海报")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "添加")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CategoryFilterRow(categories, selectedCategory, vm::selectCategory)
            StatusFilterRow(statusFilter) { statusFilter = it }
            val filtered =
                if (statusFilter == null) items else items.filter { it.status == statusFilter }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (statusFilter != null || selectedCategory != null) {
                            "没有符合筛选条件的影片"
                        } else {
                            "暂无影片，点击右下角添加"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        WatchItemCard(
                            item = item,
                            onClick = { onItemClick(item.id) },
                            onChangeStatus = { vm.setStatus(item, it) },
                            onDelete = { vm.delete(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<WatchCategory>,
    selected: WatchCategory?,
    onSelect: (WatchCategory?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部分类") }
            )
        }
        items(categories, key = { it.name }) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category.label) }
            )
        }
    }
}

@Composable
private fun StatusFilterRow(
    current: WatchStatus?,
    onSelect: (WatchStatus?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        item {
            FilterChip(
                selected = current == null,
                onClick = { onSelect(null) },
                label = { Text("全部状态") }
            )
        }
        items(WatchStatus.entries) { status ->
            FilterChip(
                selected = current == status,
                onClick = { onSelect(status) },
                label = { Text(status.label()) }
            )
        }
    }
}

@Composable
private fun WatchItemCard(
    item: WatchItem,
    onClick: () -> Unit,
    onChangeStatus: (WatchStatus) -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

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
                Spacer(Modifier.height(4.dp))
                val meta = buildList {
                    add(item.type.label())
                    add(item.category.label)
                    if (item.type == WatchType.TV_SERIES && item.seasonNumber > 0) {
                        add("S${item.seasonNumber} E${item.episodeNumber}")
                    }
                    item.releaseDate?.let { add(formatDate(it)) }
                    if (item.rating > 0f) add("★ ${item.rating}")
                }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StatusBadge(item.status)
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                WatchStatus.entries.forEach { status ->
                    DropdownMenuItem(
                        text = { Text("设为${status.label()}") },
                        onClick = {
                            menuOpen = false
                            onChangeStatus(status)
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: WatchStatus) {
    Surface(
        shape = RoundedCornerShape(50),
        color = status.color().copy(alpha = 0.15f)
    ) {
        Text(
            status.label(),
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = status.color(),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
