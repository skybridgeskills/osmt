package edu.wgu.osmt.credentialengine

import edu.wgu.osmt.collection.Collection
import edu.wgu.osmt.richskill.RichSkillDescriptor

interface SyncTarget {
    fun publishSkill(rsd: RichSkillDescriptor): Result<Unit>

    fun publishCollection(
        collection: Collection,
        skillCtids: List<String>,
    ): Result<Unit>

    fun deprecateSkill(rsd: RichSkillDescriptor): Result<Unit>

    fun deprecateCollection(collection: Collection): Result<Unit>
}
