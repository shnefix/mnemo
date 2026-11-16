package app.mnemo.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mnemo.ui.components.MnemoIcons
import app.mnemo.ui.theme.MnemoColors

/** Layout class derived from the window width (Material 3 breakpoints). */
enum class WindowSize { Compact, Medium, Expanded }

val LocalWindowSize = staticCompositionLocalOf { WindowSize.Compact }

@Composable
fun WindowSizeProvider(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    var widthDp by remember { mutableStateOf(0) }
    Box(
        modifier =
            Modifier.fillMaxSize().onSizeChanged {
                widthDp =
                    with(density) {
                        it.width
                            .toDp()
                            .value
                            .toInt()
                    }
            },
    ) {
        val cls =
            when {
                widthDp < 600 -> WindowSize.Compact
                widthDp < 840 -> WindowSize.Medium
                else -> WindowSize.Expanded
            }
        CompositionLocalProvider(LocalWindowSize provides cls, content = content)
    }
}

private fun NavSection.icon(): ImageVector =
    when (this) {
        NavSection.Today -> MnemoIcons.Home
        NavSection.Decks -> MnemoIcons.Decks
        NavSection.Browse -> MnemoIcons.Browse
        NavSection.Graph -> MnemoIcons.Graph
        NavSection.Stats -> MnemoIcons.Stats
    }

/**
 * The two shells: phone (bottom tab bar, Design C) and desktop (sidebar,
 * Design A). [showChrome] hides both for focus screens such as Review.
 */
@Composable
fun AdaptiveScaffold(
    current: NavSection?,
    showChrome: Boolean,
    onNavigate: (NavSection) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    sidebarExtra: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val size = LocalWindowSize.current
    if (!showChrome) {
        content(PaddingValues(0.dp))
        return
    }
    if (size == WindowSize.Compact) {
        Box(Modifier.fillMaxSize().background(MnemoColors.background)) {
            content(PaddingValues(bottom = 84.dp))
            PhoneTabBar(
                current = current,
                onNavigate = onNavigate,
                onCreate = onCreate,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    } else {
        Row(Modifier.fillMaxSize().background(MnemoColors.background)) {
            Sidebar(current = current, onNavigate = onNavigate, onCreate = onCreate, onSettings = onSettings, extra = sidebarExtra)
            Box(Modifier.weight(1f).fillMaxHeight()) { content(PaddingValues(0.dp)) }
        }
    }
}

@Composable
private fun PhoneTabBar(
    current: NavSection?,
    onNavigate: (NavSection) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(NavSection.Today, NavSection.Decks, null, NavSection.Graph, NavSection.Stats)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(MnemoColors.surface)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            if (tab == null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MnemoColors.coral)
                                .clickable(role = Role.Button, onClick = onCreate)
                                .semantics { contentDescription = "Create cards" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MnemoIcons.Plus, contentDescription = null, tint = MnemoColors.onCoral, modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                val selected = tab == current
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Tab) { onNavigate(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        tab.icon(),
                        contentDescription = tab.label,
                        tint = if (selected) MnemoColors.text else MnemoColors.text2,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MnemoColors.text else MnemoColors.text2,
                    )
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    current: NavSection?,
    onNavigate: (NavSection) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    extra: @Composable (() -> Unit)?,
) {
    Column(
        modifier =
            Modifier
                .width(248.dp)
                .fillMaxHeight()
                .background(MnemoColors.surface)
                .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(MnemoColors.coral))
            Spacer(Modifier.width(10.dp))
            Text("mnemo", style = MaterialTheme.typography.headlineMedium, color = MnemoColors.text)
        }
        Spacer(Modifier.height(24.dp))
        NavSection.entries.forEach { section ->
            SidebarItem(label = section.label, icon = section.icon(), selected = section == current) { onNavigate(section) }
        }
        SidebarItem(label = "Create", icon = MnemoIcons.Plus, selected = false, onClick = onCreate)
        SidebarItem(label = "Settings", icon = MnemoIcons.Settings, selected = false, onClick = onSettings)
        if (extra != null) {
            Spacer(Modifier.height(24.dp))
            extra()
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) MnemoColors.surface2 else MnemoColors.surface)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MnemoColors.coral else MnemoColors.text2,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MnemoColors.text else MnemoColors.text2,
        )
    }
}
