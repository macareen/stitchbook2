package com.macareen.stitchbook2.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.parseLocalDateOrNull
import java.time.Instant
import java.time.ZoneOffset

/**
 * An ISO-8601 local-date text field ("yyyy-MM-dd") with a calendar button.
 * Typing stays possible (fast for keyboard and accessibility users); the
 * picker is a convenience. The Material date picker works in UTC-midnight
 * millis, so conversion uses [ZoneOffset.UTC] on both sides to avoid an
 * off-by-one day in negative-offset time zones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        placeholder = { Text(text = stringResource(R.string.date_field_placeholder)) },
        singleLine = true,
        isError = isError,
        enabled = enabled,
        supportingText = if (isError && errorText != null) {
            { Text(text = errorText) }
        } else {
            null
        },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }, enabled = enabled) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = stringResource(R.string.date_field_pick, label)
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    )

    if (showPicker) {
        val initialMillis = parseLocalDateOrNull(value)
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onValueChange(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            )
                        }
                        showPicker = false
                    }
                ) {
                    Text(text = stringResource(R.string.date_field_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
