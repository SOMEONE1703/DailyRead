package com.someone1703.dailyread.ui

import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (s.selectedCount > 0) {
                            Text(
                                "One a day, you'd finish around ${finishDateLabel(s.selectedCount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { vm.save()?.let(onSaved) },
                            enabled = s.selectedCount > 0,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                when (s.selectedCount) {
                                    0 -> "Select at least one reading"
                                    1 -> "Create plan · 1 day"
                                    else -> "Create plan · ${s.selectedCount} days"
                                }
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            s.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Reading your PDF…", style = MaterialTheme.typography.bodyLarge)
                }
            }
            s.error != null -> MessageState(
                icon = Icons.Filled.ErrorOutline,
                title = "Can't use this PDF",
                message = s.error.orEmpty(),
                modifier = Modifier.padding(padding),
            ) {
                Button(onClick = onBack) { Text("Go back") }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).wrapContentWidth().widthIn(max = MaxContentWidth),
            ) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = s.title,
                            onValueChange = vm::setTitle,
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(24.dp))
                        SectionTitle("How much each day?")
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
                                Text("Pages per day", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { vm.setMode(SplitMode.Pages((pages.perDay - 1).coerceAtLeast(1))) },
                                    enabled = pages.perDay > 1,
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Fewer pages")
                                }
                                Text(
                                    "${pages.perDay}",
                                    Modifier.widthIn(min = 32.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                IconButton(onClick = { vm.setMode(SplitMode.Pages(pages.perDay + 1)) }) {
                                    Icon(Icons.Filled.Add, contentDescription = "More pages")
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        ReminderCard(
                            enabled = s.reminderEnabled,
                            days = s.reminderDays,
                            hour = s.reminderHour,
                            minute = s.reminderMinute,
                            onEnabledChange = { vm.setReminder(it, s.reminderHour, s.reminderMinute) },
                            onDaysChange = vm::setReminderDays,
                            onPickTime = { showTime = true },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(24.dp))
                        SectionTitle("Readings")
                        Text(
                            "Untick anything you don't want to read, like the cover or index.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                itemsIndexed(s.readings) { i, r ->
                    val selected = i in s.included
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { vm.toggle(i) })
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(
                                r.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val meta = listOfNotNull(r.subtitle, pagesLabel(r.startPage, r.endPage), pageCountLabel(r.pageCount))
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

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

private fun bookmarkLevelLabel(depth: Int) = when (depth) {
    1 -> "Chapters"
    2 -> "Sections"
    else -> "Sub-sections"
}

private fun finishDateLabel(days: Int): String =
    LocalDate.now().plusDays(days.toLong() - 1).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
