package io.github.otato.recipebook.domain

import java.util.UUID

/** ID の作り方。ID は UUID にする（バックアップから戻しても、番号がぶつからない）。テストでは固定の値を返す実装に差し替える。 */
fun interface IdGenerator {
    fun next(): String
}

object UuidGenerator : IdGenerator {
    override fun next(): String = UUID.randomUUID().toString()
}
