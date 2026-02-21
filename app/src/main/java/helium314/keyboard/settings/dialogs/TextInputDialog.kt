// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.text.InputType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.latin.dogakdogak.NoUnderlineTextField
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.previewDark

// mostly taken from StreetComplete / SCEE
/** Dialog with which to input text. OK button is only clickable if [checkTextValid] returns true. */
@Composable
fun TextInputDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    onNeutral: () -> Unit = { },
    neutralButtonText: String? = null,
    confirmButtonText: String = stringResource(android.R.string.ok),
    initialText: String = "",
    textInputLabel: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
    properties: DialogProperties = DialogProperties(),
    reducePadding: Boolean = false,
    checkTextValid: (text: String) -> Boolean = { it.isNotBlank() }
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(if (singleLine) initialText.length else 0)))
    }

    val androidInputType = when (keyboardType) {
        KeyboardType.Number -> InputType.TYPE_CLASS_NUMBER
        KeyboardType.Phone -> InputType.TYPE_CLASS_PHONE
        KeyboardType.Uri -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        KeyboardType.Email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        KeyboardType.Password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        KeyboardType.NumberPassword -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        KeyboardType.Decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        else -> InputType.TYPE_CLASS_TEXT
    }

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(value.text) },
        confirmButtonText = confirmButtonText,
        checkOk = { checkTextValid(value.text) },
        neutralButtonText = neutralButtonText,
        onNeutral = { onDismissRequest(); onNeutral() },
        modifier = modifier,
        title = title,
        content = {
            NoUnderlineTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                requestFocus = true,
                textColor = MaterialTheme.colorScheme.onSurface,
                hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                inputType = androidInputType,
            )
        },
        properties = properties,
        reducePadding = reducePadding,
    )
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        TextInputDialog(
            onDismissRequest = {},
            onConfirmed = {},
            title = { Text("Title") },
            initialText = "some text\nand another line",
            singleLine = false,
            textInputLabel = { Text("fill it") }
        )
    }
}
