package com.iptv.family.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.iptv.family.theme.TvFocusColor

/**
 * Marca un elemento como navegable por mando: aplica un borde de color muy
 * contrastado (ambar) y un ligero zoom cuando recibe el foco de D-pad. Sin
 * esto, el resaltado de Material3 (azul sobre azul oscuro) es casi invisible
 * en la tele.
 */
@Composable
fun Modifier.tvFocusable(shape: Shape, onFocusChange: (Boolean) -> Unit = {}): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "tvFocusScale")
    return this
        .scale(scale)
        .onFocusChanged {
            focused = it.isFocused
            onFocusChange(focused)
        }
        .focusable(interactionSource = remember { MutableInteractionSource() })
        .then(if (focused) Modifier.border(3.dp, TvFocusColor, shape) else Modifier)
}
