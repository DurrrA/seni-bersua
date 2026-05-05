package com.durrr.first.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ProductImageBanner(
    imageUrl: String?,
    modifier: Modifier = Modifier,
)
