package com.udl.oud.taqsim

import com.udl.oud.fretboard.Ajnas
import com.udl.oud.fretboard.Jins

/** What a motif is good for: starting a phrase, carrying it on, or closing it on the jins' root. */
enum class MotifUse { Open, Develop, Close }

/**
 * A short melodic cell in degrees counted from the jins' root (0), as players learn them: a
 * turn round a pillar, a run down to it, an arch. Degrees past the jins carry on into the scale.
 */
data class Motif(val name: String, val degrees: List<Int>, val uses: Set<MotifUse>, val weight: Double = 1.0) {
    init {
        require(degrees.size in 2..8) { "$name: a motif is 2–8 notes" }
        require(degrees.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) <= 3 }) { "$name: leap too wide" }
    }
}

/**
 * How a jins is played: its [characteristic] degrees (the colour that must be heard), the
 * degrees a phrase may stop on, how its root is usually reached ([fromAbove]: share of
 * approaches from the note above), its own [motifs], and a line about it for the learner.
 */
data class JinsGrammar(
    val jins: Jins,
    val characteristic: Set<Int>,
    val rests: Set<Int>,
    val fromAbove: Double,
    val motifs: List<Motif>,
    val about: String,
) {
    /** Motifs of this jins and the shared ones, for [use]. */
    fun motifsFor(use: MotifUse): List<Motif> = (motifs + JinsGrammars.common).filter { use in it.uses }
}

/**
 * Grammars of the ten ajnas the maqamat are built from. The shapes are study material drawn
 * from common teaching practice (turns round the pillars, runs into the root, the jins' own
 * colour note), not quotations from recordings.
 */
object JinsGrammars {
    private val O = setOf(MotifUse.Open)
    private val D = setOf(MotifUse.Develop)
    private val C = setOf(MotifUse.Close)
    private val OD = setOf(MotifUse.Open, MotifUse.Develop)
    private val DC = setOf(MotifUse.Develop, MotifUse.Close)
    private val ALL = setOf(MotifUse.Open, MotifUse.Develop, MotifUse.Close)

    /** Shapes that work in any jins. */
    val common = listOf(
        Motif("шаг вверх", listOf(0, 1, 2), OD, 0.8),
        Motif("спуск к основе", listOf(2, 1, 0), DC, 1.0),
        Motif("длинный спуск", listOf(3, 2, 1, 0), DC, 0.9),
        Motif("оборот вокруг основы", listOf(0, 1, 0, -1, 0), ALL, 1.0),
        Motif("опевание сверху", listOf(1, 0, -1, 0), DC, 0.9),
        Motif("арка", listOf(0, 1, 2, 3, 2, 1), D, 0.8),
        Motif("скачок и заполнение", listOf(0, 2, 1, 0), DC, 0.6),
        Motif("волна", listOf(1, 2, 1, 0, 1), D, 0.6),
        Motif("подход снизу", listOf(-1, 0, 1, 0), O, 0.7),
        Motif("терции вниз", listOf(3, 1, 2, 0), DC, 0.6),
        Motif("покачивание", listOf(0, 1, 0, 1, 0), D, 0.5),
        Motif("задержка и спуск", listOf(2, 2, 1, 0), DC, 0.7),
        Motif("подъём с возвратом", listOf(0, 1, 2, 1, 2, 3), OD, 0.7),
        Motif("опевание с двух сторон", listOf(1, -1, 0), C, 0.6),
        Motif("широкий спуск", listOf(4, 3, 2, 1, 0), DC, 0.5),
    )

    private val byJins: Map<Jins, JinsGrammar> = listOf(
        JinsGrammar(
            Ajnas.Rast, characteristic = setOf(2), rests = setOf(0, 2, 4), fromAbove = 0.6,
            motifs = listOf(
                Motif("Ми½♭ к основе", listOf(2, 1, 0), C, 1.4),
                Motif("распев от тоники", listOf(0, 1, 2, 1, 0), ALL, 1.0),
                Motif("к Фа через Ми½♭", listOf(1, 2, 3, 2), D, 0.9),
                Motif("задержка на Ми½♭", listOf(4, 3, 2, 2, 1, 0), C, 1.0),
                Motif("оборот вокруг второй", listOf(1, 2, 1, 0, 1), D, 0.8),
                Motif("через Ми½♭ к квинте", listOf(0, 2, 3, 4), O, 0.8),
                Motif("снизу от шестой", listOf(-2, -1, 0, 1), O, 0.7),
                Motif("распев до квинты", listOf(0, 1, 2, 3, 4, 3, 2), D, 1.0),
                Motif("через нижнюю Си½♭", listOf(1, 0, -1, 0), C, 1.2),
                Motif("оборот на третьей", listOf(2, 3, 2, 1, 2), D, 1.0),
                Motif("сверху от квинты", listOf(4, 3, 2, 1, 0), C, 0.9),
            ),
            about = "Раст: светлый, «мажорный» с Ми½♭; опоры — тоника, третья и квинта.",
        ),
        JinsGrammar(
            Ajnas.Bayati, characteristic = setOf(1), rests = setOf(0, 3), fromAbove = 0.75,
            motifs = listOf(
                Motif("Ми½♭ над основой", listOf(1, 0, -1, 0), C, 1.5),
                Motif("кварта и обратно", listOf(0, 3, 2, 1, 0), C, 0.9),
                Motif("опора на третьей", listOf(2, 1, 2, 3, 2), D, 1.0),
                Motif("снизу через седьмую", listOf(-1, 0, 1, 0), O, 1.0),
                Motif("подъём к квинте", listOf(0, 1, 2, 3, 4, 3), D, 0.8),
                Motif("Ми½♭ дважды", listOf(2, 1, 0, 1, 0), C, 1.0),
                Motif("задержка на второй", listOf(0, 1, 1, 0), DC, 1.1),
                Motif("подъём к кварте", listOf(0, 1, 2, 3), O, 1.0),
                Motif("спуск от кварты", listOf(3, 2, 1, 1, 0), C, 1.2),
                Motif("качание Ми½♭–Фа", listOf(1, 2, 1, 2, 1, 0), D, 0.9),
            ),
            about = "Баяти: мягкая, чуть грустная; главный цвет — низкая вторая ступень над основой.",
        ),
        JinsGrammar(
            Ajnas.Sikah, characteristic = setOf(0), rests = setOf(0, 2), fromAbove = 0.35,
            motifs = listOf(
                Motif("задержка на основе", listOf(0, 1, 0), ALL, 1.4),
                Motif("снизу с задержкой", listOf(-1, 0, 0, 1, 0), C, 1.1),
                Motif("через третью вниз", listOf(2, 1, 2, 1, 0), C, 1.0),
                Motif("подъём к четвёртой", listOf(0, 1, 2, 3, 2), D, 0.8),
                Motif("из глубины", listOf(-2, -1, 0), O, 0.9),
                Motif("снизу на основу", listOf(-1, 0, 1, 0), C, 1.3),
                Motif("от третьей вниз", listOf(2, 1, 0), C, 1.1),
                Motif("подъём к третьей", listOf(0, 1, 2, 1, 2), D, 1.0),
            ),
            about = "Сикях: основа сама на четверти тона — на ней задерживаются и к ней приходят снизу.",
        ),
        JinsGrammar(
            Ajnas.Hijaz, characteristic = setOf(1, 2), rests = setOf(0, 3), fromAbove = 0.65,
            motifs = listOf(
                Motif("через полтора тона", listOf(1, 2, 1, 0), C, 1.5),
                Motif("подъём через полтора тона", listOf(0, 1, 2, 3), O, 1.2),
                Motif("задержка на третьей", listOf(0, 2, 2, 1, 0), C, 1.0),
                Motif("подъём к квинте", listOf(0, 1, 2, 3, 4), OD, 0.8),
                Motif("кварта и полтора тона", listOf(3, 2, 3, 1, 0), C, 0.9),
                Motif("снизу через седьмую", listOf(-1, 0, 1, 2, 1), D, 0.9),
                Motif("опевание третьей", listOf(2, 3, 2, 1, 2), D, 1.0),
                Motif("спуск от кварты", listOf(3, 2, 1, 0), C, 1.2),
                Motif("качание вокруг второй", listOf(1, 0, 1, 2, 1, 0), D, 0.8),
            ),
            about = "Хиджаз: острый шаг в полтора тона между второй и третьей ступенью.",
        ),
        JinsGrammar(
            Ajnas.Saba, characteristic = setOf(3), rests = setOf(0, 2), fromAbove = 0.7,
            motifs = listOf(
                Motif("вокруг третьей", listOf(2, 3, 2, 1, 2), ALL, 1.5),
                Motif("тесная к третьей", listOf(3, 2, 2, 1, 2), D, 1.1),
                Motif("от пятой вниз", listOf(4, 3, 2, 1, 0), C, 0.7),
                Motif("подъём с задержкой", listOf(0, 1, 2, 2), O, 1.0),
                Motif("снизу к третьей", listOf(-1, 0, 1, 2), O, 0.9),
                Motif("тесная ступень", listOf(1, 2, 3, 2, 1, 0), DC, 1.3),
                Motif("от третьей вниз", listOf(2, 1, 0), C, 1.1),
                Motif("задержка на третьей", listOf(1, 2, 2, 3, 2), D, 1.0),
            ),
            about = "Саба: центр на третьей ступени, над ней тесная пониженная четвёртая — «щемящая».",
        ),
        JinsGrammar(
            Ajnas.SabaZamzam, characteristic = setOf(1, 3), rests = setOf(0, 2), fromAbove = 0.7,
            motifs = listOf(
                Motif("вокруг третьей", listOf(2, 3, 2, 1, 2), ALL, 1.4),
                Motif("тесная к третьей", listOf(3, 2, 2, 1, 2), D, 1.0),
                Motif("подъём с задержкой", listOf(0, 1, 2, 2), O, 1.0),
                Motif("снизу на основу", listOf(-1, 0, 1, 0), C, 0.9),
                Motif("от пятой вниз", listOf(4, 3, 2, 1, 0), C, 0.7),
                Motif("тесная ступень и полутон", listOf(3, 2, 1, 2), D, 0.9),
                Motif("полутон над основой", listOf(1, 0, 1, 0), C, 1.1),
                Motif("спуск к основе", listOf(3, 2, 1, 0), C, 1.1),
            ),
            about = "Саба замзам: та же Саба, но с полутоном над основой вместо ¾ тона.",
        ),
        JinsGrammar(
            Ajnas.Kurd, characteristic = setOf(1), rests = setOf(0, 3), fromAbove = 0.7,
            motifs = listOf(
                Motif("полутон над основой", listOf(1, 0, 1, 0), DC, 1.3),
                Motif("распев от основы", listOf(0, 1, 2, 1, 0), ALL, 1.0),
                Motif("задержка и спуск", listOf(3, 2, 1, 1, 0), C, 1.0),
                Motif("снизу к третьей", listOf(-1, 0, 1, 2), O, 0.9),
                Motif("вокруг четвёртой", listOf(2, 3, 2, 1, 2), D, 0.9),
                Motif("спуск от кварты", listOf(3, 2, 1, 0), C, 1.2),
                Motif("арка до кварты", listOf(0, 1, 2, 3, 2, 1, 0), D, 0.9),
                Motif("задержка на третьей", listOf(2, 1, 2, 1, 0), C, 1.0),
            ),
            about = "Курд: тёмный, с полутоном сразу над основой.",
        ),
        JinsGrammar(
            Ajnas.Nahawand, characteristic = setOf(2), rests = setOf(0, 2, 4), fromAbove = 0.55,
            motifs = listOf(
                Motif("минорный спуск", listOf(4, 3, 2, 1, 0), C, 1.2),
                Motif("через третью к основе", listOf(0, 2, 1, 0), C, 1.0),
                Motif("волна к основе", listOf(3, 2, 1, 2, 1, 0), C, 1.0),
                Motif("вокруг четвёртой", listOf(2, 3, 4, 3, 2), D, 0.9),
                Motif("с вводным и скачком", listOf(-1, 0, 2, 1, 0), C, 0.9),
                Motif("подъём к квинте", listOf(0, 1, 2, 3, 4), O, 1.0),
                Motif("опевание третьей", listOf(2, 1, 2, 3, 2), D, 1.0),
                Motif("с вводным снизу", listOf(1, 0, -1, 0), C, 1.1),
            ),
            about = "Нахаванд: «минор» с пониженной третьей; опоры — тоника и квинта.",
        ),
        JinsGrammar(
            Ajnas.Ajam, characteristic = setOf(2), rests = setOf(0, 2, 3), fromAbove = 0.5,
            motifs = listOf(
                Motif("мажорный спуск", listOf(3, 2, 1, 0), C, 1.1),
                Motif("по трезвучию вверх", listOf(0, 2, 4, 3, 2), D, 0.9),
                Motif("по трезвучию вниз", listOf(4, 2, 0), C, 0.8),
                Motif("распев к основе", listOf(1, 2, 3, 2, 1, 0), C, 1.0),
                Motif("снизу и через третью", listOf(-1, 0, 2, 1), O, 0.8),
                Motif("по трезвучию", listOf(0, 2, 3, 2), D, 0.9),
                Motif("подъём к кварте", listOf(0, 1, 2, 3), O, 1.0),
                Motif("с вводным снизу", listOf(-1, 0, 1, 0), C, 1.0),
            ),
            about = "Аджам: «мажор» с чистыми тонами; допускает скачки по трезвучию.",
        ),
        JinsGrammar(
            Ajnas.Nikriz, characteristic = setOf(2, 3), rests = setOf(0, 4), fromAbove = 0.6,
            motifs = listOf(
                Motif("через полтора тона", listOf(2, 3, 2, 1, 0), C, 1.4),
                Motif("опевание четвёртой", listOf(3, 4, 3, 2, 3), D, 1.1),
                Motif("вокруг третьей", listOf(1, 2, 3, 2, 1), D, 1.0),
                Motif("качание у квинты", listOf(4, 3, 4, 3, 2), D, 0.8),
                Motif("распев до квинты", listOf(0, 1, 2, 3, 4, 3), O, 0.9),
                Motif("спуск через полтора тона", listOf(3, 2, 1, 0), C, 1.1),
                Motif("подъём к квинте", listOf(0, 1, 2, 3, 4), O, 1.0),
                Motif("от квинты вниз", listOf(4, 3, 2, 1, 0), C, 1.0),
            ),
            about = "Никриз: полтора тона между третьей и четвёртой; квинта — опора.",
        ),
    ).associateBy { it.jins }

    fun of(jins: Jins): JinsGrammar = byJins[jins] ?: error("no grammar for ${jins.name}")

    internal val all: Collection<JinsGrammar> get() = byJins.values
}
