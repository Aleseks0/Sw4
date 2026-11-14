package cz.mojekoreny.gedcom

enum class Outcome {
    /** Změna se provedla. */
    APPLIED,
    /** Rodokmen už změnu obsahuje (např. po schválení na webu) – nic se neudělalo. */
    NOOP,
    /** Změnu nešlo provést (chybí osoba, obsazené místo rodiče …). */
    FAILED,
}

data class Applied(
    val tree: GedcomTree,
    val outcome: Outcome,
    val message: String? = null,
    /** Pole, která se od úpravy změnila i jinde (na webu) – použila se hodnota z této změny. */
    val conflicts: List<String> = emptyList(),
)

data class ApplyReport(val tree: GedcomTree, val results: List<Pair<Change, Applied>>) {
    val failed get() = results.filter { it.second.outcome == Outcome.FAILED }
    val noop get() = results.filter { it.second.outcome == Outcome.NOOP }
    val conflicts get() = results.filter { it.second.conflicts.isNotEmpty() }
}

object ChangeApplier {

    fun applyAll(tree: GedcomTree, changes: List<Change>): ApplyReport {
        var t = tree
        val results = changes.map { c ->
            val r = apply(t, c)
            if (r.outcome == Outcome.APPLIED) t = r.tree
            c to r
        }
        return ApplyReport(t, results)
    }

    fun apply(tree: GedcomTree, c: Change): Applied = try {
        when (c.op) {
            Op.CREATE_PERSON -> create(tree, c)
            Op.UPDATE_PERSON -> update(tree, c)
            Op.DELETE_PERSON -> delete(tree, c)
            Op.SET_MARRIAGE -> marriage(tree, c)
            Op.ADD_MEDIA -> addMedia(tree, c)
            Op.REMOVE_MEDIA -> removeMedia(tree, c)
            Op.SET_PORTRAIT -> portrait(tree, c)
        }
    } catch (e: IllegalStateException) {
        fail(tree, e.message ?: "změnu nelze provést")
    }

    private fun fail(t: GedcomTree, msg: String) = Applied(t, Outcome.FAILED, msg)
    private fun ok(t: GedcomTree, msg: String? = null, conflicts: List<String> = emptyList()) = Applied(t, Outcome.APPLIED, msg, conflicts)
    private fun noop(t: GedcomTree) = Applied(t, Outcome.NOOP)

    private fun need(t: GedcomTree, key: String): Person =
        t.findByKey(key) ?: error("osoba „$key“ v rodokmenu není")

    // ------------------------------------------------------------ vytvoření osoby

    private fun create(t0: GedcomTree, c: Change): Applied {
        if (t0.findByKey(c.key) != null) return noop(t0)
        val id = nextId("I", t0.persons.map { it.id })
        var person = PersonFields.apply(Person(id = id, uid = c.key), c.fields)
        var t = t0
        val rel = c.relation
        when (rel?.type) {
            null -> t = t.copy(persons = t.persons + person)
            "parent" -> {
                val child = need(t, rel.of)
                val fam = child.familiesAsChild.firstNotNullOfOrNull(t::family)
                if (fam != null) {
                    val asHusband = when (person.sex) {
                        Sex.MALE -> true
                        Sex.FEMALE -> false
                        else -> fam.husband == null
                    }
                    if (asHusband && fam.husband != null) error("${child.name.full.ifEmpty { "Osoba" }} už má otce")
                    if (!asHusband && fam.wife != null) error("${child.name.full.ifEmpty { "Osoba" }} už má matku")
                    person = person.copy(familiesAsSpouse = listOf(fam.id))
                    t = t.replace(if (asHusband) fam.copy(husband = id) else fam.copy(wife = id))
                    t = t.copy(persons = t.persons + person)
                } else {
                    val fid = nextId("F", t.families.map { it.id })
                    val asHusband = person.sex != Sex.FEMALE
                    person = person.copy(familiesAsSpouse = listOf(fid))
                    val fam2 = Family(fid, husband = if (asHusband) id else null, wife = if (asHusband) null else id, children = listOf(child.id))
                    t = t.copy(persons = t.persons + person, families = t.families + fam2)
                    t = t.replace(child.copy(familiesAsChild = child.familiesAsChild + fid))
                }
            }
            "spouse" -> {
                val other = need(t, rel.of)
                val fid = nextId("F", t.families.map { it.id })
                val otherIsWife = other.sex == Sex.FEMALE
                val fam = Family(
                    fid,
                    husband = if (otherIsWife) id else other.id,
                    wife = if (otherIsWife) other.id else id,
                )
                person = person.copy(familiesAsSpouse = listOf(fid))
                t = t.copy(persons = t.persons + person, families = t.families + fam)
                t = t.replace(other.copy(familiesAsSpouse = other.familiesAsSpouse + fid))
            }
            "child" -> {
                val parent = need(t, rel.of)
                val partner = rel.other?.let { need(t, it) }
                val existing = t.families.firstOrNull { f ->
                    (f.husband == parent.id || f.wife == parent.id) &&
                        (partner == null || f.husband == partner.id || f.wife == partner.id)
                } ?: if (partner == null) parent.familiesAsSpouse.firstNotNullOfOrNull(t::family) else null
                if (existing != null) {
                    person = person.copy(familiesAsChild = listOf(existing.id))
                    t = t.copy(persons = t.persons + person)
                    t = t.replace(existing.copy(children = existing.children + id))
                } else {
                    val fid = nextId("F", t.families.map { it.id })
                    val parentIsWife = parent.sex == Sex.FEMALE
                    val fam = Family(
                        fid,
                        husband = if (parentIsWife) partner?.id else parent.id,
                        wife = if (parentIsWife) parent.id else partner?.id,
                        children = listOf(id),
                    )
                    person = person.copy(familiesAsChild = listOf(fid))
                    t = t.copy(persons = t.persons + person, families = t.families + fam)
                    t = t.replace(parent.copy(familiesAsSpouse = parent.familiesAsSpouse + fid))
                    if (partner != null) {
                        val p2 = t.person(partner.id)!!
                        t = t.replace(p2.copy(familiesAsSpouse = p2.familiesAsSpouse + fid))
                    }
                }
            }
            else -> error("neznámý vztah „${rel.type}“")
        }
        return ok(t)
    }

    // ------------------------------------------------------------ úprava osoby

    private fun update(t: GedcomTree, c: Change): Applied {
        val p = need(t, c.key)
        val current = PersonFields.read(p)
        val norm = c.fields.mapValues { it.value?.trim()?.ifEmpty { null } }
        val conflicts = norm.keys.filter { k ->
            val before = c.old[k]?.trim()?.ifEmpty { null }
            k in c.old && current[k] != before && current[k] != norm[k]
        }
        val updated = PersonFields.apply(p, c.fields)
        if (updated == p) return noop(t)
        return ok(t.replace(updated), conflicts = conflicts)
    }

    // ------------------------------------------------------------ smazání osoby

    private fun delete(t0: GedcomTree, c: Change): Applied {
        val p = t0.findByKey(c.key) ?: return noop(t0)
        var families = t0.families.map { f ->
            f.copy(
                husband = f.husband.takeIf { it != p.id },
                wife = f.wife.takeIf { it != p.id },
                children = f.children.filter { it != p.id },
            )
        }
        // Rodina bez rodičů, nebo jediný manžel bez dětí, nemá smysl.
        val dead = families.filter { f ->
            val spouses = listOfNotNull(f.husband, f.wife).size
            spouses == 0 || (spouses == 1 && f.children.isEmpty())
        }.map { it.id }.toSet()
        families = families.filter { it.id !in dead }
        val persons = t0.persons.filter { it.id != p.id }.map { q ->
            q.copy(
                familiesAsChild = q.familiesAsChild.filter { it !in dead },
                familiesAsSpouse = q.familiesAsSpouse.filter { it !in dead },
                childLinkExtra = q.childLinkExtra.filterKeys { it !in dead },
            )
        }
        return ok(t0.copy(persons = persons, families = families))
    }

    // ------------------------------------------------------------ sňatek

    private fun marriage(t0: GedcomTree, c: Change): Applied {
        val a = need(t0, c.key)
        val b = need(t0, c.partner ?: error("chybí druhý z manželů"))
        var t = t0
        var fam = t.families.firstOrNull { (it.husband == a.id && it.wife == b.id) || (it.husband == b.id && it.wife == a.id) }
        var created = false
        if (fam == null) {
            val fid = nextId("F", t.families.map { it.id })
            val aIsWife = a.sex == Sex.FEMALE
            fam = Family(fid, husband = if (aIsWife) b.id else a.id, wife = if (aIsWife) a.id else b.id)
            t = t.copy(families = t.families + fam)
            t = t.replace(a.copy(familiesAsSpouse = a.familiesAsSpouse + fid))
            t = t.replace(t.person(b.id)!!.let { it.copy(familiesAsSpouse = it.familiesAsSpouse + fid) })
            created = true
        }
        val idx = fam.events.indexOfFirst { it.tag == "MARR" }
        val cur = fam.events.getOrNull(idx) ?: Event("MARR")
        val newDate = if ("date" in c.fields) c.fields["date"]?.trim()?.ifEmpty { null } else cur.date
        val newPlace = if ("place" in c.fields) c.fields["place"]?.trim()?.ifEmpty { null } else cur.place
        val upd = cur.copy(date = newDate, place = newPlace)
        val events = when {
            idx >= 0 -> fam.events.mapIndexed { i, e -> if (i == idx) upd else e }
            else -> fam.events + upd
        }
        val result = fam.copy(events = events)
        if (!created && result == fam) return noop(t0)
        return ok(t.replace(result))
    }

    // ------------------------------------------------------------ fotografie

    private fun addMedia(t: GedcomTree, c: Change): Applied {
        val m = c.media ?: error("chybí fotografie")
        val p = need(t, c.key)
        if (t.photosOf(p).any { it.path == m.path }) return noop(t)
        val link = MediaLink(file = MediaFile(m.path, m.format ?: "jpg", m.title))
        return ok(t.replace(p.copy(media = p.media + link)))
    }

    private fun removeMedia(t: GedcomTree, c: Change): Applied {
        val m = c.media ?: error("chybí fotografie")
        val p = t.findByKey(c.key) ?: return noop(t)
        val keep = p.media.filterNot { link ->
            link.file?.path == m.path ||
                (link.mediaId != null && t.media.firstOrNull { it.id == link.mediaId }?.files?.any { it.path == m.path } == true)
        }
        if (keep.size == p.media.size) return noop(t)
        return ok(t.replace(p.copy(media = keep)))
    }

    private fun portrait(t: GedcomTree, c: Change): Applied {
        val m = c.media ?: error("chybí fotografie")
        val p = need(t, c.key)
        val target = p.media.indexOfFirst { link ->
            link.file?.path == m.path ||
                (link.mediaId != null && t.media.firstOrNull { it.id == link.mediaId }?.files?.any { it.path == m.path } == true)
        }
        if (target < 0) error("fotografie „${m.path}“ u osoby není")
        val already = p.media.withIndex().all { (i, l) -> l.isPrimary() == (i == target) }
        if (already) return noop(t)
        val media = p.media.mapIndexed { i, l ->
            val without = l.copy(extra = l.extra.filterNot { it.tag == "_PRIM" })
            if (i == target) without.copy(extra = without.extra + GedcomNode("_PRIM", "Y")) else without
        }
        return ok(t.replace(p.copy(media = media)))
    }

    private fun MediaLink.isPrimary() = extra.any { it.tag == "_PRIM" && it.value.equals("Y", ignoreCase = true) }
}
