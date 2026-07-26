package com.example.cadernodoprofesor.ui.theme

import androidx.compose.ui.graphics.Color

// Base Canvas
val WhiteCanvas = Color(0xFFFFFFFF)
val LightGrayCanvas = Color(0xFFF8F9FA)

// Functional Rainbow (Primary Palette)
object FunctionalColors {
    val Red = Color(0xFFE53935)    // Urxencia / Alerta / Baixa
    val Orange = Color(0xFFFB8C00) // Pendente / Atención / Festivo
    val Yellow = Color(0xFFFFB300) // Importante / Evento
    val Green = Color(0xFF43A047)  // Completado / Éxito / Alta
    val Blue = Color(0xFF1E88E5)   // Primario / Información
    val Purple = Color(0xFF8E24AA) // Categoría Especial / Ingreso
}

// Material 3 Semantic Mapping
val PrimaryBlue = FunctionalColors.Blue
val OnPrimaryBlue = Color.White
val SecondaryPurple = FunctionalColors.Purple
val TertiaryGreen = FunctionalColors.Green

val DarkText = Color(0xFF1C1B1F)
val GrayText = Color(0xFF49454F)
val OutlineVariant = Color(0xFFCAC4D0)
