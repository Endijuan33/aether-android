package id.mybase.aether.security

import java.util.regex.Pattern

object InputValidators {
    private val PACKAGE: Pattern = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]*)+$")
    private val IPV4: Pattern =
        Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])$",
        )
    private val HOSTNAME: Pattern =
        Pattern.compile(
            "^(?=.{1,253}\$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*$",
        )

    fun filterPairingDigits(input: String): String = input.filter { it.isDigit() }.take(6)

    fun isValidPairingCode(code: String): Boolean = code.length == 6 && code.all { it.isDigit() }

    fun isValidHost(host: String): Boolean {
        val h = host.trim()
        return h.isNotEmpty() && (IPV4.matcher(h).matches() || HOSTNAME.matcher(h).matches())
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { isValidPort(it) }

    fun isValidPackageName(id: String): Boolean = id.length <= 255 && PACKAGE.matcher(id).matches()
}
