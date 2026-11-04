package com.udl.oud.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A drum stroke: dum (low, centre of the drum), tak (high, rim), ka (a light tak), or a rest. */
enum class Stroke { Dum, Tak, Ka, Rest }

/** How the library is split: the rhythms met every day, and the rarer and longer ones. */
enum class IqaGroup(val title: String) {
    Main("Основные"),
    Complex("Побочные и сложные"),
}

/**
 * An Arabic rhythmic cycle (iqa'): strokes on an even grid of [stepsPerBeat] steps per beat,
 * written like "D T . T D . T ." (D = dum, T = tak, t = a light tak that may be left out,
 * . = rest). [beatUnit] is what a beat is (♩ a quarter, ♪ an eighth), [bpm] its usual tempo,
 * [grouping] how the beats fall together in an uneven (aqsaq) cycle, 2+2+2+3 and the like.
 */
data class Iqa(
    val name: String,
    val meter: String,
    val stepsPerBeat: Int,
    val pattern: String,
    val latin: String = name,
    val group: IqaGroup = IqaGroup.Main,
    val bpm: Int = 90,
    val beatUnit: String = "♩",
    val grouping: List<Int>? = null,
    val about: String = "",
) {
    val strokes: List<Stroke> = pattern.split(" ").filter { it.isNotEmpty() }.map {
        when (it) {
            "D" -> Stroke.Dum
            "T" -> Stroke.Tak
            "t" -> Stroke.Ka
            "." -> Stroke.Rest
            else -> error("$name: unknown stroke '$it'")
        }
    }

    init {
        require(strokes.size % stepsPerBeat == 0) { "$name: the cycle is not a whole number of beats" }
        require(grouping == null || grouping.sum() == beats) { "$name: grouping doesn't add up to the beats" }
    }

    val beats: Int get() = strokes.size / stepsPerBeat

    /** Samples per step at [bpm] beats per minute. */
    fun stepSamples(bpm: Int, sampleRate: Int): Int = (sampleRate * 60.0 / bpm / stepsPerBeat).roundToInt()

    /** Milliseconds per step at [bpm]. */
    fun stepMs(bpm: Int): Double = 60_000.0 / bpm / stepsPerBeat

    /** Steps on which a group starts: every beat in an even cycle, the 2s and 3s in an uneven one. */
    val groupStarts: Set<Int>
        get() = grouping?.runningFold(0) { a, g -> a + g }?.dropLast(1)?.map { it * stepsPerBeat }?.toSet()
            ?: (0 until beats).map { it * stepsPerBeat }.toSet()

    /** The cycle spoken: "ДУМ тек – тек ДУМ – тек –". */
    val spoken: String
        get() = strokes.joinToString(" ") {
            when (it) {
                Stroke.Dum -> "ДУМ"
                Stroke.Tak -> "тек"
                Stroke.Ka -> "(ка)"
                Stroke.Rest -> "–"
            }
        }
}

/**
 * The library of iqa'at. Frameworks follow M. Frishkopf's "Arab durub charts" (2009); Karachi and
 * Curcuna follow the Middle Eastern Rhythms FAQ. A drummer fills the rests and varies the
 * strokes; these are the skeletons to count and to play against.
 */
object Iqaat {
    val all = listOf(
        // --- The everyday ones ---
        Iqa(
            "Малфуф", "2/4", 4, "D . . T . . T .", "Malfuf", bpm = 100,
            about = "«Завёрнутый»: быстрый и бегущий, дум и два тека. Быстрые песни, дабка, переходы и проходки.",
        ),
        Iqa(
            "Аюб", "2/4", 4, "D . . t D . T .", "Ayoub", bpm = 100,
            about = "Качающийся, гипнотичный — «дум-ка-дум-тек». Зикр и суфийская музыка, египетский зар, трансовые места в танце.",
        ),
        Iqa(
            "Фокс", "2/4", 2, "D . T t", "Fox", bpm = 120,
            about = "Простой «дум-тек», взятый из фокстрота. Главный ритм лонги; играют очень быстро и почти без украшений, легко переходит в малфуф.",
        ),
        Iqa(
            "Карачи", "2/4", 4, "T . . t T . D .", "Karachi", bpm = 90,
            about = "Начинается с тека, дум — в конце: фраза тянет вперёд. Спокойнее малфуфа, часто чередуется с ним; египетская эстрада.",
        ),
        Iqa(
            "Максум", "4/4", 2, "D T . T D . T .", "Maqsum", bpm = 100,
            about = "Самый ходовой ритм: песни, танец, инструментальные пьесы. Дум–тек–тек–дум–тек; он же вахда ва нусс.",
        ),
        Iqa(
            "Вахда", "4/4", 2, "D . . t . . t .", "Wahda", bpm = 70,
            about = "Один дум на такт, остальное — воздух: главный ритм вокальных фраз тараба, певец сам ведёт темп. Теки лёгкие, их можно не играть.",
        ),
        Iqa(
            "Балади", "4/4", 2, "D D . T D . T .", "Baladi", bpm = 100,
            about = "«Деревенский», он же масмуди сагир: два дума в начале — тяжелее максума. Египетский танец балади, народные песни.",
        ),
        Iqa(
            "Саиди", "4/4", 2, "D T . D D . T .", "Saidi", bpm = 100,
            about = "Из Верхнего Египта, танец с палкой (тахтиб): два дума подряд в середине такта. Первый дум иногда пропускают.",
        ),
        Iqa(
            "Масмуди кабир", "8/4", 2, "D . D . . . T . D . . . T . T .", "Masmoudi Kabir", bpm = 90,
            about = "«Большой масмуди»: длинный, тяжёлый, величавый цикл на два такта. Любимый ритм тараба XX века — Абдель Ваххаб, Умм Кульсум.",
        ),
        Iqa(
            "Чифтетелли", "8/4", 2, "D . . T . . T . D . D . T . . .", "Chiftetelli", bpm = 70,
            about = "Турецкий по происхождению, медленный и тягучий, на два такта. Под таксимы и танцевальные импровизации, под сочинённые мелодии — редко.",
        ),
        // --- Rarer, uneven and long ---
        Iqa(
            "Аксак", "9/8", 1, "D . T . D . T . T", "Aksak", IqaGroup.Complex, bpm = 240, beatUnit = "♪",
            grouping = listOf(2, 2, 2, 3),
            about = "«Хромой»: девять восьмых как 2+2+2+3, последняя доля длиннее. Турецкая и балканская музыка, сазы и лонги.",
        ),
        Iqa(
            "Самаи дарий", "3/4", 4, "D . t t T . T . T . . .", "Samai Darij", IqaGroup.Complex, bpm = 90,
            about = "Трёхдольный: дум и три тека. Многие мувашшахи и песни; лёгкие теки на второй восьмой можно не играть.",
        ),
        Iqa(
            "Куркуна", "10/8", 1, "D . . T . D . T . .", "Curcuna", IqaGroup.Complex, bpm = 220, beatUnit = "♪",
            grouping = listOf(3, 2, 2, 3),
            about = "Curcuna (у нас — «куркузина»): десять восьмых как 3+2+2+3, хромающий и пружинистый. Турецкие, курдские и армянские песни.",
        ),
        Iqa(
            "Давр хинди", "7/8", 1, "D T T D . T .", "Dawr Hindi", IqaGroup.Complex, bpm = 200, beatUnit = "♪",
            grouping = listOf(3, 2, 2),
            about = "«Индийский круг»: семь восьмых как 3+2+2. Мувашшахи; если растянуть вдвое, выйдет навахт.",
        ),
        Iqa(
            "Самаи сакиль", "10/8", 1, "D . . T . D D T . .", "Samai Thaqil", IqaGroup.Complex, bpm = 160, beatUnit = "♪",
            grouping = listOf(3, 2, 2, 3),
            about = "«Тяжёлый самаи»: десять восьмых, два дума подряд во второй половине. Ритм инструментальной формы самаи (кроме последней части) и мувашшахов.",
        ),
        Iqa(
            "Мухаджар", "14/4", 2, "D . D . D . . . T . . . D . . T . . T . T . . . T . T .", "Muhajjar", IqaGroup.Complex,
            bpm = 90,
            about = "Длинный цикл в 14 четвертей: три дума в начале, потом россыпь теков. Мувашшахи и старые инструментальные пьесы; учи его кусками.",
        ),
    )
}

/** Renders an iqa' as a loop: each stroke is placed on its step, tails wrap around. */
object Drum {
    fun render(iqa: Iqa, bpm: Int, sampleRate: Int = LoopTrack.SAMPLE_RATE): ShortArray =
        render(iqa, bpm, cycles = 1, sampleRate = sampleRate)

    /**
     * [cycles] cycles in one loop; the drum plays only in the cycles [drumIn] lets through, and
     * with [clicks] a soft click marks every beat (every group in an uneven cycle), the first
     * one higher — to keep time by when the drum is silent.
     */
    fun render(
        iqa: Iqa,
        bpm: Int,
        cycles: Int,
        drumIn: (Int) -> Boolean = { true },
        clicks: Boolean = false,
        sampleRate: Int = LoopTrack.SAMPLE_RATE,
    ): ShortArray = mix(iqa, bpm, cycles, drumIn, clicks, sampleRate).toPcm()

    /** The same as [render], unmixed to PCM yet: for laying a melody over the drum. */
    fun mix(
        iqa: Iqa,
        bpm: Int,
        cycles: Int,
        drumIn: (Int) -> Boolean = { true },
        clicks: Boolean = false,
        sampleRate: Int = LoopTrack.SAMPLE_RATE,
    ): FloatArray {
        val step = iqa.stepSamples(bpm, sampleRate)
        val n = iqa.strokes.size
        val mix = FloatArray(step * n * cycles)
        val dum = dum(sampleRate)
        val tak = tak(sampleRate)
        val ka = FloatArray(tak.size) { tak[it] * KA_GAIN }
        val high = click(sampleRate, 1760.0)
        val low = click(sampleRate, 1320.0)
        fun place(sound: FloatArray, at: Int) {
            // A sound longer than what is left of the loop rings on into its start.
            for (j in sound.indices) mix[(at + j) % mix.size] += sound[j]
        }
        for (c in 0 until cycles) {
            if (drumIn(c)) {
                iqa.strokes.forEachIndexed { i, stroke ->
                    val sound = when (stroke) {
                        Stroke.Dum -> dum
                        Stroke.Tak -> tak
                        Stroke.Ka -> ka
                        Stroke.Rest -> return@forEachIndexed
                    }
                    place(sound, (c * n + i) * step)
                }
            }
            if (clicks) iqa.groupStarts.forEach { i -> place(if (i == 0) high else low, (c * n + i) * step) }
        }
        return mix
    }

    /** A short soft woodblock-like tick. */
    private fun click(sampleRate: Int, hz: Double): FloatArray = FloatArray((0.03 * sampleRate).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        (0.25 * sin(2 * PI * hz * t) * exp(-t * 160) * attack(t)).toFloat()
    }

    private const val KA_GAIN = 0.45f

    /** Low thump whose pitch drops from 130 to 70 Hz as it decays, like a darbuka's centre. */
    private fun dum(sampleRate: Int): FloatArray = FloatArray((0.35 * sampleRate).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        // Phase is the integral of the gliding frequency 70 + 60·e^(−30t).
        val phase = 2 * PI * (70 * t + 60 * (1 - exp(-t * 30)) / 30)
        (0.9 * sin(phase) * exp(-t * 9) * attack(t)).toFloat()
    }

    /** Short bright crack from the rim: a ringing high partial over a burst of noise. */
    private fun tak(sampleRate: Int): FloatArray {
        val random = Random(1)
        return FloatArray((0.12 * sampleRate).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val ring = 0.5 * sin(2 * PI * 1800 * t) * exp(-t * 45)
            val noise = 0.45 * (random.nextDouble() * 2 - 1) * exp(-t * 70)
            ((ring + noise) * attack(t)).toFloat()
        }
    }

    private fun attack(t: Double) = (t / 0.002).coerceAtMost(1.0)
}
