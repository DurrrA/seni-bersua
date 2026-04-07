package com.durrr.first.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlatformUriImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
)
