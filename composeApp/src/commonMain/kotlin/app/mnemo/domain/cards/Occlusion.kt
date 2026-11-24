package app.mnemo.domain.cards

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Image occlusion: boxes drawn over a picture, each box (or group of boxes) one card.
 * Coordinates are fractions of the image size (0–1), so they don't depend on the screen.
 */
@Serializable
data class Mask(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    /** Boxes with the same group are hidden and asked together (one card). */
    val group: Int,
)

@Serializable
data class Occlusions(
    /** "all": every box is hidden and one is asked; "one": only the asked box is hidden. */
    val mode: String = MODE_ALL,
    val masks: List<Mask> = emptyList(),
) {
    val hideAll: Boolean get() = mode != MODE_ONE

    /** Group numbers in card order: card N asks `groups[N]`. */
    val groups: List<Int> get() = masks.map { it.group }.distinct().sorted()

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val MODE_ALL = "all"
        const val MODE_ONE = "one"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String?): Occlusions =
            raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: Occlusions()

        private val ankiShape = Regex("""\{\{c(\d+)::image-occlusion:(rect|ellipse|polygon|text)((?::[a-z]+=[^:}]*)*)\}\}""")

        /**
         * Reads Anki's image-occlusion field (`{{c1::image-occlusion:rect:left=.1:top=.2:width=.3:height=.1:oi=1}}`).
         * Ellipses and polygons become their bounding box. Older notes store pixels: [imageSize]
         * (width, height) turns those into fractions.
         */
        fun fromAnki(
            field: String,
            imageSize: Pair<Int, Int>? = null,
        ): Occlusions {
            // Anki's oi=1 ("occlude inactive") is hide all, guess one; without it only the asked box is hidden.
            var hideAll = false
            val masks =
                ankiShape
                    .findAll(field)
                    .mapNotNull { m ->
                        val props =
                            m.groupValues[3]
                                .split(':')
                                .filter { '=' in it }
                                .associate { it.substringBefore('=') to it.substringAfter('=') }
                        if (props["oi"] == "1") hideAll = true
                        var box: List<Double> =
                            when (m.groupValues[2]) {
                                "polygon" -> {
                                    val pts =
                                        props["points"]
                                            .orEmpty()
                                            .split(' ')
                                            .mapNotNull { p -> p.split(',').takeIf { it.size == 2 }?.map { it.toDoubleOrNull() ?: 0.0 } }
                                    if (pts.isEmpty()) return@mapNotNull null
                                    val xs = pts.map { it[0] }
                                    val ys = pts.map { it[1] }
                                    listOf(xs.min(), ys.min(), xs.max() - xs.min(), ys.max() - ys.min())
                                }
                                "ellipse" -> {
                                    val l = props["left"]?.toDoubleOrNull() ?: return@mapNotNull null
                                    val t = props["top"]?.toDoubleOrNull() ?: return@mapNotNull null
                                    val rx = props["rx"]?.toDoubleOrNull() ?: return@mapNotNull null
                                    val ry = props["ry"]?.toDoubleOrNull() ?: return@mapNotNull null
                                    listOf(l, t, rx * 2, ry * 2)
                                }
                                else ->
                                    listOf(
                                        props["left"]?.toDoubleOrNull() ?: return@mapNotNull null,
                                        props["top"]?.toDoubleOrNull() ?: return@mapNotNull null,
                                        props["width"]?.toDoubleOrNull() ?: 0.05,
                                        props["height"]?.toDoubleOrNull() ?: 0.05,
                                    )
                            }
                        if (box.any { it > 1.5 } && imageSize != null) {
                            box =
                                listOf(
                                    box[0] / imageSize.first,
                                    box[1] / imageSize.second,
                                    box[2] / imageSize.first,
                                    box[3] / imageSize.second,
                                )
                        }
                        Mask(box[0], box[1], box[2], box[3], m.groupValues[1].toInt())
                    }.toList()
            return Occlusions(if (hideAll) MODE_ALL else MODE_ONE, masks)
        }

        /** Anki's field text for these boxes (rectangles), for `.apkg` export. */
        fun toAnki(o: Occlusions): String {
            fun f(d: Double) = ((d.coerceIn(0.0, 1.0) * 10_000).roundToInt() / 10_000.0).toString()
            val oi = if (o.hideAll) "1" else "0"
            return o.masks.joinToString("") { m ->
                "{{c${m.group}::image-occlusion:rect:left=${f(m.x)}:top=${f(m.y)}:width=${f(m.w)}:height=${f(m.h)}:oi=$oi}}"
            }
        }
    }
}

/** What an occlusion card shows: the image, its boxes, and which group it asks. */
data class OcclusionView(
    val image: String,
    val occlusions: Occlusions,
    val group: Int,
)
