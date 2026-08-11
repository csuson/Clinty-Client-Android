package com.clinty.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clinty.client.models.ThreadData
import com.clinty.client.models.displayFrom
import com.clinty.client.models.displayTitle
import com.clinty.client.models.formatInstant

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "interrupted" -> Color(0xFFFF9500)
        "busy" -> Color(0xFF007AFF)
        "error" -> Color(0xFFFF3B30)
        "idle" -> Color(0xFF34C759)
        else -> Color.Gray
    }

    Text(
        text = status.replaceFirstChar { it.uppercase() },
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun FromBadge(from: String) {
    Text(
        text = from,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun ThreadRow(threadData: ThreadData) {
    val interrupt = threadData.interrupts?.firstOrNull()
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = interrupt?.displayTitle() ?: threadData.inboxSubject(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            interrupt?.displayFrom()?.let { from ->
                FromBadge(from = from)
            }
        }
        interrupt?.description?.takeIf { it.isNotEmpty() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        threadData.inboxDate()?.let { date ->
            Text(
                text = formatInstant(date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
