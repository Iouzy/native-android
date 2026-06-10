package com.pauta.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Pauta's own icon set, ported 1:1 from the inline SVGs in
 * src/ui-primitives.jsx (24×24 viewport, 1.6 stroke, round caps/joins) so the
 * native shell draws the exact same glyphs as the web app: the Hoje sun, the
 * Pauta clock, the Marés waves, and the Settings gear. Drawn in black and tinted
 * at the call site, like any Compose icon. // PT: ícones próprios da Pauta,
 * copiados dos SVGs da web — sol, relógio, ondas e engrenagem.
 */
object PautaIcons {

    val Hoje: ImageVector by lazy {
        strokeIcon(
            "PautaHoje", 24f, 1.6f,
            // circle cx=12 cy=12 r=4 (as two arcs) + the 8 sun rays.
            "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8",
            "M12 2v3M12 19v3M2 12h3M19 12h3M4.93 4.93l2.12 2.12M16.95 16.95l2.12 2.12M4.93 19.07l2.12-2.12M16.95 7.05l2.12-2.12",
        )
    }

    val Pauta: ImageVector by lazy {
        strokeIcon(
            "PautaPauta", 24f, 1.6f,
            // circle cx=12 cy=12 r=9 + clock hands.
            "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18",
            "M12 7v5l3 2",
        )
    }

    val Mares: ImageVector by lazy {
        strokeIcon(
            "PautaMares", 24f, 1.6f,
            "M3 12c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2",
            "M3 17c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2",
            "M3 7c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2",
        )
    }

    val Gear: ImageVector by lazy {
        strokeIcon(
            "PautaGear", 24f, 1.6f,
            // circle cx=12 cy=12 r=3 + the 8 teeth.
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
            "M12 2v2.5M12 19.5V22M4.2 4.2l1.8 1.8M18 18l1.8 1.8M2 12h2.5M19.5 12H22M4.2 19.8l1.8-1.8M18 6l1.8-1.8",
        )
    }

    /** The small check mark (12×12, 1.8 stroke) used inside filled state
     *  circles — the web's Icon.Check. */
    val Check: ImageVector by lazy {
        strokeIcon("PautaCheck", 12f, 1.8f, "M2.5 6.5L5 9L9.5 3.5")
    }

    /** Builds a stroked square vector from SVG path data, matching the web
     *  SVGs' `fill=none stroke=currentColor round caps/joins`. */
    private fun strokeIcon(name: String, viewport: Float, strokeWidth: Float, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = viewport.dp,
            defaultHeight = viewport.dp,
            viewportWidth = viewport,
            viewportHeight = viewport,
        )
        for (d in paths) {
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
