package com.mrhakan.codexmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mrhakan.codexmobile.ui.AppUiState

@Composable
fun SignInScreen(
    state: AppUiState,
    onSignInWithToken: (String) -> Unit,
    onStartDeviceFlow: () -> Unit,
    onOpenUrl: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var token by remember { mutableStateOf("") }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Codex Mobile", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Send a task to the Codex cloud agent running on GitHub Actions, " +
                    "then watch it open a pull request.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.deviceFlow != null) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Enter this code on GitHub", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.deviceFlow.userCode,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Button(onClick = { onOpenUrl(state.deviceFlow.verificationUri) }) {
                            Text("Open ${state.deviceFlow.verificationUri}")
                        }
                        Text(
                            "Waiting for authorization…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (state.deviceFlowSupported) {
                Button(
                    onClick = onStartDeviceFlow,
                    enabled = !state.signingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign in with GitHub")
                }
                Text("or", style = MaterialTheme.typography.labelMedium)
            }

            Text("Personal access token", style = MaterialTheme.typography.titleMedium)
            Text(
                "Needs Actions: read and write (to trigger the workflow), Contents: read, " +
                    "and Pull requests: read on the repositories you want to work on. " +
                    "The token is stored in Android's encrypted preferences and never leaves the device " +
                    "except as an Authorization header to api.github.com.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("ghp_… / github_pat_…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSignInWithToken(token) },
                enabled = token.isNotBlank() && !state.signingIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use token")
            }
            OutlinedButton(
                onClick = { onOpenUrl("https://github.com/settings/tokens") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create a token on GitHub")
            }

            if (state.signingIn) {
                CircularProgressIndicator()
            }
        }
    }
}
