package pt.tiago.cinzeiro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing in multiples of 4 dp; side margin 16 dp; corners: card 16 dp, chip 8 dp. */
object Dimens {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    val screenPadding = 16.dp
    val cardCorner = 16.dp
    val chipCorner = 8.dp
    val plusButton = 128.dp

    val cardShape = RoundedCornerShape(cardCorner)
    val chipShape = RoundedCornerShape(chipCorner)
}
