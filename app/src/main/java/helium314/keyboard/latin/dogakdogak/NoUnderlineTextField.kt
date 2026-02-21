package helium314.keyboard.latin.dogakdogak

import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun NoUnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    singleLine: Boolean = true,
    textColor: Color = Color.Black,
    hintColor: Color = Color.Gray,
    textSizeSp: Float = 16f,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    imeOptions: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    onEditorAction: ((Int) -> Boolean)? = null,
    requestFocus: Boolean = false,
    maxLength: Int = -1,
) {
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val isUpdating = remember { booleanArrayOf(false) }

    AndroidView(
        factory = { ctx ->
            EditText(ctx).apply {
                if (hint.isNotEmpty()) setHint(hint)
                setHintTextColor(hintColor.toArgb())
                setTextColor(textColor.toArgb())
                background = null
                gravity = Gravity.TOP or Gravity.START
                this.inputType = if (singleLine) inputType
                    else inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                if (singleLine) { this.maxLines = 1; isSingleLine = true }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                if (maxLength > 0) filters = arrayOf(InputFilter.LengthFilter(maxLength))
                if (imeOptions != EditorInfo.IME_ACTION_UNSPECIFIED) this.imeOptions = imeOptions
                setText(value)
                setSelection(value.length)

                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (isUpdating[0]) return
                        onValueChangeState.value(s?.toString() ?: "")
                    }
                })

                if (onEditorAction != null) {
                    setOnEditorActionListener { _, actionId, _ -> onEditorAction(actionId) }
                }
                if (requestFocus) post { requestFocus() }
            }
        },
        update = { editText ->
            if (editText.text.toString() != value) {
                isUpdating[0] = true
                val sel = editText.selectionStart.coerceIn(0, value.length)
                editText.setText(value)
                editText.setSelection(sel)
                isUpdating[0] = false
            }
        },
        modifier = modifier
    )
}

@Composable
fun NoUnderlineTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    singleLine: Boolean = true,
    textColor: Color = Color.Black,
    hintColor: Color = Color.Gray,
    textSizeSp: Float = 16f,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    imeOptions: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    onEditorAction: ((Int) -> Boolean)? = null,
    requestFocus: Boolean = false,
) {
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val isUpdating = remember { booleanArrayOf(false) }

    AndroidView(
        factory = { ctx ->
            EditText(ctx).apply {
                if (hint.isNotEmpty()) setHint(hint)
                setHintTextColor(hintColor.toArgb())
                setTextColor(textColor.toArgb())
                background = null
                gravity = Gravity.TOP or Gravity.START
                this.inputType = if (singleLine) inputType
                    else inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                if (singleLine) { this.maxLines = 1; isSingleLine = true }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                if (imeOptions != EditorInfo.IME_ACTION_UNSPECIFIED) this.imeOptions = imeOptions
                setText(value.text)
                setSelection(
                    value.selection.start.coerceIn(0, value.text.length),
                    value.selection.end.coerceIn(0, value.text.length)
                )

                val et = this
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (isUpdating[0]) return
                        onValueChangeState.value(TextFieldValue(
                            text = s?.toString() ?: "",
                            selection = TextRange(et.selectionStart, et.selectionEnd)
                        ))
                    }
                })

                if (onEditorAction != null) {
                    setOnEditorActionListener { _, actionId, _ -> onEditorAction(actionId) }
                }
                if (requestFocus) post { requestFocus() }
            }
        },
        update = { editText ->
            if (editText.text.toString() != value.text) {
                isUpdating[0] = true
                editText.setText(value.text)
                editText.setSelection(
                    value.selection.start.coerceIn(0, value.text.length),
                    value.selection.end.coerceIn(0, value.text.length)
                )
                isUpdating[0] = false
            }
        },
        modifier = modifier
    )
}
