package com.miqat.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (date: LocalDate, title: String, description: String, yearly: Boolean) -> Unit
) {
    var date by remember { mutableStateOf(initialDate) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var yearly by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val canSave = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardGrey,
        titleContentColor = White,
        textContentColor = White,
        title = { Text("Add event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiqatButton(date.format(longDate), Modifier.fillMaxWidth()) { picking = true }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Description (optional)") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().clickable { yearly = !yearly }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = yearly, onCheckedChange = { yearly = it },
                        colors = CheckboxDefaults.colors(checkedColor = White, uncheckedColor = White, checkmarkColor = Black)
                    )
                    Text("Repeats every year (for birthdays)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(date, title.trim(), desc.trim(), yearly) }, enabled = canSave) {
                Text("Save", color = if (canSave) White else LightGrey)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = White) } }
    )

    if (picking) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK", color = White) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", color = White) } }
        ) { DatePicker(state = st) }
    }
}
