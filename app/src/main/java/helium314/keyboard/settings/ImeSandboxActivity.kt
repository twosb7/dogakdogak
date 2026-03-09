package helium314.keyboard.settings

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView

class ImeSandboxActivity : Activity() {
    private lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        editText = EditText(this).apply {
            hint = "IME sandbox"
            textSize = 22f
            setSingleLine(false)
            isSingleLine = false
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = 32
                topMargin = 64
                rightMargin = 32
                bottomMargin = 32
            }
        }

        val title = TextView(this).apply {
            text = "IME sandbox"
            textSize = 14f
            alpha = 0.7f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 16
                topMargin = 16
            }
        }

        root.addView(editText)
        root.addView(title)
        setContentView(root)

        editText.requestFocus()
        showIme()
    }

    override fun onResume() {
        super.onResume()
        editText.postDelayed({ showIme() }, 120)
        editText.postDelayed({ showIme() }, 420)
    }

    private fun showIme() {
        editText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
}
