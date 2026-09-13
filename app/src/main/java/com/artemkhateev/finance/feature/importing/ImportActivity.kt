package com.artemkhateev.finance.feature.importing

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.ui.components.CategoryChip
import com.artemkhateev.finance.ui.components.EmptyState
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.SectionHeader
import com.artemkhateev.finance.ui.components.SelectablePill
import com.artemkhateev.finance.ui.components.appBackgroundBrush
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.format.MoneyFormatter
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.format.periodLabel
import com.artemkhateev.finance.ui.format.shortDate
import com.artemkhateev.finance.ui.theme.FinanceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Сколько строк показывать в превью: остальные импортируются, но не рисуются. */
private const val PREVIEW_LIMIT = 200

/**
 * Импорт CSV: файл приходит из «Поделиться» (файлом или текстом) или открывается как файл.
 * Пока пользователь не подтвердит превью, ничего не записывается.
 */
class ImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val shared = intent
        setContent {
            FinanceTheme {
                val viewModel = viewModel {
                    ImportViewModel(
                        repository = AppGraph.repository,
                        // Без Firebase в сборке входа нет: импорт идёт в демо-данные.
                        signedIn = { AppGraph.auth?.let { it.user.value != null } ?: true },
                        load = { readShared(shared) },
                    )
                }
                ImportScreen(viewModel, onClose = ::finish)
            }
        }
    }

    private suspend fun readShared(intent: Intent): String? = withContext(Dispatchers.IO) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { return@withContext it }
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: intent.data
        uri?.let { contentResolver.openInputStream(it)?.use { stream -> stream.bufferedReader().readText() } }
    }
}

@Composable
private fun ImportScreen(viewModel: ImportViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush(colors))
            .statusBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("Import", style = typography.screenTitle, color = colors.textPrimary, modifier = Modifier.align(Alignment.Center))
            Text(
                text = "Close",
                style = typography.bodySecondary,
                color = colors.accent,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        when (val current = state) {
            ImportUiState.Loading -> EmptyState("Reading the file…", "")
            ImportUiState.NeedsSignIn -> EmptyState("Sign in first", "Open Money Tracker, sign in, then share the file again.")
            is ImportUiState.Failed -> EmptyState("Couldn't import", current.message)
            is ImportUiState.Importing -> EmptyState("Importing…", transactionsLabel(current.count))
            is ImportUiState.Done -> DoneContent(current.count, onClose)
            is ImportUiState.Ready -> ReadyContent(current, onChoose = viewModel::chooseAccount, onConfirm = viewModel::confirm)
        }
    }
}

@Composable
private fun ReadyContent(state: ImportUiState.Ready, onChoose: (String, String?) -> Unit, onConfirm: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val plan = state.plan
    val count = plan.transactions.size
    val categoryNames = state.categories.associate { it.id to it.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            FinanceCard(
                hero = true,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(transactionsLabel(count), style = typography.heroAmount, color = colors.textPrimary)
                val first = plan.firstDate
                val last = plan.lastDate
                if (first != null && last != null) {
                    Text(
                        text = "${periodLabel(first, last)} · ${MoneyFormatter.format(plan.total, sign = SignStyle.Negative)}",
                        style = typography.bodySecondary,
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                val notes = buildList {
                    if (plan.duplicates > 0) add("${plan.duplicates} already in the app will be skipped")
                    if (state.unreadable > 0) add("${state.unreadable} rows couldn't be read")
                    if (plan.olderThanWindow > 0) add("${plan.olderThanWindow} older than last month are saved but not shown yet")
                    add("Account balances stay as they are")
                }
                notes.forEach { Text(it, style = typography.caption, color = colors.textSecondary, textAlign = TextAlign.Center) }
            }
        }
        item(key = "confirm") {
            PillButton(
                text = if (count == 0) "Nothing new to import" else "Import ${transactionsLabel(count)}",
                onClick = onConfirm,
                enabled = count > 0,
                filled = true,
            )
        }
        state.sources.forEach { source ->
            item(key = "account-${source.name}") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Records from “${source.name}” go to")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.accounts, key = { it.id }) { account ->
                            SelectablePill(
                                text = account.name,
                                selected = source.targetAccountId == account.id,
                                onClick = { onChoose(source.name, account.id) },
                            )
                        }
                        item(key = "new-account") {
                            SelectablePill(
                                text = "New account “${source.name}”",
                                selected = source.targetAccountId == null,
                                onClick = { onChoose(source.name, null) },
                            )
                        }
                    }
                }
            }
        }
        if (plan.newCategories.isNotEmpty()) {
            item(key = "categories") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("New categories")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(plan.newCategories, key = { it.id }) { CategoryChip(it) }
                    }
                }
            }
        }
        item(key = "transactions-header") { SectionHeader("Transactions") }
        items(plan.transactions.take(PREVIEW_LIMIT), key = { it.id }) { transaction ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchant,
                        style = typography.body,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(shortDate(transaction.date), transaction.categoryId?.let { categoryNames[it] }).joinToString(" · "),
                        style = typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                val income = transaction.amount.minor > 0
                MoneyText(
                    amount = if (income) transaction.amount else transaction.amount.abs(),
                    color = if (income) colors.positiveText else colors.textPrimary,
                    sign = if (income) SignStyle.Always else SignStyle.None,
                )
            }
        }
        if (count > PREVIEW_LIMIT) {
            item(key = "more") {
                Text("and ${count - PREVIEW_LIMIT} more", style = typography.caption, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun DoneContent(count: Int, onClose: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FinanceCard(
            hero = true,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text("Imported ${transactionsLabel(count)}", style = typography.cardTitle, color = colors.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Imported records don't change account balances — check them in Accounts.",
                style = typography.bodySecondary,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        PillButton(text = "Done", onClick = onClose, filled = true)
    }
}

private fun transactionsLabel(count: Int) = if (count == 1) "1 transaction" else "$count transactions"
