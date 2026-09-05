package com.mrhakan.codexmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrhakan.codexmobile.data.CodexTaskRepository
import com.mrhakan.codexmobile.ui.AppUiState

@Composable
fun SettingsScreen(
    state: AppUiState,
    onControllerRepoChange: (String) -> Unit,
    onWorkflowRefChange: (String) -> Unit,
    onSignOut: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Seeded once from the stored settings; every edit writes straight through.
    var controllerRepo by remember { mutableStateOf(state.controllerRepo) }
    var workflowRef by remember { mutableStateOf(state.workflowRef) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        state.login?.let { Text("Signed in as $it") }

        Text("Cloud runner", style = MaterialTheme.typography.titleMedium)
        Text(
            "The repository that hosts ${CodexTaskRepository.WORKFLOW_FILE}. Tasks for other " +
                "repositories are dispatched here and checked out remotely, which needs a " +
                "CODEX_GH_TOKEN secret on the controller repo.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = controllerRepo,
            onValueChange = {
                controllerRepo = it
                onControllerRepoChange(it)
            },
            label = { Text("Controller repository (owner/repo)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = workflowRef,
            onValueChange = {
                workflowRef = it
                onWorkflowRefChange(it)
            },
            label = { Text("Workflow branch") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            onClick = {
                val repo = state.controllerRepo
                onOpenUrl("https://github.com/$repo/actions/workflows/${CodexTaskRepository.WORKFLOW_FILE}")
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open workflow on GitHub")
        }

        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out")
        }
        Text(
            "Signing out deletes the stored token from this device. Task history is kept.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
