package com.durrr.first.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
actual fun ProductImageBanner(
    imageUrl: String?,
    modifier: Modifier,
) {
    // iOS fallback for now: keep card layout consistent until remote image loader is added for iosMain.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))),
            ),
    )
}
