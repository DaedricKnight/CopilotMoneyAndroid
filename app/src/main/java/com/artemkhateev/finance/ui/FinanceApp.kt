package com.artemkhateev.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.artemkhateev.finance.R
import com.artemkhateev.finance.feature.dashboard.DashboardScreen
import com.artemkhateev.finance.feature.transactions.TransactionsScreen
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.PillTabRow
import com.artemkhateev.finance.ui.navigation.AppTab
import com.artemkhateev.finance.ui.theme.FinanceTheme
import kotlinx.coroutines.launch

@Composable
fun FinanceApp() {
    val colors = FinanceTheme.colors
    val tabs = AppTab.entries
    val pagerState = rememberPagerState(initialPage = AppTab.Default.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to colors.backgroundTop, 0.4f to colors.background))
            .statusBarsPadding(),
    ) {
        AppHeader()
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
                else -> PlaceholderScreen(tab)
            }
        }
    }
}

@Composable
private fun AppHeader() {
    val colors = FinanceTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        IconButton(onClick = {}, modifier = Modifier.align(Alignment.CenterStart)) {
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

@Composable
private fun PlaceholderScreen(tab: AppTab) {
    val colors = FinanceTheme.colors
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        FinanceCard(
            hero = true,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
        ) {
            Text(tab.title, style = FinanceTheme.typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This section isn't built yet",
                style = FinanceTheme.typography.bodySecondary,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
