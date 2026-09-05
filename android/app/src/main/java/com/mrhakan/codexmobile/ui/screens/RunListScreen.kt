package com.mrhakan.codexmobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrhakan.codexmobile.data.CodexTask
import com.mrhakan.codexmobile.ui.formatTimestamp
import com.mrhakan.codexmobile.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunListScreen(
    tasks: List<CodexTask>,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Runs") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("No tasks yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Submit one from the New task tab and it will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(tasks, key = { it.id }) { task ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(task.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        task.prompt.lineSequence().first(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${task.status.label()} · ${task.targetRepo} · " +
                            formatTimestamp(task.createdAtEpochMillis),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    task.pullRequestUrl?.let {
                        Text("Pull request ready", style = MaterialTheme.typography.labelMedium)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
