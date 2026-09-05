package com.mrhakan.codexmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrhakan.codexmobile.data.CloudTaskThread
import com.mrhakan.codexmobile.data.ThreadMessage
import com.mrhakan.codexmobile.ui.components.DiffStatLabel
import com.mrhakan.codexmobile.ui.components.DiffView
import com.mrhakan.codexmobile.ui.components.StatusDot
import com.mrhakan.codexmobile.ui.components.statusColor
import com.mrhakan.codexmobile.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    thread: CloudTaskThread?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        thread?.title ?: "Task",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (loading || thread?.status?.isRunning == true) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (thread == null) return@Column

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusDot(thread.status)
                Text(
                    thread.status.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor(thread.status),
                )
                DiffStatLabel(thread.diffStat)
            }

            thread.messages.forEach { message -> MessageBubble(message) }

            thread.errorMessage?.let { error ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Task failed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            thread.diff?.let { diff ->
                Text(
                    "Diff",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                )
                DiffView(diff)
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                thread.pullRequestUrl?.let { url ->
                    Button(
                        onClick = { onOpenUrl(url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open pull request")
                    }
                }
                OutlinedButton(
                    onClick = {
                        onOpenUrl(
                            com.mrhakan.codexmobile.data.CodexCloudClient.taskUrl(thread.id),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open in ChatGPT")
                }
                Text(
                    "Follow-up messages and \"Create PR\" live in the web view for now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ThreadMessage) {
    val isUser = message.author == ThreadMessage.Author.USER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (isUser) "You" else "Codex",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            message.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = if (isUser) {
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(12.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        )
    }
}
