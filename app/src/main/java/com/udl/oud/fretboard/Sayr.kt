package com.udl.oud.fretboard

/** A common move to another maqam: [maqam] built on the note [offset] quarter tones from the tonic. */
data class Modulation(val maqam: String, val offset: Int)

/**
 * Sayr: how a maqam usually unfolds. [start] is the degree development tends to centre on at
 * first (0 = tonic), [path] the usual course in short steps, [modulations] the usual moves.
 */
data class Sayr(val start: Int, val path: List<String>, val modulations: List<Modulation>)

/**
 * Sayr of the core maqamat in their usual form. Schools differ (Egyptian, Syrian, Iraqi): this is
 * a shared minimum — pillars and the most common moves — not a full account.
 */
object Sayrs {
    private val byName = mapOf(
        "Раст" to Sayr(
            start = 0,
            path = listOf(
                "начало на тонике, в нижнем Расте",
                "подъём к гаммазу Соль",
                "развитие в верхнем Расте",
                "спуск через Си♭ (Нахаванд на Соль)",
                "каданс на тонику через Ми½♭ и Ре",
            ),
            modulations = listOf(
                Modulation("Баяти", 4), // on D
                Modulation("Сикях", 7), // on E½♭
                Modulation("Нахаванд", 14), // on G
                Modulation("Хиджаз", 14), // on G
            ),
        ),
        "Баяти" to Sayr(
            start = 0,
            path = listOf(
                "начало у тоники, игра с Ми½♭",
                "опора на гаммаз Соль",
                "развитие в Нахаванде на Соль",
                "каданс на Ре через Ми½♭",
            ),
            modulations = listOf(
                Modulation("Раст", -4), // on C below
                Modulation("Саба", 0),
                Modulation("Хиджаз", 0),
                Modulation("Нахаванд", 10), // on G
            ),
        ),
        "Хиджаз" to Sayr(
            start = 0,
            path = listOf(
                "начало в нижнем Хиджазе: Ми♭ — Фа♯, полтора тона",
                "подъём к гаммазу Соль",
                "наверху Раст на Соль, на спуске Нахаванд (Си♭)",
                "каданс на Ре через Ми♭",
            ),
            modulations = listOf(
                Modulation("Раст", 10), // on G
                Modulation("Нахаванд", 10), // on G
                Modulation("Баяти", 0),
            ),
        ),
        "Саба" to Sayr(
            start = 2,
            path = listOf(
                "движение вокруг третьей ступени Фа",
                "Соль♭ — тесная, «щемящая» ступень",
                "верхняя часть — Хиджаз на Фа",
                "окончание вниз на Ре",
            ),
            modulations = listOf(
                Modulation("Баяти", 0),
                Modulation("Хиджаз", 6), // on F
            ),
        ),
        "Нахаванд" to Sayr(
            start = 0,
            path = listOf(
                "нижний Нахаванд До–Соль, минорный характер",
                "гаммаз Соль",
                "наверху Хиджаз (Ля♭ Си), на спуске Курд (Ля♭ Си♭)",
                "каданс на До",
            ),
            modulations = listOf(
                Modulation("Курд", 4), // on D
                Modulation("Хиджаз", 14), // on G
                Modulation("Аджам", 6), // on E♭
            ),
        ),
        "Курд" to Sayr(
            start = 0,
            path = listOf(
                "нижний Курд: полутон сразу над тоникой",
                "гаммаз Соль, наверху Нахаванд",
                "каданс на Ре через Ми♭",
            ),
            modulations = listOf(
                Modulation("Хиджаз", 0),
                Modulation("Аджам", 6), // on F
            ),
        ),
        "Аджам" to Sayr(
            start = 0,
            path = listOf(
                "мажорный характер, нижний Аджам",
                "гаммаз Фа — второй Аджам",
                "каданс на Си♭",
            ),
            modulations = listOf(
                Modulation("Нахаванд", 18), // on G
            ),
        ),
        "Сикях" to Sayr(
            start = 0,
            path = listOf(
                "тоника Ми½♭ — на ней задерживаются",
                "подъём к Соль, Раст на Соль",
                "каданс сверху вниз на Ми½♭",
            ),
            modulations = listOf(
                Modulation("Раст", -7), // on C below
                Modulation("Хузам", 0),
                Modulation("Баяти", -3), // on D below
            ),
        ),
    )

    fun of(maqam: Maqam): Sayr? = byName[maqam.name]

    internal val all: Map<String, Sayr> get() = byName
}
