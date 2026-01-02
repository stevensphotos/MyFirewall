package com.fw.myfirewall.viewmodel

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import com.fw.myfirewall.R

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    // Suppression de la condition if (Build.VERSION.SDK_INT >= Q) car minSdk = 29
    // unsafeCheckOpNoThrow est la méthode recommandée pour l'API 29+
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun openUsageStatsSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (e: Exception) {
        // Utilisation de la ressource existante pour éviter le texte en dur
        Toast.makeText(context, context.getString(R.string.error_open_settings), Toast.LENGTH_SHORT).show()
    }
}
