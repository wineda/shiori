package com.wineda.shiori.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ShipporiMincho = FontFamily.Serif

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = ShipporiMincho, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = ShipporiMincho, fontWeight = FontWeight.Medium, fontSize = 20.sp),
    bodyLarge = TextStyle(fontFamily = ShipporiMincho, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = ShipporiMincho, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 22.sp),
    labelSmall = TextStyle(fontFamily = ShipporiMincho, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 2.sp),
)
