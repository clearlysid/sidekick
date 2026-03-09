package com.sidekick.watch.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

data class AppTheme(
    val id: String,
    val displayName: String,
    val previewColor: Color,
    val colorScheme: ColorScheme,
)

val appThemes = listOf(
    AppTheme(
        id = "default",
        displayName = "Default",
        previewColor = Color(0xFFD0BCFF),
        colorScheme = ColorScheme(),
    ),
    AppTheme(
        id = "ocean",
        displayName = "Ocean",
        previewColor = Color(0xFF80CFFF),
        colorScheme = ColorScheme(
            primary = Color(0xFF80CFFF),
            primaryDim = Color(0xFF5AAFDF),
            primaryContainer = Color(0xFF00497A),
            onPrimary = Color(0xFF003354),
            onPrimaryContainer = Color(0xFFC5E7FF),
            secondary = Color(0xFF6DD5C8),
            secondaryDim = Color(0xFF4DB5A8),
            secondaryContainer = Color(0xFF1E4E47),
            onSecondary = Color(0xFF003731),
            onSecondaryContainer = Color(0xFFB0F1E4),
            tertiary = Color(0xFFBBC6EA),
            tertiaryDim = Color(0xFF9BA6CA),
            tertiaryContainer = Color(0xFF3B4664),
            onTertiary = Color(0xFF253048),
            onTertiaryContainer = Color(0xFFD9E2FF),
        ),
    ),
    AppTheme(
        id = "sunset",
        displayName = "Sunset",
        previewColor = Color(0xFFFFB77C),
        colorScheme = ColorScheme(
            primary = Color(0xFFFFB77C),
            primaryDim = Color(0xFFDF9A5F),
            primaryContainer = Color(0xFF7A3E00),
            onPrimary = Color(0xFF542B00),
            onPrimaryContainer = Color(0xFFFFDCC5),
            secondary = Color(0xFFE3BFAB),
            secondaryDim = Color(0xFFC3A08B),
            secondaryContainer = Color(0xFF5B4130),
            onSecondary = Color(0xFF432B1B),
            onSecondaryContainer = Color(0xFFFFDBCA),
            tertiary = Color(0xFFD1C88E),
            tertiaryDim = Color(0xFFB1A871),
            tertiaryContainer = Color(0xFF4B4524),
            onTertiary = Color(0xFF342F10),
            onTertiaryContainer = Color(0xFFEDE4A7),
        ),
    ),
    AppTheme(
        id = "mint",
        displayName = "Mint",
        previewColor = Color(0xFF7BDFAC),
        colorScheme = ColorScheme(
            primary = Color(0xFF7BDFAC),
            primaryDim = Color(0xFF5BBF8C),
            primaryContainer = Color(0xFF005234),
            onPrimary = Color(0xFF003A23),
            onPrimaryContainer = Color(0xFFA0F7C7),
            secondary = Color(0xFFB3CCBC),
            secondaryDim = Color(0xFF93AC9C),
            secondaryContainer = Color(0xFF3A4E42),
            onSecondary = Color(0xFF24382C),
            onSecondaryContainer = Color(0xFFCFE8D8),
            tertiary = Color(0xFFA3CEDF),
            tertiaryDim = Color(0xFF83AEBF),
            tertiaryContainer = Color(0xFF274958),
            onTertiary = Color(0xFF123340),
            onTertiaryContainer = Color(0xFFBFEAFC),
        ),
    ),
    AppTheme(
        id = "rose",
        displayName = "Rose",
        previewColor = Color(0xFFFFB1C8),
        colorScheme = ColorScheme(
            primary = Color(0xFFFFB1C8),
            primaryDim = Color(0xFFDF91A8),
            primaryContainer = Color(0xFF7A2946),
            onPrimary = Color(0xFF541432),
            onPrimaryContainer = Color(0xFFFFD9E3),
            secondary = Color(0xFFE3BDC6),
            secondaryDim = Color(0xFFC39DA6),
            secondaryContainer = Color(0xFF5B3F47),
            onSecondary = Color(0xFF432931),
            onSecondaryContainer = Color(0xFFFFD9E1),
            tertiary = Color(0xFFEFBD94),
            tertiaryDim = Color(0xFFCF9D74),
            tertiaryContainer = Color(0xFF5B3F24),
            onTertiary = Color(0xFF432B10),
            onTertiaryContainer = Color(0xFFFFDCC3),
        ),
    ),
)

fun themeById(id: String): AppTheme = appThemes.firstOrNull { it.id == id } ?: appThemes.first()

@Composable
fun SidekickTheme(themeId: String = "default", content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = themeById(themeId).colorScheme,
        content = content,
    )
}
