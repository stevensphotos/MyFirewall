package com.fw.myfirewall.model

import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap,
    val isSystem: Boolean,
    val hasInternet: Boolean,
    val wifiAllowed: Boolean,
    val mobileAllowed: Boolean,
    val roamingAllowed: Boolean
)
