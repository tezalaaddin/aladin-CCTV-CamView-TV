package com.aladin.aladincamviewer

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object SecurityUtils {

    private const val MASTER_PIN = "0000" // Hardcoded admin override

    fun checkPin(context: Context, onResult: (Boolean) -> Unit) {
        val prefHelper = PreferenceHelper(context)
        val savedPin = prefHelper.appPin

        if (savedPin.isEmpty()) {
            onResult(true)
            return
        }

        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = context.getString(R.string.enter_pin)
            textAlignment = EditText.TEXT_ALIGNMENT_CENTER
            letterSpacing = 0.5f
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.auth_required))
            .setMessage(context.getString(R.string.auth_message))
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.confirm)) { _, _ ->
                val enteredPin = input.text.toString()
                if (enteredPin == savedPin || enteredPin == MASTER_PIN) {
                    onResult(true)
                } else {
                    Toast.makeText(context, context.getString(R.string.incorrect_pin), Toast.LENGTH_SHORT).show()
                    onResult(false)
                }
            }
            .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                onResult(false)
            }
            .show()
    }
}
