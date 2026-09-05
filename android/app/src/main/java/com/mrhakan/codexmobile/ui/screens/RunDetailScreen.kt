package com.mrhakan.codexmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrhakan.codexmobile.data.CodexTask
import com.mrhakan.codexmobile.ui.formatTimestamp
import com.mrhakan.codexmobile.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    task: CodexTask,
    onRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task.status.label()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove from history")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!task.status.isTerminal) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LabeledValue("Task id", task.id)
            LabeledValue("Repository", task.targetRepo)
            LabeledValue("Base branch", task.baseBranch.ifBlank { "(repository default)" })
            LabeledValue("Working branch", task.branch)
            LabeledValue("Submitted", formatTimestamp(task.createdAtEpochMillis))
            task.conclusion?.let { LabeledValue("Conclusion", it) }
            task.model?.let { LabeledValue("Model", it) }

            task.error?.let { error ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Problem", style = MaterialTheme.typography.titleSmall)
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            task.pullRequestUrl?.let { url ->
                Button(onClick = { onOpenUrl(url) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open pull request")
                }
            }
            task.runUrl?.let { url ->
                OutlinedButton(onClick = { onOpenUrl(url) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open workflow run")
                }
            }
            if (task.status == com.mrhakan.codexmobile.data.TaskStatus.SUCCEEDED &&
                task.pullRequestUrl == null
            ) {
                Text(
                    "The run finished without changing any files, so no pull request was opened.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text("Prompt", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(task.prompt, modifier = Modifier.padding(12.dp))
            }

            if (task.jobSummary.isNotEmpty()) {
                Text("Steps", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        task.jobSummary.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
