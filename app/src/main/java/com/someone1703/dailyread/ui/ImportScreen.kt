package com.someone1703.dailyread.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    uri: Uri,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    vm: ImportViewModel = viewModel(),
) {
    LaunchedEffect(uri) { vm.load(uri) }
    val s by vm.state.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set up reading plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (!s.loading && s.error == null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { vm.save()?.let(onSaved) },
                        enabled = s.selectedCount > 0,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Text("Create plan · ${s.selectedCount} days")
                    }
                }
            }
        },
    ) { padding ->
        when {
            s.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            s.error != null -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(s.error.orEmpty())
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = s.title,
                            onValueChange = vm::setTitle,
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(20.dp))
                        Text("Split into daily readings by", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        val maxDepth = s.info?.maxDepth ?: 0
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (depth in 1..minOf(maxDepth, 3)) {
                                FilterChip(
                                    selected = s.mode == SplitMode.Bookmarks(depth),
                                    onClick = { vm.setMode(SplitMode.Bookmarks(depth)) },
                                    label = { Text(bookmarkLevelLabel(depth)) },
                                )
                            }
                            FilterChip(
                                selected = s.mode is SplitMode.Pages,
                                onClick = { vm.setMode(SplitMode.Pages((s.mode as? SplitMode.Pages)?.perDay ?: 10)) },
                                label = { Text("Fixed pages") },
                            )
                        }
                        if (maxDepth == 0) {
                            Text(
                                "This PDF has no bookmarks, so it's split by page count.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        val pages = s.mode as? SplitMode.Pages
                        if (pages != null) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Pages per day", Modifier.weight(1f))
                                IconButton(onClick = { vm.setMode(SplitMode.Pages((pages.perDay - 1).coerceAtLeast(1))) }) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Fewer pages")
                                }
                                Text("${pages.perDay}", style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { vm.setMode(SplitMode.Pages(pages.perDay + 1)) }) {
                                    Icon(Icons.Filled.Add, contentDescription = "More pages")
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Daily reminder", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Nudges you once a day until you've read",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (s.reminderEnabled) {
                                OutlinedButton(onClick = { showTime = true }) {
                                    Text(timeLabel(s.reminderHour, s.reminderMinute))
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Switch(
                                checked = s.reminderEnabled,
                                onCheckedChange = { vm.setReminder(it, s.reminderHour, s.reminderMinute) },
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Readings · untick any you want to skip (cover, index…)",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    HorizontalDivider()
                }

                itemsIndexed(s.readings) { i, r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggle(i) }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = i in s.included, onCheckedChange = { vm.toggle(i) })
                        Column(Modifier.weight(1f)) {
                            Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(r.subtitle, "${pagesLabel(r.startPage, r.endPage)} · ${r.pageCount}p")
                            Text(
                                meta.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTime) {
        ReminderTimeDialog(
            hour = s.reminderHour,
            minute = s.reminderMinute,
            onDismiss = { showTime = false },
            onConfirm = { h, m ->
                vm.setReminder(true, h, m)
                showTime = false
            },
        )
    }
}

private fun bookmarkLevelLabel(depth: Int) = when (depth) {
    1 -> "Chapters"
    2 -> "Sections"
    else -> "Sub-sections"
}
