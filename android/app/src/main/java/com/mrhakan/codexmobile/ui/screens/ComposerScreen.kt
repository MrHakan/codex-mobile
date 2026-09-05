package com.mrhakan.codexmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
fun ComposerScreen(
    state: AppUiState,
    onPromptChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onSelectEnvironment: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    var environmentMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                label = { Text("What should Codex do?") },
                placeholder = { Text("Fix the flaky test in tests/e2e and explain the cause") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
            )

            ExposedDropdownMenuBox(
                expanded = environmentMenuOpen,
                onExpandedChange = { environmentMenuOpen = !environmentMenuOpen },
            ) {
                OutlinedTextField(
                    value = state.selectedEnvironment?.let { it.label ?: it.id }
                        ?: "Choose an environment",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Environment") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = environmentMenuOpen)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = environmentMenuOpen,
                    onDismissRequest = { environmentMenuOpen = false },
                ) {
                    state.environments.forEach { environment ->
                        DropdownMenuItem(
                            text = { Text(environment.label ?: environment.id) },
                            onClick = {
                                onSelectEnvironment(environment.id)
                                environmentMenuOpen = false
                            },
                        )
                    }
                }
            }
            if (state.environmentsLoading) {
                CircularProgressIndicator()
            }
            if (state.environments.isEmpty() && !state.environmentsLoading) {
                Text(
                    "No environments yet. Connect a repository to Codex at " +
                        "chatgpt.com/codex/settings, then refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.branch,
                onValueChange = onBranchChange,
                label = { Text("Branch (optional)") },
                placeholder = { Text("defaults to the environment's branch") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.submitting) "Starting…" else "Start task")
            }

            Text(
                "The task runs in Codex Cloud on your ChatGPT account. You can close the app; " +
                    "it keeps running.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
