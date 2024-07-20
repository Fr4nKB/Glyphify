package com.frank.glyphify

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.frank.glyphify.ui.dialogs.Dialog

class PermissionHandling(private val activity: Activity) {

    private fun askPermissions(permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    /**shows alert dialog, if ok is pressed a set of permissions are requested otherwise the app is closed*/
    private fun popUp(permissions: Array<String>, layout: Int, requestCode: Int) {
        Dialog.showDialog(
            activity,
            layout,
            mapOf(
                R.id.positiveButton to {
                    askPermissions(permissions, requestCode)
                },
                R.id.negativeButton to { }
            )
        )
    }

    fun checkRequiredPermission(permissions: List<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun askRequiredPermissions(permissions: List<String>, layout: Int) {
        if (!checkRequiredPermission(permissions)) {
            popUp(permissions.toTypedArray(), layout, 1)
        }
    }

}