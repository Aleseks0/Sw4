package cz.mojekoreny.gedcom

/**
 * Úprava rodokmenu jako opakovatelný záznam. Stejný záznam se použije pro úpravu
 * v telefonu, pro odeslání na web i pro znovupoužití na čerstvě staženém rodokmenu.
 * Osoby se označují klíčem (`_UID` / slug z webu, jinak ID v souboru).
 */
enum class Op(val code: String) {
    CREATE_PERSON("create_person"),
    UPDATE_PERSON("update_person"),
    DELETE_PERSON("delete_person"),
    SET_MARRIAGE("set_marriage"),
    ADD_MEDIA("add_media"),
    REMOVE_MEDIA("remove_media"),
    SET_PORTRAIT("set_portrait");

    companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } }
}

/** Vztah nové osoby k existující: rodič / partner / dítě osoby [of]. */
data class Relation(
    /** „parent“, „spouse“ nebo „child“ */
    val type: String,
    val of: String,
    /** U dítěte: klíč druhého rodiče (určuje rodinu). */
    val other: String? = null,
)

data class MediaRef(val sha: String, val path: String, val title: String? = null, val format: String? = null)

enum class ChangeState(val code: String) {
    /** Čeká na odeslání na web. */
    PENDING("pending"),
    /** Web změnu přijal, ale ještě není ve staženém rodokmenu (čeká na schválení). */
    SENT("sent");

    companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } ?: PENDING }
}

data class Change(
    val id: String,
    val ts: String,
    val author: String,
    val op: Op,
    /** Klíč osoby, které se změna týká (u SET_MARRIAGE první z páru). */
    val key: String,
    val fields: Map<String, String?> = emptyMap(),
    /** Hodnoty polí před úpravou – slouží k odhalení, že se pole mezitím změnilo i na webu. */
    val old: Map<String, String?> = emptyMap(),
    val relation: Relation? = null,
    val partner: String? = null,
    val media: MediaRef? = null,
    val state: ChangeState = ChangeState.PENDING,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "ts" to ts, "author" to author, "op" to op.code, "key" to key,
        "fields" to fields, "old" to old,
        "relation" to relation?.let { mapOf("type" to it.type, "of" to it.of, "other" to it.other) },
        "partner" to partner,
        "media" to media?.let { mapOf("sha" to it.sha, "path" to it.path, "title" to it.title, "format" to it.format) },
        "state" to state.code,
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(m: Map<String, Any?>): Change {
            fun strMap(x: Any?) = (x as? Map<String, Any?>)?.mapValues { it.value as? String } ?: emptyMap()
            return Change(
                id = m["id"] as String,
                ts = m["ts"] as? String ?: "",
                author = m["author"] as? String ?: "",
                op = Op.of(m["op"] as? String) ?: throw IllegalArgumentException("neznámá operace ${m["op"]}"),
                key = m["key"] as String,
                fields = strMap(m["fields"]),
                old = strMap(m["old"]),
                relation = (m["relation"] as? Map<String, Any?>)?.let {
                    Relation(it["type"] as String, it["of"] as String, it["other"] as? String)
                },
                partner = m["partner"] as? String,
                media = (m["media"] as? Map<String, Any?>)?.let {
                    MediaRef(it["sha"] as String, it["path"] as String, it["title"] as? String, it["format"] as? String)
                },
                state = ChangeState.of(m["state"] as? String),
            )
        }

        fun listToJson(changes: List<Change>, pretty: Boolean = false): String =
            MiniJson.write(changes.map { it.toMap() }, pretty)

        @Suppress("UNCHECKED_CAST")
        fun listFromJson(json: String): List<Change> =
            (MiniJson.parse(json) as? List<Any?> ?: emptyList()).map { fromMap(it as Map<String, Any?>) }
    }
}
