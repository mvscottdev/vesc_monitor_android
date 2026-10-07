package dev.vescmonitor.core.replay

/**
 * Minimal JSON for one-level objects whose values are strings, numbers, booleans or
 * null. Enough for the fixed capture schema without a serialization library.
 */
object FlatJson {
    fun write(fields: Map<String, Any?>): String {
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in fields) {
            if (!first) sb.append(',')
            first = false
            string(sb, k)
            sb.append(':')
            when (v) {
                null -> sb.append("null")
                is String -> string(sb, v)
                is Boolean, is Int, is Long -> sb.append(v.toString())
                is Number -> sb.append(v.toDouble().toString())
                else -> throw IllegalArgumentException("unsupported value for $k")
            }
        }
        return sb.append('}').toString()
    }

    /** Parses one object; numbers come back as Double, strings unescaped. */
    fun parse(line: String): Map<String, Any?> = Parser(line).obj()

    private fun string(
        sb: StringBuilder,
        s: String,
    ) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(
        private val s: String,
    ) {
        private var i = 0

        fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            expect('{')
            skipWs()
            if (peek() == '}') {
                i++
                return out
            }
            while (true) {
                skipWs()
                val key = str()
                skipWs()
                expect(':')
                skipWs()
                out[key] = value()
                skipWs()
                if (peek() == ',') i++ else break
            }
            expect('}')
            return out
        }

        private fun value(): Any? =
            when (peek()) {
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }

        private fun literal(
            word: String,
            v: Any?,
        ): Any? {
            require(s.startsWith(word, i)) { "bad literal at $i" }
            i += word.length
            return v
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("bad number at $start")
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> sb.append(escape())
                    else -> sb.append(c)
                }
            }
        }

        private fun escape(): Char =
            when (val e = s[i++]) {
                'n' -> {
                    '\n'
                }

                'r' -> {
                    '\r'
                }

                't' -> {
                    '\t'
                }

                'b' -> {
                    '\b'
                }

                'f' -> {
                    '\u000C'
                }

                'u' -> {
                    s
                        .substring(i, i + 4)
                        .toInt(16)
                        .toChar()
                        .also { i += 4 }
                }

                else -> {
                    e
                }
            }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun expect(c: Char) {
            require(peek() == c) { "expected '$c' at $i" }
            i++
        }
    }
}
