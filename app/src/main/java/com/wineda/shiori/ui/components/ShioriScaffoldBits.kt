package com.wineda.shiori.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wineda.shiori.navigation.ShioriDestination
import com.wineda.shiori.ui.theme.ShioriColors

val ShioriScreenBrush = Brush.verticalGradient(listOf(ShioriColors.Paper, ShioriColors.PaperDeep))

@Composable
fun ShioriScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.background(ShioriScreenBrush),
        content = content,
    )
}

@Composable
fun ShioriTopBar(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { leading?.invoke() }
        Column(modifier = Modifier.weight(2f), horizontalAlignment = Alignment.CenterHorizontally) {
            eyebrow?.let { SectionLabel(it, textAlign = TextAlign.Center) }
            Text(title, color = ShioriColors.Ink, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            subtitle?.let { Text(it, color = ShioriColors.InkMute, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { action?.invoke() }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text = text,
        modifier = modifier,
        color = ShioriColors.InkMute,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        letterSpacing = 2.4.sp,
        textAlign = textAlign,
    )
}

@Composable
fun ShioriCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ShioriColors.Card.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, ShioriColors.Divider),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun PrimaryShioriButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ShioriColors.Ink, contentColor = ShioriColors.Paper),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(text, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BatonCard(text: String?, compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 8.dp else 10.dp))
            .background(Brush.linearGradient(listOf(ShioriColors.BatonStart, ShioriColors.BatonEnd)))
            .padding(if (compact) 12.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("◉", color = ShioriColors.Tomorrow, fontSize = if (compact) 11.sp else 14.sp)
            SectionLabel("昨日からのバトン")
        }
        Text(
            text = text?.let { "「$it」" } ?: "まだバトンはありません",
            color = if (text == null) ShioriColors.InkFaint else ShioriColors.InkSoft,
            style = if (compact) androidx.compose.material3.MaterialTheme.typography.bodyMedium else androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        )
        if (!compact && text != null) {
            Row(
                modifier = Modifier.fillMaxWidth().border(BorderStroke(0.dp, Color.Transparent)).padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = ShioriColors.InkMute, modifier = Modifier.size(14.dp))
                Text("受け取った", color = ShioriColors.InkMute, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun StreakIndicator(days: List<Boolean>) {
    val labels = listOf("月", "火", "水", "木", "金", "土", "日")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEachIndexed { index, filled ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (filled) ShioriColors.Good else ShioriColors.Divider),
                )
                Text(labels.getOrElse(index) { "" }, color = ShioriColors.InkMute, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = ShioriColors.InkFaint, textAlign = TextAlign.Center, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ShioriTabBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val tabs = listOf(
        TabSpec(ShioriDestination.Home.route, "今日", Icons.Filled.Book),
        TabSpec(ShioriDestination.Memo.route, "メモ", Icons.Filled.ModeComment),
        TabSpec(ShioriDestination.Archive.route, "記録", Icons.AutoMirrored.Filled.Article),
        TabSpec(ShioriDestination.Export.route, "解析", Icons.Filled.AutoAwesome),
    )
    NavigationBar(containerColor = ShioriColors.Card.copy(alpha = 0.95f), tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(20.dp)) },
                label = { Text(tab.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ShioriColors.Ink,
                    selectedTextColor = ShioriColors.Ink,
                    unselectedIconColor = ShioriColors.InkFaint,
                    unselectedTextColor = ShioriColors.InkFaint,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

@Composable
fun IconCircle(icon: ImageVector, tint: Color = ShioriColors.InkSoft, background: Color = ShioriColors.Good.copy(alpha = 0.15f)) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
fun TagPill(text: String, color: Color, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else Color.Transparent)
            .border(1.dp, if (selected) color else ShioriColors.Divider.copy(alpha = 1f), RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) ShioriColors.Card else ShioriColors.InkSoft,
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
    )
}
