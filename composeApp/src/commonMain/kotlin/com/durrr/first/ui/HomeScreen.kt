package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onNavigate: (AppScreen) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("SuCash POS", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { onNavigate(AppScreen.ORDERS) }) { Text("Orders") }
        Button(onClick = { onNavigate(AppScreen.POS) }) { Text("POS") }
        Button(onClick = { onNavigate(AppScreen.MENU) }) { Text("Menu") }
        Button(onClick = { onNavigate(AppScreen.RECAP) }) { Text("Recap") }
        Button(onClick = { onNavigate(AppScreen.SETTINGS) }) { Text("Settings") }
    }
}
