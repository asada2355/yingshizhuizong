@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.yingshizhuizong.ui.screens.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshizhuizong.data.entity.WatchCategory
import com.example.yingshizhuizong.data.entity.WatchStatus
import com.example.yingshizhuizong.data.entity.WatchType
import com.example.yingshizhuizong.ui.components.label
import com.example.yingshizhuizong.util.formatDate
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(onBack: () -> Unit) {
    val vm: EditViewModel = viewModel()

    val title by vm.title.collectAsStateWithLifecycle()
    val originalTitle by vm.originalTitle.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val releaseDate by vm.releaseDate.collectAsStateWithLifecycle()
    val rating by vm.rating.collectAsStateWithLifecycle()
    val comment by vm.comment.collectAsStateWithLifecycle()
    val reminderEnabled by vm.reminderEnabled.collectAsStateWithLifecycle()
    val trailerUrl by vm.trailerUrl.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "编辑影片" else "添加影片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.save(onBack) }) { Text("保存") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { vm.title.value = it },
                label = { Text("片名 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = originalTitle,
                onValueChange = { vm.originalTitle.value = it },
                label = { Text("原名（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = trailerUrl,
                onValueChange = { vm.trailerUrl.value = it },
                label = { Text("预告片链接（可选）") },
                placeholder = { Text("YouTube / B 站等视频链接，或 mp4 直链") },
                supportingText = { Text("留空时保存后自动从 TMDB 查找预告片") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            TypeSelector(type) { vm.type.value = it }
            StatusSelector(status) { vm.status.value = it }
            CategorySelector(category) { vm.category.value = it }

            OutlinedTextField(
                value = formatDate(releaseDate),
                onValueChange = {},
                readOnly = true,
                label = { Text("上映日期") },
                placeholder = { Text("选择日期") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "选择日期")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "评分：${if (rating > 0f) rating.toString() else "未评分"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = rating,
                onValueChange = { vm.rating.value = it },
                valueRange = 0f..10f
            )

            OutlinedTextField(
                value = comment,
                onValueChange = { vm.comment.value = it },
                label = { Text("备注") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("开启上映提醒", Modifier.weight(1f))
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { vm.reminderEnabled.value = it }
                )
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = releaseDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val localDate = state.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    vm.setReleaseDate(localDate)
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun TypeSelector(selected: WatchType, onSelect: (WatchType) -> Unit) {
    Column {
        Text("类型", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchType.entries.forEach { t ->
                FilterChip(
                    selected = selected == t,
                    onClick = { onSelect(t) },
                    label = { Text(t.label()) }
                )
            }
        }
    }
}

@Composable
private fun StatusSelector(selected: WatchStatus, onSelect: (WatchStatus) -> Unit) {
    Column {
        Text("状态", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchStatus.entries.forEach { s ->
                FilterChip(
                    selected = selected == s,
                    onClick = { onSelect(s) },
                    label = { Text(s.label()) }
                )
            }
        }
    }
}

@Composable
private fun CategorySelector(selected: WatchCategory, onSelect: (WatchCategory) -> Unit) {
    Column {
        Text("分类", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WatchCategory.entries.forEach { c ->
                FilterChip(
                    selected = selected == c,
                    onClick = { onSelect(c) },
                    label = { Text(c.label) }
                )
            }
        }
    }
}
