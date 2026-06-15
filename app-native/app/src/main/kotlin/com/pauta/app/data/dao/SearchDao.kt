package com.pauta.app.data.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * E1: full-text search over the `search_index` FTS4 virtual table. That table is
 * created and kept in sync by SQL triggers (see [com.pauta.app.data.AppDatabase])
 * rather than being modelled as a Room `@Entity` — so Room neither creates nor
 * validates it, and we own the exact tokenizer. Because it isn't a known entity,
 * the lookup can't be a verified `@Query`; a [RawQuery] runs the prepared MATCH
 * statement the repository builds. // PT: pesquisa FTS sobre a tabela virtual
 * `search_index`, mantida por triggers; `@RawQuery` porque não é entidade Room.
 */
@Dao
interface SearchDao {
    @RawQuery
    suspend fun search(query: SupportSQLiteQuery): List<SearchHit>
}

/**
 * One full-text match: the day it belongs to (so results group + navigate by
 * day), which of the three sources matched, and the matched text for the snippet.
 * The `docId` (`i:<id>` / `d:<dayKey>` / `b:<id>`) keeps results stable as list
 * keys. // PT: um resultado da pesquisa — dia, origem e texto.
 */
data class SearchHit(
    val docId: String,
    val source: String,   // "intention" | "reflection" | "block"
    val dayKey: String,
    val text: String,
)
