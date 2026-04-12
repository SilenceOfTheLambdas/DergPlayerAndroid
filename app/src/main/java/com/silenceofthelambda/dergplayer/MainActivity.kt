package com.silenceofthelambda.dergplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.silenceofthelambda.dergplayer.api.AuthManager
import com.silenceofthelambda.dergplayer.api.YouTubeClient
import com.silenceofthelambda.dergplayer.ui.DergPlayerApp
import com.silenceofthelambda.dergplayer.ui.PlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val authManager = AuthManager(this)
        
        setContent {
            var isAuthorized by remember { mutableStateOf(authManager.isAuthorized()) }
            var refreshTrigger by remember { mutableIntStateOf(0) }
            
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
                    if (accountName != null) {
                        authManager.setAccountName(accountName)
                        isAuthorized = true
                    } else {
                        // This could be the result of a recovery intent
                        refreshTrigger++
                    }
                }
            }

            if (isAuthorized) {
                val youtubeClient = remember { YouTubeClient(this, authManager.credential) }
                
                LaunchedEffect(youtubeClient) {
                    youtubeClient.authRecoveryIntents.collect { intent ->
                        launcher.launch(intent)
                    }
                }

                val playerViewModel: PlayerViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return PlayerViewModel(application, youtubeClient) as T
                        }
                    }
                )
                DergPlayerApp(
                    viewModel = playerViewModel, 
                    youtubeClient = youtubeClient,
                    refreshTrigger = refreshTrigger
                )
            } else {
                // Show login screen or trigger account picker
                LaunchedEffect(Unit) {
                    launcher.launch(authManager.getPickAccountIntent())
                }
            }
        }
    }
}
