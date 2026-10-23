package id.mybase.aether.packages

import id.mybase.aether.domain.CatalogueEntry
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.security.InputValidators

/**
 * Strict loader for assets/catalogue.json.
 *
 * Uses a small hand-rolled JSON parser (no new dependency) so the catalogue can be validated
 * identically on-device and in JVM unit tests. Any schema violation throws [IllegalStateException]
 * with a precise message; callers are expected to catch it at startup and surface it to the user,
 * never swallow it.
 */
internal data class ParsedCatalogue(
    val revision: String,
    val updated: String,
    val source: String,
    val entries: List<CatalogueEntry>,
)

internal fun parseCatalogueJson(text: String): ParsedCatalogue {
    fun fail(why: String): Nothing = throw IllegalStateException("catalogue.json: $why")

    val root =
        try {
            JsonParser(text).parse()
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            fail("not valid JSON (${e.message})")
        }
    if (root !is JsonObject) fail("top-level value must be an object")

    val revision = (root["revision"] as? JsonString)?.value ?: fail("missing \"revision\"")
    if (revision.isBlank()) fail("\"revision\" must not be blank")

    val updated = (root["updated"] as? JsonString)?.value ?: ""
    val source = (root["source"] as? JsonString)?.value ?: ""
    val entriesRaw = root["entries"] as? JsonArray ?: fail("missing \"entries\" array")

    val entries =
        entriesRaw.values.mapIndexed { index, raw ->
            parseCatalogueEntry(raw, index, ::fail)
        }

    val duplicates = entries.groupBy { it.packageName }.filter { it.value.size > 1 }.keys
    if (duplicates.isNotEmpty())
        fail("duplicate packageName entries: ${duplicates.sorted().joinToString()}")

    return ParsedCatalogue(revision, updated, source, entries)
}

private fun parseCatalogueEntry(
    raw: JsonValue,
    index: Int,
    fail: (String) -> Nothing,
): CatalogueEntry {
    if (raw !is JsonObject) fail("entries[$index] must be an object")
    val where = "entries[$index]"

    val packageName =
        (raw["packageName"] as? JsonString)?.value ?: fail("$where: missing \"packageName\"")
    if (!InputValidators.isValidPackageName(packageName)) {
        fail("$where: invalid packageName \"$packageName\"")
    }

    val ratingRaw =
        (raw["rating"] as? JsonString)?.value ?: fail("$where ($packageName): missing \"rating\"")
    val rating =
        try {
            SafetyRating.valueOf(ratingRaw)
        } catch (e: IllegalArgumentException) {
            fail(
                "$where ($packageName): unknown rating \"$ratingRaw\" (expected one of ${SafetyRating.entries.joinToString()})",
            )
        }

    val note =
        (raw["note"] as? JsonString)?.value ?: fail("$where ($packageName): missing \"note\"")
    if (note.isBlank()) fail("$where ($packageName): \"note\" must not be blank")

    val source =
        (raw["source"] as? JsonString)?.value ?: fail("$where ($packageName): missing \"source\"")
    if (source.isBlank()) fail("$where ($packageName): \"source\" must not be blank")

    // revision/updated are stamped from the top-level document; per-entry
    // overrides are accepted but not required.
    val revision = (raw["revision"] as? JsonString)?.value ?: ""
    val updated = (raw["updated"] as? JsonString)?.value ?: ""

    return CatalogueEntry(
        packageName = packageName,
        rating = rating,
        note = note,
        source = source,
        revision = revision,
        updated = updated,
    )
}

// ---------------------------------------------------------------------------
// Minimal JSON parser: objects, arrays, strings (with escapes), numbers,
// true/false/null. Strict — trailing garbage or truncated input is an error.
// ---------------------------------------------------------------------------

internal sealed interface JsonValue

internal data class JsonObject(val members: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = members[key]
}

internal data class JsonArray(val values: List<JsonValue>) : JsonValue

internal data class JsonString(val value: String) : JsonValue

internal data class JsonNumber(val value: String) : JsonValue

internal data object JsonTrue : JsonValue

internal data object JsonFalse : JsonValue

internal data object JsonNull : JsonValue

internal class JsonParser(private val text: String) {
    private var pos = 0

    fun parse(): JsonValue {
        val v = parseValue()
        skipWs()
        if (pos != text.length) fail("trailing characters after top-level value")
        return v
    }

    private fun fail(why: String): Nothing =
        throw IllegalStateException("catalogue.json: invalid JSON at offset $pos: $why")

    private fun skipWs() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun parseValue(): JsonValue {
        skipWs()
        if (pos >= text.length) fail("unexpected end of input")
        return when (val c = text[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonString(parseString())
            't' -> expect("true", JsonTrue)
            'f' -> expect("false", JsonFalse)
            'n' -> expect("null", JsonNull)
            '-',
            in '0'..'9' -> parseNumber()
            else -> fail("unexpected character '$c'")
        }
    }

    private fun expect(literal: String, value: JsonValue): JsonValue {
        if (!text.startsWith(literal, pos)) fail("expected '$literal'")
        pos += literal.length
        return value
    }

    private fun parseObject(): JsonObject {
        pos++ // {
        val map = LinkedHashMap<String, JsonValue>()
        skipWs()
        if (pos < text.length && text[pos] == '}') {
            pos++
            return JsonObject(map)
        }
        while (true) {
            skipWs()
            if (pos >= text.length || text[pos] != '"') fail("expected string key")
            val key = parseString()
            skipWs()
            if (pos >= text.length || text[pos] != ':') fail("expected ':' after key")
            pos++
            val value = parseValue()
            if (map.put(key, value) != null) fail("duplicate key \"$key\"")
            skipWs()
            if (pos >= text.length) fail("unterminated object")
            when (text[pos]) {
                ',' -> {
                    pos++
                    continue
                }
                '}' -> {
                    pos++
                    return JsonObject(map)
                }
                else -> fail("expected ',' or '}' in object")
            }
        }
    }

    private fun parseArray(): JsonArray {
        pos++ // [
        val list = ArrayList<JsonValue>()
        skipWs()
        if (pos < text.length && text[pos] == ']') {
            pos++
            return JsonArray(list)
        }
        while (true) {
            list.add(parseValue())
            skipWs()
            if (pos >= text.length) fail("unterminated array")
            when (text[pos]) {
                ',' -> {
                    pos++
                    continue
                }
                ']' -> {
                    pos++
                    return JsonArray(list)
                }
                else -> fail("expected ',' or ']' in array")
            }
        }
    }

    private fun parseString(): String {
        pos++ // opening quote
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("unterminated string")
            val c = text[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= text.length) fail("unterminated escape")
                    when (val e = text[pos++]) {
                        '"',
                        '\\',
                        '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail("bad unicode escape")
                            val hex = text.substring(pos, pos + 4)
                            val code = hex.toIntOrNull(16) ?: fail("bad unicode escape \\u$hex")
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> fail("bad escape '\\$e'")
                    }
                }
                else -> {
                    if (c < ' ') fail("unescaped control character")
                    sb.append(c)
                }
            }
        }
    }

    private fun parseNumber(): JsonNumber {
        val start = pos
        if (pos < text.length && text[pos] == '-') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
        return JsonNumber(text.substring(start, pos))
    }
}
