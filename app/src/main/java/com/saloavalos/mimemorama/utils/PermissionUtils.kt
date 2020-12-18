package com.saloavalos.mimemorama.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

fun isPermissionGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) ==PackageManager.PERMISSION_GRANTED
}

fun requestPermission(actvity: Activity?, permission: String, requestCode: Int) {
    ActivityCompat.requestPermissions(actvity!!, arrayOf(permission), requestCode)
}