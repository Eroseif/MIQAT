package com.miqat.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miqat.app.AppViewModel
import com.miqat.app.R
import java.time.LocalDate

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MiqatRoot(vm: AppViewModel, onRequestPermission: () -> Unit) {
    val state by vm.state.collectAsState()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf(
        Tab("Calendar", Icons.Outlined.CalendarMonth),
        Tab("Prayer Times", Icons.Outlined.Mosque),
        Tab("Qibla", Icons.Outlined.Explore)
    )
    var showAdd by remember { mutableStateOf(false) }
    var addDate by remember { mutableStateOf<LocalDate?>(null) }
    BackHandler(enabled = showSettings) { showSettings = false }

    Scaffold(
        containerColor = Black,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Black).padding(top = 36.dp, start = 16.dp, end = 8.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.ic_logo_fg), contentDescription = "MIQAT logo", modifier = Modifier.size(40.dp))
                Text("MIQAT", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, modifier = Modifier.weight(1f))
                if (selected == 0 && !showSettings) {
                    IconButton(onClick = { addDate = null; showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add event", tint = White)
                    }
                }
                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(
                        if (showSettings) Icons.Filled.Close else Icons.Outlined.Settings,
                        contentDescription = if (showSettings) "Close settings" else "Settings",
                        tint = White
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Black, contentColor = White) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = selected == i && !showSettings,
                        onClick = { selected = i; showSettings = false },
                        icon = { Icon(t.icon, contentDescription = t.label, tint = White) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = White, selectedTextColor = White,
                            unselectedIconColor = White, unselectedTextColor = LightGrey,
                            indicatorColor = CardGrey
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Black)) {
            if (showSettings) {
                SettingsScreen(state, vm)
            } else {
                when (selected) {
                    0 -> CalendarScreen(state, vm, onAddEvent = { d -> addDate = d; showAdd = true })
                    1 -> PrayerScreen(state, vm, onRequestPermission)
                    else -> QiblaScreen(state)
                }
            }
        }
    }

    if (showAdd) {
        AddEventDialog(
            initialDate = addDate ?: LocalDate.now(),
            onDismiss = { showAdd = false },
            onSave = { d, t, desc, yearly ->
                vm.addEvent(d, t, desc, yearly)
                showAdd = false
            }
        )
    }
}
