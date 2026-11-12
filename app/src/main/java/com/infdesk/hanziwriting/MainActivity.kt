package com.infdesk.hanziwriting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.DatabaseInitializer
import com.infdesk.hanziwriting.ui.HanziWritingApp
import com.infdesk.hanziwriting.ui.HanziWritingTheme
import com.infdesk.hanziwriting.ui.InitializationFailureScreen
import com.infdesk.hanziwriting.ui.LoadingScreen
import com.infdesk.hanziwriting.utils.CrashHandler
import com.infdesk.hanziwriting.utils.DebugLog
import com.infdesk.hanziwriting.utils.ProgressStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.init(this)
        DebugLog.init(this)
        ProgressStore.init(this)

        // Read the quick preference before the first Compose frame. Existing
        // installations can open the main UI immediately without a Preparing flash.
        val initializationRequiredAtLaunch = DatabaseInitializer.needsInitialization(this)

        setContent {
            val context = LocalContext.current
            var isReady by remember { mutableStateOf(!initializationRequiredAtLaunch) }
            var initializationFailed by remember { mutableStateOf(false) }
            var retryToken by remember { mutableIntStateOf(0) }

            LaunchedEffect(initializationRequiredAtLaunch, retryToken) {
                if (initializationRequiredAtLaunch) {
                    isReady = false
                    initializationFailed = false
                    val initialized = DatabaseInitializer.initializeIfNeeded(
                        context,
                        AppDatabase.getDatabase(context)
                    )
                    isReady = initialized
                    initializationFailed = !initialized
                }
            }

            HanziWritingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        isReady -> HanziWritingApp()
                        initializationFailed -> InitializationFailureScreen { retryToken++ }
                        else -> LoadingScreen()
                    }
                }
            }
        }
    }
}
