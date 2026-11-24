package app.mnemo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn 24dp stroke icons (2px, round caps) so the app does not depend on
 * the large material-icons-extended artifact.
 */
object MnemoIcons {
    private fun stroke(
        name: String,
        build: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    pathFillType = PathFillType.NonZero,
                    pathBuilder = build,
                )
            }.build()

    val Home: ImageVector by lazy {
        stroke("Home") {
            moveTo(3f, 10.5f)
            lineTo(12f, 3f)
            lineTo(21f, 10.5f)
            lineTo(21f, 20f)
            lineTo(15f, 20f)
            lineTo(15f, 14f)
            lineTo(9f, 14f)
            lineTo(9f, 20f)
            lineTo(3f, 20f)
            close()
        }
    }

    val Decks: ImageVector by lazy {
        stroke("Decks") {
            moveTo(12f, 3f)
            lineTo(2f, 8f)
            lineTo(12f, 13f)
            lineTo(22f, 8f)
            close()
            moveTo(2f, 12f)
            lineTo(12f, 17f)
            lineTo(22f, 12f)
            moveTo(2f, 16f)
            lineTo(12f, 21f)
            lineTo(22f, 16f)
        }
    }

    val Browse: ImageVector by lazy {
        stroke("Browse") {
            moveTo(9f, 6f)
            lineTo(21f, 6f)
            moveTo(9f, 12f)
            lineTo(21f, 12f)
            moveTo(9f, 18f)
            lineTo(21f, 18f)
            moveTo(4f, 6f)
            lineTo(4.01f, 6f)
            moveTo(4f, 12f)
            lineTo(4.01f, 12f)
            moveTo(4f, 18f)
            lineTo(4.01f, 18f)
        }
    }

    val Graph: ImageVector by lazy {
        stroke("Graph") {
            moveTo(8.5f, 6f)
            arcTo(2.5f, 2.5f, 0f, true, true, 3.5f, 6f)
            arcTo(2.5f, 2.5f, 0f, true, true, 8.5f, 6f)
            moveTo(20.5f, 8f)
            arcTo(2.5f, 2.5f, 0f, true, true, 15.5f, 8f)
            arcTo(2.5f, 2.5f, 0f, true, true, 20.5f, 8f)
            moveTo(11.5f, 18f)
            arcTo(2.5f, 2.5f, 0f, true, true, 6.5f, 18f)
            arcTo(2.5f, 2.5f, 0f, true, true, 11.5f, 18f)
            moveTo(8.4f, 6.3f)
            lineTo(15.6f, 7.2f)
            moveTo(6.7f, 8.4f)
            lineTo(8.3f, 15.6f)
            moveTo(16.4f, 9.9f)
            lineTo(10.7f, 16.2f)
        }
    }

    val Stats: ImageVector by lazy {
        stroke("Stats") {
            moveTo(3f, 20f)
            lineTo(21f, 20f)
            moveTo(7f, 16f)
            lineTo(7f, 11f)
            moveTo(12f, 16f)
            lineTo(12f, 6f)
            moveTo(17f, 16f)
            lineTo(17f, 8f)
        }
    }

    val Settings: ImageVector by lazy {
        stroke("Settings") {
            moveTo(4f, 6f)
            lineTo(14f, 6f)
            moveTo(18f, 6f)
            lineTo(20f, 6f)
            moveTo(4f, 12f)
            lineTo(8f, 12f)
            moveTo(12f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 18f)
            lineTo(12f, 18f)
            moveTo(16f, 18f)
            lineTo(20f, 18f)
            moveTo(18f, 6f)
            arcTo(2f, 2f, 0f, true, true, 14f, 6f)
            arcTo(2f, 2f, 0f, true, true, 18f, 6f)
            moveTo(12f, 12f)
            arcTo(2f, 2f, 0f, true, true, 8f, 12f)
            arcTo(2f, 2f, 0f, true, true, 12f, 12f)
            moveTo(16f, 18f)
            arcTo(2f, 2f, 0f, true, true, 12f, 18f)
            arcTo(2f, 2f, 0f, true, true, 16f, 18f)
        }
    }

    val Plus: ImageVector by lazy {
        stroke("Plus") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val Close: ImageVector by lazy {
        stroke("Close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val Back: ImageVector by lazy {
        stroke("Back") {
            moveTo(15f, 18f)
            lineTo(9f, 12f)
            lineTo(15f, 6f)
        }
    }

    val ArrowRight: ImageVector by lazy {
        stroke("ArrowRight") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
            moveTo(13f, 6f)
            lineTo(19f, 12f)
            lineTo(13f, 18f)
        }
    }

    val Search: ImageVector by lazy {
        stroke("Search") {
            moveTo(18f, 11f)
            arcTo(7f, 7f, 0f, true, true, 4f, 11f)
            arcTo(7f, 7f, 0f, true, true, 18f, 11f)
            moveTo(20f, 20f)
            lineTo(16f, 16f)
        }
    }

    val Play: ImageVector by lazy {
        stroke("Play") {
            moveTo(7f, 4.5f)
            lineTo(7f, 19.5f)
            lineTo(19f, 12f)
            close()
        }
    }

    val Check: ImageVector by lazy {
        stroke("Check") {
            moveTo(5f, 12.5f)
            lineTo(9.5f, 17f)
            lineTo(19f, 7f)
        }
    }

    val Undo: ImageVector by lazy {
        stroke("Undo") {
            moveTo(9f, 14f)
            lineTo(4f, 9f)
            lineTo(9f, 4f)
            moveTo(4f, 9f)
            lineTo(14f, 9f)
            arcTo(6f, 6f, 0f, false, true, 14f, 21f)
            lineTo(11f, 21f)
        }
    }

    val Edit: ImageVector by lazy {
        stroke("Edit") {
            moveTo(4f, 20f)
            lineTo(8f, 20f)
            lineTo(19f, 9f)
            lineTo(15f, 5f)
            lineTo(4f, 16f)
            close()
            moveTo(13.5f, 6.5f)
            lineTo(17.5f, 10.5f)
        }
    }

    val Pause: ImageVector by lazy {
        stroke("Pause") {
            moveTo(9f, 5f)
            lineTo(9f, 19f)
            moveTo(15f, 5f)
            lineTo(15f, 19f)
        }
    }

    val Clock: ImageVector by lazy {
        stroke("Clock") {
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, true, true, 3f, 12f)
            arcTo(9f, 9f, 0f, true, true, 21f, 12f)
            moveTo(12f, 7f)
            lineTo(12f, 12f)
            lineTo(15f, 14f)
        }
    }

    val Flame: ImageVector by lazy {
        stroke("Flame") {
            moveTo(12f, 3f)
            curveTo(13f, 7f, 17f, 8.5f, 17f, 13f)
            arcTo(5f, 5f, 0f, true, true, 7f, 13f)
            curveTo(7f, 10.5f, 8.5f, 9.2f, 9.5f, 8f)
            curveTo(9.8f, 9.6f, 10.5f, 10.5f, 11.5f, 11f)
            curveTo(11.5f, 8f, 10.5f, 5.5f, 12f, 3f)
            close()
        }
    }

    val Trash: ImageVector by lazy {
        stroke("Trash") {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(10f, 11f)
            lineTo(10f, 17f)
            moveTo(14f, 11f)
            lineTo(14f, 17f)
            moveTo(6f, 7f)
            lineTo(7f, 20f)
            lineTo(17f, 20f)
            lineTo(18f, 7f)
            moveTo(9f, 7f)
            lineTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(15f, 7f)
        }
    }

    val Tag: ImageVector by lazy {
        stroke("Tag") {
            moveTo(3f, 12f)
            lineTo(3f, 4f)
            lineTo(11f, 4f)
            lineTo(20f, 13f)
            lineTo(12f, 21f)
            close()
            moveTo(7.5f, 8.5f)
            lineTo(7.51f, 8.5f)
        }
    }

    val Flag: ImageVector by lazy {
        stroke("Flag") {
            moveTo(5f, 21f)
            lineTo(5f, 4f)
            moveTo(5f, 4f)
            curveTo(9f, 1.5f, 12f, 6.5f, 19f, 4f)
            lineTo(19f, 13f)
            curveTo(12f, 15.5f, 9f, 10.5f, 5f, 13f)
        }
    }

    val Info: ImageVector by lazy {
        stroke("Info") {
            moveTo(12f, 3f)
            curveTo(17f, 3f, 21f, 7f, 21f, 12f)
            curveTo(21f, 17f, 17f, 21f, 12f, 21f)
            curveTo(7f, 21f, 3f, 17f, 3f, 12f)
            curveTo(3f, 7f, 7f, 3f, 12f, 3f)
            close()
            moveTo(12f, 11f)
            lineTo(12f, 16f)
            moveTo(12f, 8f)
            lineTo(12.01f, 8f)
        }
    }

    val Import: ImageVector by lazy {
        stroke("Import") {
            moveTo(12f, 4f)
            lineTo(12f, 16f)
            moveTo(7f, 11f)
            lineTo(12f, 16f)
            lineTo(17f, 11f)
            moveTo(4f, 20f)
            lineTo(20f, 20f)
        }
    }

    val Export: ImageVector by lazy {
        stroke("Export") {
            moveTo(12f, 16f)
            lineTo(12f, 4f)
            moveTo(7f, 9f)
            lineTo(12f, 4f)
            lineTo(17f, 9f)
            moveTo(4f, 20f)
            lineTo(20f, 20f)
        }
    }

    val Bulb: ImageVector by lazy {
        stroke("Bulb") {
            moveTo(9f, 18f)
            lineTo(15f, 18f)
            moveTo(10f, 21f)
            lineTo(14f, 21f)
            moveTo(12f, 3f)
            arcTo(6f, 6f, 0f, false, false, 8.5f, 13.9f)
            curveTo(9.1f, 14.4f, 9.5f, 15.1f, 9.5f, 15.9f)
            lineTo(9.5f, 16f)
            lineTo(14.5f, 16f)
            lineTo(14.5f, 15.9f)
            curveTo(14.5f, 15.1f, 14.9f, 14.4f, 15.5f, 13.9f)
            arcTo(6f, 6f, 0f, false, false, 12f, 3f)
            close()
        }
    }

    val Target: ImageVector by lazy {
        stroke("Target") {
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, true, true, 3f, 12f)
            arcTo(9f, 9f, 0f, true, true, 21f, 12f)
            moveTo(17f, 12f)
            arcTo(5f, 5f, 0f, true, true, 7f, 12f)
            arcTo(5f, 5f, 0f, true, true, 17f, 12f)
            moveTo(13f, 12f)
            arcTo(1f, 1f, 0f, true, true, 11f, 12f)
            arcTo(1f, 1f, 0f, true, true, 13f, 12f)
        }
    }

    val Chevron: ImageVector by lazy {
        stroke("Chevron") {
            moveTo(9f, 6f)
            lineTo(15f, 12f)
            lineTo(9f, 18f)
        }
    }

    val More: ImageVector by lazy {
        stroke("More") {
            moveTo(12f, 5f)
            lineTo(12.01f, 5f)
            moveTo(12f, 12f)
            lineTo(12.01f, 12f)
            moveTo(12f, 19f)
            lineTo(12.01f, 19f)
        }
    }

    val Speaker: ImageVector by lazy {
        stroke("Speaker") {
            moveTo(4f, 9f)
            lineTo(4f, 15f)
            lineTo(8f, 15f)
            lineTo(13f, 19f)
            lineTo(13f, 5f)
            lineTo(8f, 9f)
            close()
            moveTo(16.5f, 8.5f)
            arcTo(5f, 5f, 0f, false, true, 16.5f, 15.5f)
        }
    }

    val Mic: ImageVector by lazy {
        stroke("Mic") {
            moveTo(12f, 3f)
            arcTo(3f, 3f, 0f, false, true, 15f, 6f)
            lineTo(15f, 11f)
            arcTo(3f, 3f, 0f, false, true, 9f, 11f)
            lineTo(9f, 6f)
            arcTo(3f, 3f, 0f, false, true, 12f, 3f)
            close()
            moveTo(5.5f, 10.5f)
            arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 10.5f)
            moveTo(12f, 17f)
            lineTo(12f, 21f)
        }
    }

    val Copy: ImageVector by lazy {
        stroke("Copy") {
            moveTo(9f, 9f)
            lineTo(20f, 9f)
            lineTo(20f, 20f)
            lineTo(9f, 20f)
            close()
            moveTo(5f, 15f)
            lineTo(4f, 15f)
            lineTo(4f, 4f)
            lineTo(15f, 4f)
            lineTo(15f, 5f)
        }
    }
}
