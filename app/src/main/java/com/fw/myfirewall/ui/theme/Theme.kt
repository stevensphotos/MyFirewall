package com.fw.myfirewall.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ############################################################################
// COLOR SCHEMES DEFINITIONS
// ############################################################################

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val PureBlackColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color.White
)

/**
 * MyFirewallTheme:
 * Gère l'application globale du thème Material 3.
 * Supporte le mode Noir Pur (OLED) et les couleurs dynamiques (Material You).
 */
@Composable
fun MyFirewallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Mode Noir Pur avec Couleurs Dynamiques (Android 12+)
        pureBlack && darkTheme && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context).copy(
                background = Color.Black,
                surface = Color.Black,
                onBackground = Color.White,
                onSurface = Color.White,
                surfaceVariant = Color(0xFF121212),
                onSurfaceVariant = Color.White
            )
        }
        // Mode Noir Pur standard
        pureBlack && darkTheme -> PureBlackColorScheme
        
        // Couleurs Dynamiques standards
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // ----------------------------------------------------------------------------
    // SYSTEM UI CONFIGURATION (StatusBar & NavigationBar)
    // ----------------------------------------------------------------------------
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Utilisation du contrôleur d'insets pour gérer la visibilité des icônes.
            // Note : Pour une transparence parfaite, enableEdgeToEdge() doit être appelé dans MainActivity.
            val insetsController = WindowCompat.getInsetsController(window, view)

            // Si le thème est sombre (darkTheme ou pureBlack), les icônes doivent être claires (AppearanceLight = false)
            // Si le thème est clair, les icônes doivent être sombres (AppearanceLight = true)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
