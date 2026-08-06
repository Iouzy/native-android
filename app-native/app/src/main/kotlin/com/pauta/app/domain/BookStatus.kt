package com.pauta.app.domain

import com.pauta.app.i18n.tr

/**
 * native-only (L3): the five states a book can be in, and the shelf section
 * that shows each one.
 *
 * `BookEntity.status` has always admitted five values while the shelf rendered
 * four of them, so a book that reached `paused` — through an L2 import, or any
 * later feature — appeared nowhere at all. The invariant this file installs is
 * that **every status maps to exactly one section**; `BookStatusTest` asserts
 * the cover is total, so the next status added fails a test instead of
 * vanishing a book. // PT: os cinco estados e a secção da estante que mostra
 * cada um — nenhum estado pode ficar sem prateleira.
 */
object BookStatus {
    const val TBR = "tbr"
    const val READING = "reading"
    const val PAUSED = "paused"
    const val DONE = "done"
    const val DNF = "dnf"

    /** Every status the entity admits, in the order the shelf lays them out. */
    val ALL = listOf(READING, PAUSED, TBR, DONE, DNF)

    /** The sections of `BookShelfScreen`, top to bottom. `Finished` carries both
     *  `done` and `dnf` — you read it or you put it down, and either way it is
     *  behind you. // PT: as secções da estante; "lidos" junta done e dnf. */
    enum class Shelf { ReadingNow, Paused, UpNext, Finished }

    /** Which section shows a book in [status]; null when the value isn't one of
     *  ours (an import coerces those away, so this is the belt to that brace). */
    fun shelfOf(status: String): Shelf? = when (status) {
        READING -> Shelf.ReadingNow
        PAUSED -> Shelf.Paused
        TBR -> Shelf.UpNext
        DONE, DNF -> Shelf.Finished
        else -> null
    }

    /**
     * L8 · the one word a search result uses to say which shelf its book is on.
     * Kept here beside the statuses themselves so a sixth status cannot be added
     * without someone seeing that it needs a name. // PT: a palavra que diz a
     * prateleira, ao pé dos próprios estados.
     */
    fun label(status: String): String = when (status) {
        READING -> tr("A ler agora")
        PAUSED -> tr("Em pausa")
        TBR -> tr("A seguir")
        DONE -> tr("Lidos")
        DNF -> tr("Abandonado")
        else -> tr("A seguir")
    }

    /** Sanitises a status arriving from outside the app (an L2 library file, a
     *  hand-edited one): anything unknown becomes [TBR] rather than a book on no
     *  shelf. // PT: um estado desconhecido cai em "tbr". */
    fun sanitize(status: String?): String = status?.takeIf { it in ALL } ?: TBR
}
