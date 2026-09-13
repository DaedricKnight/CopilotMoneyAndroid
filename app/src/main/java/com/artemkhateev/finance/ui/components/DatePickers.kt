package com.artemkhateev.finance.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Календарь, в котором выбираются только даты с [earliest] по [latest]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoundedDatePickerDialog(
    initial: LocalDate?,
    earliest: LocalDate,
    latest: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        // Дату вне границ (например, прошедший срок цели) календарь выбранной не покажет.
        initialSelectedDateMillis = initial?.takeIf { !it.isBefore(earliest) && !it.isAfter(latest) }?.toUtcMillis(),
        // Календарь открывается на текущем месяце, поэтому его год обязан быть в диапазоне.
        yearRange = minOf(earliest.year, today.year)..maxOf(latest.year, today.year),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = utcTimeMillis.toUtcDate()
                return !date.isBefore(earliest) && !date.isAfter(latest)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.toUtcDate()) } ?: onDismiss() }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

// DatePicker работает с полночью по UTC, а даты в приложении — без часового пояса.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
