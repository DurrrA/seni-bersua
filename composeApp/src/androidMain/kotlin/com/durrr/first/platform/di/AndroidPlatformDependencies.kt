package com.durrr.first.platform.di

import android.content.Context
import androidx.compose.runtime.Composable
import com.durrr.first.rememberAppDependencies
import com.durrr.first.ui.AppDependencies

@Composable
fun rememberAndroidPlatformDependencies(
    context: Context,
    launchScanner: () -> Unit,
    pickImage: ((String?) -> Unit) -> Unit = { onPicked -> onPicked(null) },
): AppDependencies {
    return rememberAppDependencies(
        context = context,
        launchScanner = launchScanner,
        pickImage = pickImage,
    )
}
