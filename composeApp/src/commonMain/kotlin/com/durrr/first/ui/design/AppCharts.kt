package com.durrr.first.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.durrr.first.domain.model.RecapChartPoint

@Composable
fun AppBarChart(
    points: List<RecapChartPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        AppEmptyState(
            title = "No chart data",
            message = "No data for selected period.",
            modifier = modifier,
        )
        return
    }

    val max = points.maxOf { it.total }.coerceAtLeast(1L).toFloat()
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(top = Dimens.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            points.forEach { point ->
                val ratio = (point.total.toFloat() / max).coerceIn(0f, 1f)
                Column(
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(18.dp)
                            .height(140.dp),
                    ) {
                        val barHeight = size.height * ratio
                        drawRoundRect(
                            color = barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - barHeight),
                            size = androidx.compose.ui.geometry.Size(size.width, barHeight),
                            cornerRadius = CornerRadius(8f, 8f),
                        )
                    }
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = "Total by selected period",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
    }
}
