package com.durrr.first

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    var lastScannedToken by mutableStateOf<String?>(null)
        private set
    var loggedIn by mutableStateOf(false)
        private set

    fun setScannedToken(token: String) {
        lastScannedToken = token
    }

    fun clearScannedToken() {
        lastScannedToken = null
    }

    fun markLoggedIn() {
        loggedIn = true
    }

    fun markLoggedOut() {
        loggedIn = false
        lastScannedToken = null
    }
}
