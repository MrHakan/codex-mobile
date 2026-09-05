package com.mrhakan.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrhakan.codexmobile.ui.AppViewModel
import com.mrhakan.codexmobile.ui.CodexApp
import com.mrhakan.codexmobile.ui.theme.CodexMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodexMobileTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val viewModel: AppViewModel =
                        viewModel(factory = AppViewModel.factory(application))
                    CodexApp(viewModel)
                }
            }
        }
    }
}
