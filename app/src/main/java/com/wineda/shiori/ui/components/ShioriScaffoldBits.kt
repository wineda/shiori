package com.wineda.shiori.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wineda.shiori.navigation.ShioriDestination
import com.wineda.shiori.ui.theme.ShioriColors

@Composable
fun ShioriTopBar(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, color = ShioriColors.InkMute, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) }
        }
        action?.invoke()
    }
}

@Composable
fun ShioriCard(modifier: Modifier = Modifier, content: @Composable Column.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ShioriColors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(24.dp),
    ) { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
fun BatonCard(text: String?) {
    ShioriCard {
        Text("昨日からのバトン", color = ShioriColors.InkMute, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text(text ?: "まだバトンはありません", color = if (text == null) ShioriColors.InkFaint else ShioriColors.Ink)
    }
}

@Composable
fun StreakIndicator(days: List<Boolean>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { filled -> Text(if (filled) "●" else "○", color = if (filled) ShioriColors.Accent else ShioriColors.InkFaint) }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = ShioriColors.InkMute)
    }
}

@Composable
fun ShioriTabBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val tabs = listOf(
        TabSpec(ShioriDestination.Home.route, "Home", Icons.Filled.Home),
        TabSpec(ShioriDestination.Memo.route, "Memo", Icons.Filled.ModeComment),
        TabSpec(ShioriDestination.Archive.route, "Archive", Icons.AutoMirrored.Filled.Article),
        TabSpec(ShioriDestination.Export.route, "Export", Icons.Filled.IosShare),
    )
    NavigationBar(containerColor = ShioriColors.Card) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onNavigate(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

@Composable
fun TagPill(text: String, color: Color, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else color.copy(alpha = 0.25f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = ShioriColors.Ink,
    )
}
