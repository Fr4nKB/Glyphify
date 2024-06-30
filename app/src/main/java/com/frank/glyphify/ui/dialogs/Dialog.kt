package com.frank.glyphify.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.frank.glyphify.R

object Dialog {
    fun showDialog(
        context: Context,
        layoutId: Int,
        buttonActions: Map<Int, (View) -> Unit>,
        isCancelable: Boolean = true,
        delayEnableButtonId: Int? = null,
        delayMillis: Long = 0
    ) {
        val dialogView = LayoutInflater.from(context).inflate(layoutId, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(isCancelable)
            .create()

        for ((buttonId, action) in buttonActions) {
            val button = dialogView.findViewById<Button>(buttonId)
            button.setOnClickListener {
                action(dialogView)
                dialog.dismiss()
            }
            button.backgroundTintList = null
        }

        dialogView.findViewById<EditText>(R.id.editText)?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                dialogView.findViewById<Button>(R.id.positiveButton).isEnabled = !s.isNullOrEmpty()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        delayEnableButtonId?.let {
            dialogView.findViewById<Button>(it).isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                dialogView.findViewById<Button>(it).isEnabled = true
            }, delayMillis)
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }


}