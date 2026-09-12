package com.artemkhateev.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.theme.FinanceTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val auth = AppGraph.auth
    val cloud = AppGraph.cloud

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surfaceHero) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = typography.cardTitle, color = colors.textPrimary)
            if (auth == null || cloud == null) {
                Text(
                    text = "Demo mode: data lives in memory until the app restarts. " +
                        "Add app/google-services.json to the project to sign in and sync with Firebase.",
                    style = typography.bodySecondary,
                    color = colors.textSecondary,
                )
            } else {
                val user by auth.user.collectAsStateWithLifecycle()
                var importing by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf<String?>(null) }

                Text(
                    text = "Signed in as ${user?.email ?: user?.displayName ?: "unknown"}",
                    style = typography.bodySecondary,
                    color = colors.textSecondary,
                )
                PillButton(
                    text = if (importing) "Saving demo data…" else "Load demo data",
                    enabled = !importing,
                    onClick = {
                        importing = true
                        status = null
                        // Области экрана тут мало: запись должна дойти до облака, даже если лист закрыли.
                        AppGraph.appScope.launch {
                            status = runCatching { cloud.importDemoData() }
                                .fold({ "Demo data saved to the cloud" }, { "Couldn't save: ${it.message}" })
                            importing = false
                        }
                    },
                )
                status?.let { Text(it, style = typography.bodySecondary, color = colors.textSecondary) }
                PillButton(
                    text = "Sign out",
                    onClick = {
                        onDismiss()
                        AppGraph.appScope.launch { auth.signOut() }
                    },
                )
            }
        }
    }
}
