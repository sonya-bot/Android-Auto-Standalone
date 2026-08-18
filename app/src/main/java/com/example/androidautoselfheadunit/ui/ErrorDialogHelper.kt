package com.example.androidautoselfheadunit.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog

object ErrorDialogHelper {
    fun showHeadUnitServerDownDialog(
        context: Context,
        onRetry: () -> Unit,
        onCancel: () -> Unit,
    ) {
        val msg =
            "Failed to connect to the Head Unit Server.\n\n" +
                "Please open Android Auto Settings and start server.\n\n" +
                "Do you want to retry connection?"
        AlertDialog.Builder(context)
            .setTitle("Connection Error")
            .setMessage(msg)
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onCancel()
            }
            .setCancelable(false)
            .show()
    }
}
