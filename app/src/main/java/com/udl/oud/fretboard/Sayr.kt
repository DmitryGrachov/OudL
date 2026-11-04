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
        "Махур" to Sayr(
            start = 0,
            path = listOf(
                "начало в Расте на До, как у Раста",
                "подъём к гаммазу Соль",
                "наверху Аджам на Соль: чистое Си, «мажорный» блеск",
                "часто развитие у верхнего До",
                "каданс на До через Ми½♭ и Ре",
            ),
            modulations = listOf(
                Modulation("Раст", 0),
                Modulation("Баяти", 4), // on D
                Modulation("Аджам", 10), // on F
            ),
        ),
        "Сузнак" to Sayr(
            start = 0,
            path = listOf(
                "начало в Расте на До",
                "подъём к гаммазу Соль",
                "наверху Хиджаз на Соль: Ля♭ — Си, полтора тона",
                "спуск через Хиджаз на Соль в нижний Раст",
                "каданс на До через Ми½♭",
            ),
            modulations = listOf(
                Modulation("Раст", 0),
                Modulation("Баяти", 4), // on D
                Modulation("Хиджаз", 14), // on G
            ),
        ),
        "Найруз" to Sayr(
            start = 0,
            path = listOf(
                "начало в Расте на До",
                "гаммаз Соль",
                "наверху Баяти на Соль: Ля½♭ над гаммазом",
                "каданс на До через Ми½♭",
            ),
            modulations = listOf(
                Modulation("Раст", 0),
                Modulation("Баяти", 14), // on G
            ),
        ),
        "Баяти шури" to Sayr(
            start = 0,
            path = listOf(
                "начало в Баяти на Ре, игра с Ми½♭",
                "подъём к гаммазу Соль",
                "наверху Хиджаз на Соль: Ля♭ — Си",
                "спуск через Хиджаз на Соль",
                "каданс на Ре через Ми½♭",
            ),
            modulations = listOf(
                Modulation("Баяти", 0),
                Modulation("Саба", 0),
                Modulation("Хиджаз", 10), // on G
            ),
        ),
        "Хусейни" to Sayr(
            start = 4,
            path = listOf(
                "развитие вокруг Ля — ступени Хусейни",
                "наверху Баяти на Ля: Си½♭",
                "спуск к Соль и Фа",
                "каданс на Ре, как в Баяти",
            ),
            modulations = listOf(
                Modulation("Баяти", 0),
                Modulation("Раст", -4), // on C below
                Modulation("Нахаванд", 10), // on G
            ),
        ),
        "Хиджазкар" to Sayr(
            start = 0,
            path = listOf(
                "начало в Хиджазе на До: Ре♭ — Ми, полтора тона",
                "гаммаз Соль",
                "наверху второй Хиджаз на Соль: Ля♭ — Си",
                "торжественный спуск через оба Хиджаза",
                "каданс на До через Ре♭, снизу вводный Си",
            ),
            modulations = listOf(
                Modulation("Хиджаз", 14), // on G
                Modulation("Нахаванд", 0),
            ),
        ),
        "Занджаран" to Sayr(
            start = 0,
            path = listOf(
                "начало в Хиджазе на До",
                "подъём к гаммазу Соль",
                "наверху Аджам на Соль: светлый верх над острым низом",
                "каданс на До через Ре♭",
            ),
            modulations = listOf(
                Modulation("Хиджазкар", 0),
                Modulation("Нахаванд", 14), // on G
            ),
        ),
        "Саба замзам" to Sayr(
            start = 2,
            path = listOf(
                "движение вокруг третьей ступени Фа",
                "Ми♭ — полутон сразу над тоникой, Соль♭ — тесная ступень",
                "верхняя часть — Хиджаз на Фа",
                "окончание вниз на Ре",
            ),
            modulations = listOf(
                Modulation("Саба", 0),
                Modulation("Курд", 0),
                Modulation("Хиджаз", 6), // on F
            ),
        ),
        "Хиджазкар-курд" to Sayr(
            start = 0,
            path = listOf(
                "начало в Курде на До: Ре♭ сразу над тоникой, тёмный цвет",
                "гаммаз Соль",
                "наверху Хиджаз на Соль: Ля♭ — Си",
                "каданс на До через Ре♭, снизу вводный Си",
            ),
            modulations = listOf(
                Modulation("Курд", 0),
                Modulation("Нахаванд", 0),
                Modulation("Хиджаз", 14), // on G
            ),
        ),
        "Хузам" to Sayr(
            start = 0,
            path = listOf(
                "тоника Ми½♭ — на ней задерживаются",
                "подъём к Соль",
                "наверху Хиджаз на Соль: Ля♭ — Си, острый цвет Хузама",
                "каданс сверху через Фа на Ми½♭",
            ),
            modulations = listOf(
                Modulation("Сикях", 0),
                Modulation("Раст", -7), // on C below
                Modulation("Хиджаз", 7), // on G
            ),
        ),
        "Ирак" to Sayr(
            start = 0,
            path = listOf(
                "тоника Си½♭ — низкий Сикях, задержка на ней",
                "подъём к Ре: Баяти на Ре",
                "развитие в Расте на До",
                "каданс сверху через До на Си½♭",
            ),
            modulations = listOf(
                Modulation("Баяти", 7), // on D
                Modulation("Раст", 3), // on C
                Modulation("Сикях", 10), // on E½♭
            ),
        ),
        "Никриз" to Sayr(
            start = 0,
            path = listOf(
                "начало в Никризе на До: Ми♭ — Фа♯, полтора тона",
                "гаммаз Соль",
                "наверху Нахаванд на Соль",
                "на спуске Хиджаз на Ре слышен внутри Никриза",
                "каданс на До через Ми♭ и Ре",
            ),
            modulations = listOf(
                Modulation("Нахаванд", 0),
                Modulation("Хиджаз", 4), // on D
                Modulation("Нава асар", 0),
            ),
        ),
        "Нава асар" to Sayr(
            start = 0,
            path = listOf(
                "начало в Никризе на До",
                "гаммаз Соль",
                "наверху Хиджаз на Соль: Ля♭ — Си, два «полуторатона» в октаве",
                "каданс на До, снизу вводный Си",
            ),
            modulations = listOf(
                Modulation("Никриз", 0),
                Modulation("Хиджаз", 4), // on D
            ),
        ),
    )

    fun of(maqam: Maqam): Sayr? = byName[maqam.name]

    internal val all: Map<String, Sayr> get() = byName
}
