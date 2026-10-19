package com.infdesk.hanziwriting.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppState {
    var isInitializing by mutableStateOf(true)
    var initMessage by mutableStateOf("Preparing…")
    var initProgress by mutableStateOf(0f)
}
