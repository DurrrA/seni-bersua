package com.durrr.first

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.durrr.first.ui.AppContent
import com.durrr.first.ui.AppDependencies
import com.durrr.first.ui.design.AppTheme

@Composable
fun App(dependencies: AppDependencies? = null) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (dependencies == null) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("SuCash POS")
                    Text("Android build required for full experience.")
                }
            } else {
                AppContent(dependencies)
            }
        }
    }
}
