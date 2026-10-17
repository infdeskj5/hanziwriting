package com.infdesk.hanziwriting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.infdesk.hanziwriting.data.AppDatabase
import com.infdesk.hanziwriting.data.DatabaseInitializer
import com.infdesk.hanziwriting.ui.MainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            
            // Trigger database initialization on first launch
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.getDatabase(context).hanziDao()
                    DatabaseInitializer.initializeIfNeeded(context, dao)
                }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}
