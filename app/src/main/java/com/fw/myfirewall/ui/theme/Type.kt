package com.fw.myfirewall.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// In ui/theme/Type.kt
val Typography = Typography(
    // Titles (used in TopAppBars and Card headers)
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium, // Medium (500) is the standard for AOSP headers
        fontSize = 24.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    // Section Headers
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    // Main App Titles
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal, // AOSP often uses Normal for large headlines
        fontSize = 24.sp,
        lineHeight = 32.sp
    )
)
