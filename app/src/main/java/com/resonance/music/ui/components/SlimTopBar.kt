package com.resonance.music.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A compact stand-in for M3's small [androidx.compose.material3.TopAppBar], which
 * is a fixed 64dp tall. This trims the top chrome to [height] (default 48dp) plus
 * the status-bar inset. The bar owns the status-bar inset itself, so a hosting
 * Scaffold lays the screen body out directly beneath it with no doubled padding.
 */
@Composable
fun SlimTopBar(
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit
) {
    Surface(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(height)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Title + optional trailing actions, the common case for top-level screens. */
@Composable
fun SlimTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    SlimTopBar(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}
