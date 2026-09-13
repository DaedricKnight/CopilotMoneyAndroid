package com.artemkhateev.finance.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.ui.components.BoundedDatePickerDialog
import com.artemkhateev.finance.ui.components.CenteredTextField
import com.artemkhateev.finance.ui.components.FieldLabel
import com.artemkhateev.finance.ui.components.MoneyInputField
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.SelectablePill
import com.artemkhateev.finance.ui.format.lastGrapheme
import com.artemkhateev.finance.ui.format.longDate
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.color
import java.time.LocalDate

/** Эмодзи, которые чаще всего нужны целям: набирать их с клавиатуры дольше. */
private val EmojiSuggestions = listOf(
    "🎯", "🛟", "🏖️", "✈️", "🗾", "🏠", "🚗", "💻", "📱", "🎁", "💍", "🎓",
    "👶", "🐶", "🎸", "📷", "🚲", "🛋️", "🎟️", "🏋️", "🎮", "💰",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(
    draft: GoalDraft,
    onChange: ((GoalDraft) -> GoalDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val today = remember { LocalDate.now() }
    val editing = draft.id.isNotBlank()
    val problem = draft.problem()
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember(draft.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = (if (editing) "Edit goal" else "New goal").uppercase(),
                style = typography.sectionLabel,
                color = colors.sectionLabel,
            )
            CenteredTextField(
                value = draft.emoji,
                placeholder = "🎯",
                style = TextStyle(fontSize = 44.sp, color = colors.textPrimary),
                onValueChange = { value -> onChange { it.copy(emoji = lastGrapheme(value)) } },
                capitalization = KeyboardCapitalization.None,
                placeholderAlpha = 0.35f,
            )
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items(EmojiSuggestions) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onChange { it.copy(emoji = emoji) } }
                            .padding(8.dp),
                    )
                }
            }
            CenteredTextField(
                value = draft.name,
                placeholder = "Goal name",
                style = typography.heroAmount.copy(color = draft.tone.color()),
                onValueChange = { value -> onChange { it.copy(name = value) } },
            )

            FieldLabel("Color")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CategoryTone.entries.forEach { tone ->
                    val selected = tone == draft.tone
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(tone.color())
                            .then(if (selected) Modifier.border(2.dp, colors.textPrimary, CircleShape) else Modifier)
                            .clickable { onChange { it.copy(tone = tone) } },
                    )
                }
            }

            FieldLabel("Target")
            MoneyInputField(
                text = draft.targetText,
                onValueChange = { text -> onChange { it.copy(targetText = text) } },
                textStyle = typography.heroAmount.copy(fontSize = 32.sp, color = colors.textPrimary),
                imeAction = if (editing) ImeAction.Done else ImeAction.Next,
            )
            if (!editing) {
                FieldLabel("Already saved")
                MoneyInputField(
                    text = draft.savedText,
                    onValueChange = { text -> onChange { it.copy(savedText = text) } },
                    textStyle = typography.heroAmount.copy(fontSize = 24.sp, color = colors.positiveText),
                    emptyHint = "Nothing yet",
                    prefixWhenEmpty = false,
                    imeAction = ImeAction.Done,
                )
            }

            FieldLabel("Target date")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val date = draft.targetDate
                SelectablePill("No date", selected = date == null, onClick = { onChange { it.copy(targetDate = null) } })
                SelectablePill(
                    text = date?.let { longDate(it) } ?: "Pick a date…",
                    selected = date != null,
                    onClick = { pickingDate = true },
                )
            }

            PillButton(
                text = if (editing) "Save changes" else "Add goal",
                onClick = onSave,
                enabled = problem == null,
                filled = true,
                modifier = Modifier.padding(top = 8.dp),
            )
            // Пустые поля видны и так; объясняем только неразборчивые суммы.
            if (problem != null && draft.name.isNotBlank() && draft.targetText.isNotBlank()) {
                Text(problem, style = typography.bodySecondary, color = colors.negativeText, textAlign = TextAlign.Center)
            }
            if (editing) {
                Text(
                    text = if (confirmDelete) "Tap again to delete" else "Delete goal",
                    style = typography.bodySecondary,
                    color = colors.negativeText,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (confirmDelete) {
                                onDelete()
                            } else {
                                confirmDelete = true
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (confirmDelete) {
                    Text(
                        text = "Its history of contributions is deleted too",
                        style = typography.caption,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (pickingDate) {
        BoundedDatePickerDialog(
            initial = draft.targetDate,
            // Срок в прошлом плана не даёт; вперёд — хоть на пенсию.
            earliest = today.plusDays(1),
            latest = today.plusYears(50),
            onPick = { picked ->
                onChange { it.copy(targetDate = picked) }
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}
