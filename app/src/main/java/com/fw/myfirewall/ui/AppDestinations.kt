package com.fw.myfirewall.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.fw.myfirewall.R

enum class AppDestinations(
    @StringRes val labelRes: Int, // Store the ID, not the String
    val icon: ImageVector,
) {
    STATUS(R.string.status_title, Icons.Default.Dashboard),
    APPLICATIONS(R.string.applications_title, Icons.Default.Apps),
    RULES(R.string.rules_title, Icons.Default.List),
    SETTINGS(R.string.settings_title, Icons.Default.Settings),
    SAVE_RULES(R.string.export_rules_label, Icons.Default.Save)
}
