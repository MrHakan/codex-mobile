package com.mrhakan.codexmobile.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrhakan.codexmobile.ui.AppUiState

@Composable
fun SignInScreen(
    state: AppUiState,
    onSignIn: () -> Unit,
    onOpenUrl: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Codex",
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "Start cloud tasks on your repositories and follow them from your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            val deviceAuth = state.deviceAuth
            if (deviceAuth == null) {
                Button(
                    onClick = onSignIn,
                    enabled = !state.signingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign in with ChatGPT")
                }
                Text(
                    "Uses your ChatGPT account — no API key. Codex Cloud access comes with " +
                        "Plus, Pro, Business and Enterprise plans.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Enter this code on chatgpt.com",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        deviceAuth.userCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 34.sp,
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { onOpenUrl(deviceAuth.verificationUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open the approval page")
                    }
                    Text(
                        deviceAuth.verificationUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CircularProgressIndicator()
                    Text(
                        "Waiting for approval…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text("Start over")
                }
            }
        }
    }
}
