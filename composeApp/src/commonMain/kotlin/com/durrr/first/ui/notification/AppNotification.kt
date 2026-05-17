package com.durrr.first.ui.notification

enum class AppNotificationLevel {
    INFO,
    WARNING,
    ERROR,
}

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val level: AppNotificationLevel = AppNotificationLevel.INFO,
    val createdAtMillis: Long,
    val read: Boolean = false,
)
