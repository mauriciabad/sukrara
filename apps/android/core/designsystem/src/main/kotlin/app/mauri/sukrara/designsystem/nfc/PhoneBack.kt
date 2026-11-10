// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.nfc

import android.nfc.NfcAdapter
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Where the antenna is when the phone doesn't say: the upper middle of the back. */
val DefaultAntenna = Offset(0.5f, 0.45f)

/**
 * Where this phone's NFC antenna is, as a fraction of the phone's back (x from the left, y from the top, back view).
 * `AvailableNfcAntenna` gives millimetres from the top left of the front in portrait, so x is mirrored.
 */
fun antennaSpot(nfc: NfcAdapter?): Offset {
    val info = runCatching { nfc?.nfcAntennaInfo }.getOrNull()
    val antenna = info?.availableNfcAntennas?.firstOrNull()
    if (info == null || antenna == null || info.deviceWidth <= 0 || info.deviceHeight <= 0) return DefaultAntenna
    return Offset(
        (1f - antenna.locationX.toFloat() / info.deviceWidth).coerceIn(0.2f, 0.8f),
        (antenna.locationY.toFloat() / info.deviceHeight).coerceIn(0.15f, 0.85f),
    )
}

/** Where [phoneBack] put the antenna ([spot], px) and how wide it drew the phone ([width], px). */
data class PhoneBackSpot(
    val spot: Offset,
    val width: Float,
)

/**
 * The back of a phone, centred and 96 % of the canvas high, with its camera island; [spot] is the antenna as
 * [antennaSpot] gives it. The new-sensor scan and the pens page draw on top of it.
 */
fun DrawScope.phoneBack(
    spot: Offset,
    body: Color,
    outline: Color,
    island: Color,
): PhoneBackSpot {
    val ph = size.height * 0.96f
    val pw = ph * 0.48f
    val left = (size.width - pw) / 2
    val top = (size.height - ph) / 2
    val corner = CornerRadius(pw * 0.16f)
    drawRoundRect(body, Offset(left, top), Size(pw, ph), corner)
    drawRoundRect(outline, Offset(left, top), Size(pw, ph), corner, style = Stroke(2.dp.toPx()))
    // the camera island, top left of the back
    val cam = pw * 0.28f
    val at = Offset(left + pw * 0.1f, top + pw * 0.1f)
    drawRoundRect(island, at, Size(cam, cam * 1.4f), CornerRadius(cam * 0.32f))
    drawCircle(outline, cam * 0.24f, Offset(at.x + cam / 2, at.y + cam * 0.4f))
    drawCircle(outline, cam * 0.24f, Offset(at.x + cam / 2, at.y + cam * 1.0f))
    return PhoneBackSpot(Offset(left + pw * spot.x, top + ph * spot.y), pw)
}

/**
 * The back of this phone with a circle where its NFC antenna is ([spot]): where to touch a pen or a sensor. Still
 * (no motion: it sits in a page, not in a step that waits for a tap); [description] is what TalkBack says.
 */
@Composable
fun AntennaArt(
    spot: Offset,
    description: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Canvas(modifier.semantics { contentDescription = description }) {
        val p = phoneBack(spot, cs.surfaceContainerHighest, cs.outline, cs.surfaceContainerLow)
        val r = p.width * 0.2f
        drawCircle(cs.primary.copy(alpha = 0.18f), r * 1.6f, p.spot)
        drawCircle(cs.primary, r, p.spot, style = Stroke(3.dp.toPx()))
    }
}
