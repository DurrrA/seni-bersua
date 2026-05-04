package com.durrr.first.features.settings.domain

data class SettingsSyncState(
    val busy: Boolean = false,
    val savedMessage: String? = null,
    val syncMessage: String? = null,
)
