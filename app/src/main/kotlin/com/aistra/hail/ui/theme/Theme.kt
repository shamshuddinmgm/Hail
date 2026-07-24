package com.aistra.hail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aistra.hail.R
import com.aistra.hail.app.HailData
import com.aistra.hail.utils.HTarget
import com.aistra.hail.utils.HTheme

private fun Int.toComposeColor(context: android.content.Context) =
    Color(ContextCompat.getColor(context, this))

private fun darkFrom(
    context: android.content.Context,
    primary: Int,
    onPrimary: Int,
    primaryContainer: Int,
    onPrimaryContainer: Int,
    secondary: Int,
    onSecondary: Int,
    secondaryContainer: Int,
    onSecondaryContainer: Int,
    tertiary: Int,
    onTertiary: Int,
    tertiaryContainer: Int,
    onTertiaryContainer: Int,
    background: Int,
    onBackground: Int,
    surface: Int,
    onSurface: Int,
    surfaceVariant: Int,
    onSurfaceVariant: Int,
    outline: Int,
) = darkColorScheme(
    primary = primary.toComposeColor(context),
    onPrimary = onPrimary.toComposeColor(context),
    primaryContainer = primaryContainer.toComposeColor(context),
    onPrimaryContainer = onPrimaryContainer.toComposeColor(context),
    secondary = secondary.toComposeColor(context),
    onSecondary = onSecondary.toComposeColor(context),
    secondaryContainer = secondaryContainer.toComposeColor(context),
    onSecondaryContainer = onSecondaryContainer.toComposeColor(context),
    tertiary = tertiary.toComposeColor(context),
    onTertiary = onTertiary.toComposeColor(context),
    tertiaryContainer = tertiaryContainer.toComposeColor(context),
    onTertiaryContainer = onTertiaryContainer.toComposeColor(context),
    background = background.toComposeColor(context),
    onBackground = onBackground.toComposeColor(context),
    surface = surface.toComposeColor(context),
    onSurface = onSurface.toComposeColor(context),
    surfaceVariant = surfaceVariant.toComposeColor(context),
    onSurfaceVariant = onSurfaceVariant.toComposeColor(context),
    outline = outline.toComposeColor(context),
)

private fun typographyFor(theme: String): Typography {
    val family = when (theme) {
        HailData.THEME_NEON_HACKER -> FontFamily.Monospace
        HailData.THEME_NEON_CYBER, HailData.THEME_DARK_GRAY -> FontFamily.SansSerif
        HailData.THEME_CHOCOLATE -> FontFamily.Serif
        else -> FontFamily.Default
    }
    val weight = when (theme) {
        HailData.THEME_EMBER -> FontWeight.Black
        HailData.THEME_NEON_PLASMA, HailData.THEME_MIDNIGHT -> FontWeight.Medium
        HailData.THEME_NEON_ICE -> FontWeight.Light
        else -> FontWeight.Normal
    }
    val base = TextStyle(fontFamily = family, fontWeight = weight, fontSize = 16.sp, lineHeight = 24.sp)
    return Typography(
        bodyLarge = base,
        bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
        titleLarge = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        labelLarge = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme() || HTheme.isForcedDark(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val theme = HailData.appTheme
    val colorScheme = when (theme) {
        HailData.FOLLOW_SYSTEM -> if (HTarget.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (darkTheme) darkColorScheme() else lightColorScheme()

        HailData.THEME_LIGHT -> lightColorScheme()
        HailData.THEME_DARK -> darkColorScheme()

        HailData.THEME_AMOLED -> darkFrom(
            context, R.color.amoled_primary, R.color.amoled_onPrimary, R.color.amoled_primaryContainer,
            R.color.amoled_onPrimaryContainer, R.color.amoled_secondary, R.color.amoled_onSecondary,
            R.color.amoled_secondaryContainer, R.color.amoled_onSecondaryContainer, R.color.amoled_tertiary,
            R.color.amoled_onTertiary, R.color.amoled_tertiaryContainer, R.color.amoled_onTertiaryContainer,
            R.color.amoled_background, R.color.amoled_onBackground, R.color.amoled_surface, R.color.amoled_onSurface,
            R.color.amoled_surfaceVariant, R.color.amoled_onSurfaceVariant, R.color.amoled_outline
        )
        HailData.THEME_DARK_GRAY -> darkFrom(
            context, R.color.gray_primary, R.color.gray_onPrimary, R.color.gray_primaryContainer,
            R.color.gray_onPrimaryContainer, R.color.gray_secondary, R.color.gray_onSecondary,
            R.color.gray_secondaryContainer, R.color.gray_onSecondaryContainer, R.color.gray_tertiary,
            R.color.gray_onTertiary, R.color.gray_tertiaryContainer, R.color.gray_onTertiaryContainer,
            R.color.gray_background, R.color.gray_onBackground, R.color.gray_surface, R.color.gray_onSurface,
            R.color.gray_surfaceVariant, R.color.gray_onSurfaceVariant, R.color.gray_outline
        )
        HailData.THEME_CHOCOLATE -> darkFrom(
            context, R.color.choco_primary, R.color.choco_onPrimary, R.color.choco_primaryContainer,
            R.color.choco_onPrimaryContainer, R.color.choco_secondary, R.color.choco_onSecondary,
            R.color.choco_secondaryContainer, R.color.choco_onSecondaryContainer, R.color.choco_tertiary,
            R.color.choco_onTertiary, R.color.choco_tertiaryContainer, R.color.choco_onTertiaryContainer,
            R.color.choco_background, R.color.choco_onBackground, R.color.choco_surface, R.color.choco_onSurface,
            R.color.choco_surfaceVariant, R.color.choco_onSurfaceVariant, R.color.choco_outline
        )
        HailData.THEME_MIDNIGHT -> darkFrom(
            context, R.color.mid_primary, R.color.mid_onPrimary, R.color.mid_primaryContainer,
            R.color.mid_onPrimaryContainer, R.color.mid_secondary, R.color.mid_onSecondary,
            R.color.mid_secondaryContainer, R.color.mid_onSecondaryContainer, R.color.mid_tertiary,
            R.color.mid_onTertiary, R.color.mid_tertiaryContainer, R.color.mid_onTertiaryContainer,
            R.color.mid_background, R.color.mid_onBackground, R.color.mid_surface, R.color.mid_onSurface,
            R.color.mid_surfaceVariant, R.color.mid_onSurfaceVariant, R.color.mid_outline
        )
        HailData.THEME_EMBER -> darkFrom(
            context, R.color.ember_primary, R.color.ember_onPrimary, R.color.ember_primaryContainer,
            R.color.ember_onPrimaryContainer, R.color.ember_secondary, R.color.ember_onSecondary,
            R.color.ember_secondaryContainer, R.color.ember_onSecondaryContainer, R.color.ember_tertiary,
            R.color.ember_onTertiary, R.color.ember_tertiaryContainer, R.color.ember_onTertiaryContainer,
            R.color.ember_background, R.color.ember_onBackground, R.color.ember_surface, R.color.ember_onSurface,
            R.color.ember_surfaceVariant, R.color.ember_onSurfaceVariant, R.color.ember_outline
        )
        HailData.THEME_NEON_HACKER -> darkFrom(
            context, R.color.hacker_primary, R.color.hacker_onPrimary, R.color.hacker_primaryContainer,
            R.color.hacker_onPrimaryContainer, R.color.hacker_secondary, R.color.hacker_onSecondary,
            R.color.hacker_secondaryContainer, R.color.hacker_onSecondaryContainer, R.color.hacker_tertiary,
            R.color.hacker_onTertiary, R.color.hacker_tertiaryContainer, R.color.hacker_onTertiaryContainer,
            R.color.hacker_background, R.color.hacker_onBackground, R.color.hacker_surface, R.color.hacker_onSurface,
            R.color.hacker_surfaceVariant, R.color.hacker_onSurfaceVariant, R.color.hacker_outline
        )
        HailData.THEME_NEON_CYBER -> darkFrom(
            context, R.color.cyber_primary, R.color.cyber_onPrimary, R.color.cyber_primaryContainer,
            R.color.cyber_onPrimaryContainer, R.color.cyber_secondary, R.color.cyber_onSecondary,
            R.color.cyber_secondaryContainer, R.color.cyber_onSecondaryContainer, R.color.cyber_tertiary,
            R.color.cyber_onTertiary, R.color.cyber_tertiaryContainer, R.color.cyber_onTertiaryContainer,
            R.color.cyber_background, R.color.cyber_onBackground, R.color.cyber_surface, R.color.cyber_onSurface,
            R.color.cyber_surfaceVariant, R.color.cyber_onSurfaceVariant, R.color.cyber_outline
        )
        HailData.THEME_NEON_PLASMA -> darkFrom(
            context, R.color.plasma_primary, R.color.plasma_onPrimary, R.color.plasma_primaryContainer,
            R.color.plasma_onPrimaryContainer, R.color.plasma_secondary, R.color.plasma_onSecondary,
            R.color.plasma_secondaryContainer, R.color.plasma_onSecondaryContainer, R.color.plasma_tertiary,
            R.color.plasma_onTertiary, R.color.plasma_tertiaryContainer, R.color.plasma_onTertiaryContainer,
            R.color.plasma_background, R.color.plasma_onBackground, R.color.plasma_surface, R.color.plasma_onSurface,
            R.color.plasma_surfaceVariant, R.color.plasma_onSurfaceVariant, R.color.plasma_outline
        )
        HailData.THEME_NEON_ICE -> darkFrom(
            context, R.color.ice_primary, R.color.ice_onPrimary, R.color.ice_primaryContainer,
            R.color.ice_onPrimaryContainer, R.color.ice_secondary, R.color.ice_onSecondary,
            R.color.ice_secondaryContainer, R.color.ice_onSecondaryContainer, R.color.ice_tertiary,
            R.color.ice_onTertiary, R.color.ice_tertiaryContainer, R.color.ice_onTertiaryContainer,
            R.color.ice_background, R.color.ice_onBackground, R.color.ice_surface, R.color.ice_onSurface,
            R.color.ice_surfaceVariant, R.color.ice_onSurfaceVariant, R.color.ice_outline
        )
        else -> if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typographyFor(theme),
        content = content
    )
}
