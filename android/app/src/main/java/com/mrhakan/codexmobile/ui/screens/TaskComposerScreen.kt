package com.mrhakan.codexmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.mrhakan.codexmobile.ui.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskComposerScreen(
    state: AppUiState,
    onPromptChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onPickRepo: () -> Unit,
    onSelectBranch: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New Codex task", style = MaterialTheme.typography.headlineSmall)

        OutlinedButton(onClick = onPickRepo, modifier = Modifier.fillMaxWidth()) {
            Text(state.selectedRepo ?: "Choose a repository")
        }

        ExposedDropdownMenuBox(
            expanded = branchMenuExpanded,
            onExpandedChange = { branchMenuExpanded = !branchMenuExpanded },
        ) {
            OutlinedTextField(
                value = state.selectedBranch.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Base branch") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = branchMenuExpanded,
                onDismissRequest = { branchMenuExpanded = false },
            ) {
                state.branches.forEach { branch ->
                    DropdownMenuItem(
                        text = { Text(branch) },
                        onClick = {
                            onSelectBranch(branch)
                            branchMenuExpanded = false
                        },
                    )
                }
            }
        }
        if (state.branchesLoading) {
            CircularProgressIndicator()
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChange,
            label = { Text("What should Codex do?") },
            placeholder = { Text("e.g. Add a --version flag to the CLI and cover it with a test") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
        )

        OutlinedButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide advanced" else "Advanced")
        }
        if (showAdvanced) {
            OutlinedTextField(
                value = state.model,
                onValueChange = onModelChange,
                label = { Text("Model (optional, passed to codex exec --model)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Workflow: ${state.controllerRepo} @ ${state.workflowRef}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.submitting) "Dispatching…" else "Run on GitHub Actions")
        }

        Text(
            "The task runs on a GitHub-hosted runner. When Codex changes files, the run " +
                "pushes branch codex/<task id> and opens a pull request.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
