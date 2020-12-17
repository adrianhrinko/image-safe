package com.safetica.datasafe.utils

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.DisplayMetrics
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.safetica.datasafe.R

object DialogUtility {

    /**
     * Shows dialog
     * @param activity in which the dialog will be displayed
     * @param titleId id of string resource for title
     * @param messageId id of string resource for message
     * @param action function to be invoked by click on ok button
     */
    fun showDialog(activity: FragmentActivity, titleId: Int, messageId: Int, action: (() -> Unit)? = null): Dialog {
        val dialog = Dialog(activity)
        dialog.show()

        val metrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(metrics)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setContentView(R.layout.dialog_layout)


        val title = dialog.findViewById<TextView>(R.id.title_dialog)
        title.setText(titleId)
        val message = dialog.findViewById<TextView>(R.id.message_dialog)
        message.setText(messageId)
        val close = dialog.findViewById<TextView>(R.id.dialog_close)
        close.setOnClickListener {
            dialog.dismiss()
        }

        val ok = dialog.findViewById<TextView>(R.id.dialog_ok)
        ok.setOnClickListener {
            action?.invoke()
            dialog.dismiss()
        }
        return dialog
    }
    interface Result {
        fun available(result: String)
    }
}