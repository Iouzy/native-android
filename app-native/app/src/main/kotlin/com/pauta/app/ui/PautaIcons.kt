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
            "PautaHoje",
            // circle cx=12 cy=12 r=4 (as two arcs) + the 8 sun rays.
            "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8",
            "M12 2v3M12 19v3M2 12h3M19 12h3M4.93 4.93l2.12 2.12M16.95 16.95l2.12 2.12M4.93 19.07l2.12-2.12M16.95 7.05l2.12-2.12",
        )
    }

    val Pauta: ImageVector by lazy {
        strokeIcon(
            "PautaPauta",
            // circle cx=12 cy=12 r=9 + clock hands.
            "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18",
            "M12 7v5l3 2",
        )
    }

    val Mares: ImageVector by lazy {
        strokeIcon(
            "PautaMares",
            "M3 12c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2",
            "M3 17c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2",
            "M3 7c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2",
        )
    }

    val Gear: ImageVector by lazy {
        strokeIcon(
            "PautaGear",
            // circle cx=12 cy=12 r=3 + the 8 teeth.
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
            "M12 2v2.5M12 19.5V22M4.2 4.2l1.8 1.8M18 18l1.8 1.8M2 12h2.5M19.5 12H22M4.2 19.8l1.8-1.8M18 6l1.8-1.8",
        )
    }

    /** Builds a stroked 24×24 vector from SVG path data, matching the web SVGs'
     *  `fill=none stroke=currentColor strokeWidth=1.6 round caps/joins`. */
    private fun strokeIcon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
