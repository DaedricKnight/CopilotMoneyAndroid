package com.artemkhateev.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artemkhateev.finance.R
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.feature.auth.SignInScreen
import com.artemkhateev.finance.feature.dashboard.DashboardScreen
import com.artemkhateev.finance.feature.transactions.TransactionsScreen
import com.artemkhateev.finance.ui.components.EmptyState
import com.artemkhateev.finance.ui.components.PillTabRow
import com.artemkhateev.finance.ui.components.appBackgroundBrush
import com.artemkhateev.finance.ui.navigation.AppTab
import com.artemkhateev.finance.ui.theme.FinanceTheme
import kotlinx.coroutines.launch

@Composable
fun FinanceApp() {
    val auth = AppGraph.auth
    if (auth == null) {
        // Проекта Firebase в сборке нет — входить некуда, сразу демо-режим.
        HomeScreen()
    } else {
        val user by auth.user.collectAsStateWithLifecycle()
        if (user == null) SignInScreen() else HomeScreen()
    }
}

@Composable
private fun HomeScreen() {
    val colors = FinanceTheme.colors
    val tabs = AppTab.entries
    val pagerState = rememberPagerState(initialPage = AppTab.Default.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush(colors))
            .statusBarsPadding(),
    ) {
        AppHeader(onSettingsClick = { settingsOpen = true })
        PillTabRow(
            titles = tabs.map { it.title },
            selectedIndex = pagerState.targetPage,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        )
        Spacer(Modifier.height(8.dp))
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (val tab = tabs[page]) {
                AppTab.Dashboard -> DashboardScreen()
                AppTab.Transactions -> TransactionsScreen()
                else -> EmptyState(tab.title, "This section isn't built yet")
            }
        }
    }
    if (settingsOpen) SettingsSheet(onDismiss = { settingsOpen = false })
}

@Composable
private fun AppHeader(onSettingsClick: () -> Unit) {
    val colors = FinanceTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        IconButton(onClick = onSettingsClick, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = colors.icon)
        }
        Text(
            text = stringResource(R.string.app_name),
            style = FinanceTheme.typography.screenTitle,
            color = colors.textPrimary,
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(onClick = {}, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Assistant", tint = colors.icon)
        }
    }
}
