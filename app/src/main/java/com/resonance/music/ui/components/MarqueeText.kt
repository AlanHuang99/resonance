package com.resonance.music.ui.components

import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = 1
) {
    val scrollState = rememberScrollState()
    var needsScrolling by remember { mutableStateOf(false) }
    var textWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }

    LaunchedEffect(textWidth, containerWidth) {
        needsScrolling = textWidth > containerWidth
    }

    if (needsScrolling) {
        LaunchedEffect(text) {
            while (true) {
                // Pause at start
                delay(1500)
                // Scroll to end
                scrollState.animateScrollTo(
                    scrollState.maxValue,
                    animationSpec = tween(
                        durationMillis = (scrollState.maxValue * 15).coerceIn(1000, 8000),
                        easing = LinearEasing
                    )
                )
                // Pause at end
                delay(1500)
                // Scroll back
                scrollState.animateScrollTo(
                    0,
                    animationSpec = tween(
                        durationMillis = (scrollState.maxValue * 15).coerceIn(1000, 8000),
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width }
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = if (needsScrolling) TextOverflow.Visible else TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier
                .then(if (needsScrolling) Modifier.horizontalScroll(scrollState) else Modifier)
                .onSizeChanged { textWidth = it.width }
        )
    }
}
