package com.example.androidautoselfheadunit.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.androidautoselfheadunit.R

object ErrorDialogHelper {
    fun showHeadUnitServerDownDialog(
        context: Context,
        onRetry: () -> Unit,
        onCancel: () -> Unit,
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(R.string.connection_error_title)
            .setMessage(R.string.connection_error_message)
            .setPositiveButton(R.string.retry) { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                onCancel()
            }
            .setCancelable(false)
            .show()
    }

    fun showAccessibilityPermissionDialog(
        context: Context,
        onOpenSettings: () -> Unit,
        onCancel: () -> Unit,
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(R.string.accessibility_permission_title)
            .setMessage(R.string.accessibility_permission_message)
            .setPositiveButton(R.string.open_settings) { dialog, _ ->
                dialog.dismiss()
                onOpenSettings()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                onCancel()
            }
            .setCancelable(false)
            .show()
    }
}
