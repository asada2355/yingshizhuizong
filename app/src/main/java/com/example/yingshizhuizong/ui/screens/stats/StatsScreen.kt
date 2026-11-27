@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.yingshizhuizong.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.stats.CategoryCount
import com.example.yingshizhuizong.data.stats.WatchStats
import com.example.yingshizhuizong.data.stats.YearReport
import com.example.yingshizhuizong.ui.components.color
import com.example.yingshizhuizong.ui.components.label

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onItemClick: (Long) -> Unit = {},
) {
    val vm: StatsViewModel = viewModel()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val years by vm.years.collectAsStateWithLifecycle()
    val selectedYear by vm.selectedYear.collectAsStateWithLifecycle()
    val report by vm.yearReport.collectAsStateWithLifecycle()

    LaunchedEffect(years) { vm.ensureSelectedYear() }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("观影统计") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (stats.total == 0) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "还没有观影数据\n先在看单里添加几部影片吧",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@LazyColumn
            }

            item { OverviewCard(stats) }
            item { StatusCard(stats) }
            if (stats.categories.isNotEmpty()) item { CategoryCard(stats.categories, stats.total) }
            item { MonthlyCard(stats) }
            if (stats.ratedCount > 0) item { RatingCard(stats) }
            item {
                YearReportCard(
                    report = report,
                    years = years,
                    selectedYear = selectedYear,
                    onSelectYear = vm::selectYear,
                    onItemClick = onItemClick,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun OverviewCard(stats: WatchStats) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatBlock(
                value = "${stats.total}",
                label = "影片总数",
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                value = "${stats.watchedCount}",
                label = "已看",
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                value = if (stats.averageRating > 0f) "%.1f".format(stats.averageRating) else "—",
                label = "平均评分",
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "电影 ${stats.movieCount} 部 · 剧集 ${stats.tvCount} 部 · 今年已看 ${stats.watchedThisYear} 部",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusCard(stats: WatchStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("状态分布")
            Spacer(Modifier.height(12.dp))
            val entries = listOf(
                WatchStatus.WANT to stats.wantCount,
                WatchStatus.WATCHING to stats.watchingCount,
                WatchStatus.WATCHED to stats.watchedCount,
                WatchStatus.DROPPED to stats.droppedCount,
            )
            entries.forEach { (status, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = status.color().copy(alpha = 0.15f),
                    ) {
                        Text(
                            status.label(),
                            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            color = status.color(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("$count 部", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        percent(count, stats.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(categories: List<CategoryCount>, total: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("分类分布")
            Spacer(Modifier.height(12.dp))
            categories.take(10).forEach { entry ->
                BarRow(
                    label = entry.category.label,
                    value = entry.count,
                    total = total,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (categories.size > 10) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "还有 ${categories.size - 10} 个分类",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarRow(
    label: String,
    value: Int,
    total: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    val fraction = if (total <= 0) 0f else value.toFloat() / total
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.width(56.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(10.dp)
                    .background(color, RoundedCornerShape(50)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$value",
            Modifier.width(32.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthlyCard(stats: WatchStats) {
    val max = stats.months.maxOfOrNull { it.count } ?: 0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("今年月度观影", Modifier.weight(1f))
                Text(
                    "共 ${stats.watchedThisYear} 部",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (max == 0) {
                Text(
                    "今年还没有标记为已看的影片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    stats.months.forEach { month ->
                        Column(
                            Modifier.weight(1f).fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(
                                if (month.count > 0) "${month.count}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(
                                Modifier
                                    .width(14.dp)
                                    .height((8f + 84f * month.count / max).dp)
                                    .background(
                                        if (month.count > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                    ),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${month.month}月",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingCard(stats: WatchStats) {
    val max = stats.ratingBuckets.maxOfOrNull { it.count } ?: 0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("评分分布")
            Spacer(Modifier.height(4.dp))
            Text(
                "已评分 ${stats.ratedCount} 部 · 平均 ${"%.1f".format(stats.averageRating)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(90.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                stats.ratingBuckets.forEach { bucket ->
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            Modifier
                                .width(12.dp)
                                .height((6f + 56f * (if (max == 0) 0f else bucket.count.toFloat() / max)).dp)
                                .background(
                                    if (bucket.count > 0) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${bucket.score}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearReportCard(
    report: YearReport,
    years: List<Int>,
    selectedYear: Int,
    onSelectYear: (Int) -> Unit,
    onItemClick: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("年度报告")
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(years) { year ->
                    FilterChip(
                        selected = year == selectedYear,
                        onClick = { onSelectYear(year) },
                        label = { Text("$year 年") },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (report.watchedCount == 0) {
                Text(
                    "${report.year} 年还没有标记为已看的影片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                "今年你看了 ${report.watchedCount} 部影视作品",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "电影 ${report.movieCount} 部 · 剧集 ${report.tvCount} 部 · 平均评分 ${
                    if (report.averageRating > 0f) "%.1f".format(report.averageRating) else "—"
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            report.topCategory?.let { top ->
                ReportLine("最爱分类", "${top.category.label}（${top.count} 部）")
            }
            report.highestRated?.let { item ->
                ReportLine(
                    label = "最高评分",
                    value = "${item.title} · ${"%.1f".format(item.rating)} 分",
                    onClick = { onItemClick(item.id) },
                )
            }
            if (report.longestWait != null) {
                ReportLine(
                    label = "等待最久",
                    value = "${report.longestWait.title} · 添加 ${report.longestWaitDays} 天后看完",
                    onClick = { onItemClick(report.longestWait.id) },
                )
            }
            val topThree = report.categories.take(3)
            if (topThree.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "分类 Top${topThree.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                topThree.forEachIndexed { index, entry ->
                    Text(
                        "${index + 1}. ${entry.category.label} · ${entry.count} 部",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String, onClick: (() -> Unit)? = null) {
    val clickable = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(clickable)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

private fun percent(count: Int, total: Int): String {
    if (total <= 0) return "0%"
    return "${kotlin.math.round(count * 100.0 / total).toInt()}%"
}