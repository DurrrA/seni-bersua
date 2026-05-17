package com.durrr.first

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.durrr.first.ui.notification.AppNotification
import com.durrr.first.ui.notification.AppNotificationLevel

class MainViewModel : ViewModel() {
    var lastScannedToken by mutableStateOf<String?>(null)
        private set
    var loggedIn by mutableStateOf(false)
        private set
    var notifications by mutableStateOf<List<AppNotification>>(emptyList())
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
        notifications = emptyList()
    }

    fun pushNotification(
        title: String,
        message: String,
        level: AppNotificationLevel = AppNotificationLevel.INFO,
    ) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return
        val newItem = AppNotification(
            id = "notif_${System.currentTimeMillis()}_${notifications.size}",
            title = title,
            message = trimmed,
            level = level,
            createdAtMillis = System.currentTimeMillis(),
            read = false,
        )
        notifications = listOf(newItem) + notifications
    }

    fun markAllNotificationsRead() {
        notifications = notifications.map { it.copy(read = true) }
    }

    fun clearNotifications() {
        notifications = emptyList()
    }

    fun unreadNotificationCount(): Int {
        return notifications.count { !it.read }
    }
}
