package com.resonance.music.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.resonance.music.R

// Bundled OFL-licensed variable fonts (see /licenses). Each weight maps to the
// font's wght axis via FontVariation so we ship one file per family.
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight): Font =
    Font(
        resId = resId,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
    )

/** Space Grotesk — display / headings / brand. */
val SpaceGrotesk = FontFamily(
    variableFont(R.font.space_grotesk, FontWeight.Normal),
    variableFont(R.font.space_grotesk, FontWeight.Medium),
    variableFont(R.font.space_grotesk, FontWeight.SemiBold),
    variableFont(R.font.space_grotesk, FontWeight.Bold),
)

/** Inter — body / labels. */
val Inter = FontFamily(
    variableFont(R.font.inter, FontWeight.Normal),
    variableFont(R.font.inter, FontWeight.Medium),
    variableFont(R.font.inter, FontWeight.SemiBold),
)
