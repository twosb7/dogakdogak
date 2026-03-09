package helium314.keyboard.settings

import android.app.Activity
import android.graphics.Typeface
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView

class ImeSandboxActivity : Activity() {
    companion object {
        const val EXTRA_PREFILL_TEXT = "prefill_text"
        const val EXTRA_PREFILL_CODEPOINTS = "prefill_codepoints"
        const val EXTRA_CLEAR_TEXT = "clear_text"
        const val EXTRA_CURSOR_POSITION = "cursor_position"
    }

    private lateinit var editText: EditText
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val container = FrameLayout(this).apply {
            setPadding(32, 64, 32, 32)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                requestKeyboard()
            }
        }
        statusView = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.YELLOW)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(16, 10, 16, 10)
            gravity = Gravity.CENTER
            text = "sel=?"
        }
        editText = object : EditText(this) {
            override fun onSelectionChanged(selStart: Int, selEnd: Int) {
                super.onSelectionChanged(selStart, selEnd)
                updateStatus()
            }
        }.apply {
            hint = "IME sandbox"
            gravity = Gravity.TOP or Gravity.START
            minLines = 8
            textSize = 28f
            typeface = Typeface.MONOSPACE
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = false
            requestFocus()
        }
        container.addView(
            editText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(
            statusView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                bottomMargin = 220
            }
        )
        setContentView(container)

        applyPrefillText()
        updateStatus()

        requestKeyboard()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPrefillText()
        requestKeyboard()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        requestKeyboard()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            updateStatus()
            requestKeyboard()
        }
    }

    private fun requestKeyboard() {
        editText.post {
            editText.requestFocus()
            editText.requestFocusFromTouch()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            window.decorView.post {
                showImeWithInsetsController(window.decorView)
            }
            editText.postDelayed({
                editText.requestFocus()
                editText.requestFocusFromTouch()
                imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                showImeWithInsetsController(window.decorView)
            }, 250)
            editText.postDelayed({
                editText.requestFocus()
                editText.requestFocusFromTouch()
                imm.restartInput(editText)
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                showImeWithInsetsController(window.decorView)
            }, 600)
        }
    }

    private fun showImeWithInsetsController(view: View) {
        view.windowInsetsController?.show(WindowInsets.Type.ime())
    }

    private fun applyPrefillText() {
        val shouldClearText = intent.getBooleanExtra(EXTRA_CLEAR_TEXT, false)
        val prefillText = buildPrefillText()
        var didUpdateText = false
        var shouldRestoreCursor = false
        if (prefillText != null) {
            editText.setText(prefillText)
            didUpdateText = true
            shouldRestoreCursor = true
        } else if (shouldClearText) {
            editText.setText("")
            didUpdateText = true
            shouldRestoreCursor = true
        }
        if (didUpdateText) {
            editText.clearComposingText()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.restartInput(editText)
            if (shouldRestoreCursor) {
                editText.post {
                    applyCursorPosition()
                    updateStatus()
                }
            }
        }
    }

    private fun applyCursorPosition() {
        val requestedCursor = intent.getIntExtra(EXTRA_CURSOR_POSITION, editText.text.length)
        val safeCursor = requestedCursor.coerceIn(0, editText.text.length)
        editText.setSelection(safeCursor)
    }

    private fun updateStatus() {
        if (!::editText.isInitialized || !::statusView.isInitialized) return
        val start = editText.selectionStart
        val end = editText.selectionEnd
        val status = "sel=$start:$end len=${editText.text.length}"
        statusView.text = status
        title = "IME sandbox $status"
    }

    private fun buildPrefillText(): String? {
        intent.getStringExtra(EXTRA_PREFILL_TEXT)?.let { return it }
        val codePointsExtra = intent.getStringExtra(EXTRA_PREFILL_CODEPOINTS) ?: return null
        val codePoints = codePointsExtra
            .split(',')
            .mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) null else trimmed.toIntOrNull(16)
            }
        if (codePoints.isEmpty()) return null
        return buildString {
            codePoints.forEach { append(String(Character.toChars(it))) }
        }
    }
}
