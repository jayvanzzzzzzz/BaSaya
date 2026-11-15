package ph.basaya.app.ui

import ph.basaya.app.ui.feedback.StudioDialog as Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import ph.basaya.app.R

/** Shared farewell confirmation for the profile and toolbar logout actions. */
object LogoutDialog {
    fun show(context: Context, onLogout: () -> Unit) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_logout)
        dialog.setTitle(R.string.pf_logout_title)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.findViewById<View>(R.id.btnLogoutStay).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnLogoutConfirm).setOnClickListener {
            dialog.dismiss()
            onLogout()
        }
        dialog.show()
        val density = context.resources.displayMetrics.density
        val width = minOf((360 * density).toInt(), context.resources.displayMetrics.widthPixels - (40 * density).toInt())
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
