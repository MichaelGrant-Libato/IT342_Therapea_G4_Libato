package com.therapea.app.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.therapea.app.R

object TheraPeaDialog {

    data class Action(
        val label:   String,
        val style:   Style = Style.PRIMARY,
        val onClick: (() -> Unit)? = null
    )

    enum class Style { PRIMARY, GHOST, DANGER }

    fun show(
        context:  Context,
        title:    String,
        message:  String,
        icon:     String?  = null,
        accent:   String   = "#E6F4EE",
        actions:  List<Action> = listOf(Action("Close"))
    ) {
        val dialog = AlertDialog.Builder(context, 0).create()

        val root = buildRoot(context)

        // ── Icon circle ───────────────────────────────────────────────
        if (icon != null) {
            root.addView(iconCircle(context, icon, accent))
        }

        // ── Title ─────────────────────────────────────────────────────
        root.addView(TextView(context).apply {
            text      = title
            textSize  = 18f
            typeface  = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity   = Gravity.CENTER
            setTextColor(Color.parseColor("#1C1F1A"))
            setPadding(0, dp(context, if (icon != null) 12 else 4), 0, 0)
        })

        // ── Message ───────────────────────────────────────────────────
        root.addView(TextView(context).apply {
            text      = message
            textSize  = 14f
            gravity   = Gravity.CENTER
            setLineSpacing(dp(context, 3).toFloat(), 1.0f)
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(context, 8), 0, dp(context, 4))
        })

        // ── Buttons ───────────────────────────────────────────────────
        root.addView(buttonRow(context, actions, dialog))

        dialog.setView(wrapInScroll(context, root))
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * Details dialog — shows a list of label/value rows with a coloured left accent bar.
     */
    fun showDetails(
        context: Context,
        title:   String,
        rows:    List<Pair<String, String>>,
        icon:    String = "📋"
    ) {
        val dialog = AlertDialog.Builder(context, 0).create()
        val root   = buildRoot(context)

        root.addView(iconCircle(context, icon, "#E6F4EE"))

        root.addView(TextView(context).apply {
            text     = title
            textSize = 18f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity  = Gravity.CENTER
            setTextColor(Color.parseColor("#1C1F1A"))
            setPadding(0, dp(context, 12), 0, dp(context, 8))
        })

        // Detail rows
        val rowContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background  = roundedBg(context, "#F8FAF8", "#E8EDE8")
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 4); bottomMargin = dp(context, 8) }
        }

        rows.forEachIndexed { index, (label, value) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.TOP
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dp(context, 10)
                }
            }

            // Accent bar
            row.addView(View(context).apply {
                setBackgroundColor(Color.parseColor("#0A5C36"))
                layoutParams = LinearLayout.LayoutParams(dp(context, 3), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dp(context, 10)
                }
            })

            val col = LinearLayout(context).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(context).apply {
                text     = label.uppercase()
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#94A3B8"))
                letterSpacing = 0.08f
            })
            col.addView(TextView(context).apply {
                text     = value.ifBlank { "—" }
                textSize = 14f
                setTextColor(Color.parseColor("#1C1F1A"))
                setPadding(0, dp(context, 2), 0, 0)
            })

            row.addView(col)
            rowContainer.addView(row)
        }

        root.addView(rowContainer)
        root.addView(buttonRow(context, listOf(Action("Close")), dialog))

        dialog.setView(wrapInScroll(context, root))
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * Input dialog — shows a text field with a cancel + confirm action.
     */
    fun showInput(
        context:       Context,
        title:         String,
        message:       String,
        hint:          String      = "",
        icon:          String      = "✏️",
        confirmLabel:  String      = "Confirm",
        cancelLabel:   String      = "Cancel",
        multiLine:     Boolean     = false,
        onConfirm:     (String) -> Unit
    ) {
        val dialog = AlertDialog.Builder(context, 0).create()
        val root   = buildRoot(context)

        root.addView(iconCircle(context, icon, "#FFF7ED"))

        root.addView(TextView(context).apply {
            text     = title
            textSize = 18f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity  = Gravity.CENTER
            setTextColor(Color.parseColor("#1C1F1A"))
            setPadding(0, dp(context, 12), 0, 0)
        })

        root.addView(TextView(context).apply {
            text     = message
            textSize = 13f
            gravity  = Gravity.CENTER
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(context, 6), 0, dp(context, 12))
        })

        val input = EditText(context).apply {
            this.hint = hint
            inputType = if (multiLine)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else
                InputType.TYPE_CLASS_TEXT
            if (multiLine) minLines = 3
            background  = roundedBg(context, "#F8FAF8", "#D1D5D0")
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            setTextColor(Color.parseColor("#1C1F1A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 12) }
        }
        root.addView(input)

        root.addView(buttonRow(context, listOf(
            Action(cancelLabel, Style.GHOST),
            Action(confirmLabel, Style.PRIMARY) {
                val text = input.text.toString().trim()
                if (text.isBlank()) {
                    input.error = "This field is required."
                } else {
                    dialog.dismiss()
                    onConfirm(text)
                }
            }
        ), dialog, dismissOnClick = false))

        dialog.setView(wrapInScroll(context, root))
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun buildRoot(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity     = Gravity.CENTER_HORIZONTAL
        background  = context.getDrawable(R.drawable.dialog_bg)
        setPadding(dp(context, 24), dp(context, 28), dp(context, 24), dp(context, 20))
    }

    private fun wrapInScroll(context: Context, inner: LinearLayout): ScrollView {
        return ScrollView(context).apply {
            background = context.getDrawable(R.drawable.dialog_bg)
            addView(inner)
        }
    }

    private fun iconCircle(context: Context, icon: String, bgHex: String): TextView {
        return TextView(context).apply {
            text     = icon
            textSize = 26f
            gravity  = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape       = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor(bgHex))
            }
            layoutParams = LinearLayout.LayoutParams(dp(context, 64), dp(context, 64)).apply {
                gravity     = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(context, 4)
            }
        }
    }

    private fun buttonRow(
        context:        Context,
        actions:        List<Action>,
        dialog:         AlertDialog,
        dismissOnClick: Boolean = true
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 8) }
        }

        actions.forEachIndexed { index, action ->
            val btn = TextView(context).apply {
                text     = action.label
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity  = Gravity.CENTER
                setPadding(dp(context, 20), dp(context, 13), dp(context, 20), dp(context, 13))

                when (action.style) {
                    Style.PRIMARY -> {
                        background = context.getDrawable(R.drawable.dialog_btn_primary)
                        setTextColor(Color.WHITE)
                    }
                    Style.GHOST -> {
                        background = context.getDrawable(R.drawable.dialog_btn_ghost)
                        setTextColor(Color.parseColor("#4A5047"))
                    }
                    Style.DANGER -> {
                        background = context.getDrawable(R.drawable.dialog_btn_danger)
                        setTextColor(Color.parseColor("#DC2626"))
                    }
                }

                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    if (index > 0) marginStart = dp(context, 10)
                }

                setOnClickListener {
                    if (dismissOnClick) dialog.dismiss()
                    action.onClick?.invoke()
                }
            }
            row.addView(btn)
        }

        return row
    }

    private fun roundedBg(context: Context, fill: String, stroke: String) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            setStroke(dp(context, 1), Color.parseColor(stroke))
            cornerRadius = dp(context, 12).toFloat()
        }

    private fun dp(context: Context, v: Int) =
        (v * context.resources.displayMetrics.density).toInt()
}