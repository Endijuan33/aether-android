package id.mybase.aether.packages

import id.mybase.aether.domain.AppPackage
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.domain.kind
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** App version stamped into every inventory export. */
const val EXPORT_APP_VERSION = "0.1.0"

/**
 * Serializes an inventory snapshot to JSON using org.json (bundled with Android — no new
 * dependency).
 *
 * Shape: {"exportedAt": "<ISO-8601 UTC>", "appVersion": "0.1.0", "packages":
 * [{"packageName","label","versionName","versionCode","kind", "safetyRating","isEnabled","permissions"}]}
 */
fun exportInventoryJson(apps: List<Pair<AppPackage, SafetyRating>>): String {
    val root = JSONObject()
    root.put("exportedAt", Instant.now().toString())
    root.put("appVersion", EXPORT_APP_VERSION)
    val packages = JSONArray()
    for ((app, rating) in apps) {
        val o = JSONObject()
        o.put("packageName", app.packageName)
        o.put("label", app.label)
        if (app.versionName != null) o.put("versionName", app.versionName)
        else o.put("versionName", JSONObject.NULL)
        o.put("versionCode", app.versionCode)
        o.put("kind", app.kind().name)
        o.put("safetyRating", rating.name)
        o.put("isEnabled", app.isEnabled)
        o.put("permissions", JSONArray(app.permissions))
        packages.put(o)
    }
    root.put("packages", packages)
    return root.toString(2)
}
