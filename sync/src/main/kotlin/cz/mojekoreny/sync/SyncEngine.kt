package cz.mojekoreny.sync

import cz.mojekoreny.gedcom.Applied
import cz.mojekoreny.gedcom.Change
import cz.mojekoreny.gedcom.ChangeApplier
import cz.mojekoreny.gedcom.ChangeState
import cz.mojekoreny.gedcom.GedcomReader
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.Op
import cz.mojekoreny.gedcom.Outcome

data class SyncOutcome(
    /** Rodokmen z webu s přehranými místními změnami; null, pokud se stažení nepovedlo. */
    val tree: GedcomTree?,
    /** Místní změny, které zůstávají (čekají na odeslání nebo na schválení na webu). */
    val changes: List<Change>,
    val pushed: Int = 0,
    /** false = web zatím nepřijímá změny; změny zůstanou v telefonu. */
    val pushSupported: Boolean = true,
    /** Změny, které už web obsahuje (schválené) – z fronty zmizely. */
    val mergedOnWeb: Int = 0,
    /** Změny odmítnuté webem nebo nepoužitelné na staženém rodokmenu (id → důvod). */
    val dropped: Map<String, String> = emptyMap(),
    /** Úpravy polí, která se mezitím změnila i na webu (použila se vaše hodnota). */
    val conflicts: List<Pair<Change, List<String>>> = emptyList(),
    val mediaUploaded: Int = 0,
    val downloadError: WebException? = null,
)

/**
 * Synchronizace: 1) odeslat fotky a změny, 2) stáhnout rodokmen z webu,
 * 3) znovu na něj použít změny, které web ještě neobsahuje.
 * Přihlášení/síťové chyby před odesláním změn vyhodí výjimku a nic se nemění.
 */
class SyncEngine(private val api: WebApi) {

    fun sync(local: List<Change>, loadMedia: (sha: String) -> ByteArray?): SyncOutcome {
        var changes = local
        var pushed = 0
        var supported = true
        var uploaded = 0
        val dropped = linkedMapOf<String, String>()

        val toPush = changes.filter { it.state == ChangeState.PENDING }
        if (toPush.isNotEmpty()) {
            // Fotky nejdřív, aby na webu nevznikly změny odkazující na chybějící soubor.
            var mediaSupported = true
            for (c in toPush) {
                val m = c.media
                if (c.op != Op.ADD_MEDIA || m == null || !mediaSupported) continue
                val bytes = loadMedia(m.sha) ?: continue
                if (api.uploadMedia(m.sha, bytes, "image/${m.format ?: "jpeg"}".replace("image/jpg", "image/jpeg"))) uploaded++
                else mediaSupported = false
            }
            val res = api.pushChanges(toPush)
            supported = res.supported
            if (res.supported) {
                changes = changes.mapNotNull { c ->
                    when {
                        c.id in res.rejected -> { dropped[c.id] = res.rejected.getValue(c.id); null }
                        c.id in res.accepted -> { pushed++; c.copy(state = ChangeState.SENT) }
                        else -> c
                    }
                }
            }
        }

        val base = try {
            GedcomReader.read(api.downloadGedcom())
        } catch (e: WebException) {
            return SyncOutcome(null, changes, pushed, supported, 0, dropped, emptyList(), uploaded, e)
        }

        val report = ChangeApplier.applyAll(base, changes)
        val keep = mutableListOf<Change>()
        var merged = 0
        for ((c, r) in report.results) {
            when (r.outcome) {
                Outcome.APPLIED -> keep += c
                Outcome.NOOP -> merged++
                Outcome.FAILED -> dropped[c.id] = r.message ?: "změnu nelze použít"
            }
        }
        val conflicts = report.conflicts.map { (c, r: Applied) -> c to r.conflicts }
        return SyncOutcome(report.tree, keep, pushed, supported, merged, dropped, conflicts, uploaded, null)
    }
}
