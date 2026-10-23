package id.mybase.aether.persistence

import android.content.Context

/** Composition root for the persistence workstream. */
object PersistenceModule {
    fun createPreferences(context: Context): AetherPreferences =
        AetherPreferences(context.applicationContext)
}
