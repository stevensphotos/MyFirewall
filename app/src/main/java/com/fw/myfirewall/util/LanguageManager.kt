package com.fw.myfirewall.util

import android.content.Context
import java.util.Locale

object LanguageManager {
    fun setLocale(context: Context, languageCode: String) {
        val locale = if (languageCode == "system") {
            // Get system locale
            val systemLocales = android.content.res.Resources.getSystem().configuration.locales
            if (!systemLocales.isEmpty) systemLocales[0] else Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageCode)
        }
        
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
