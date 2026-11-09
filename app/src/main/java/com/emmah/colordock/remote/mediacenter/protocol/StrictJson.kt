package com.emmah.colordock.remote.mediacenter.protocol

/** RFC 8259 syntax gate: Android JSONTokener also accepts JavaScript-like extensions. */
internal class StrictJson private constructor(
    private val input: String,
) {
    private var index = 0

    private fun peek(): Char = input.getOrNull(index) ?: '\u0000'

    private fun take(char: Char) {
        if (peek() != char) invalid()
        index++
    }

    private fun whitespace() {
        while (peek() in " \t\r\n") index++
    }

    private fun string(): String {
        val result = StringBuilder()
        take('"')
        var pendingHigh = false
        var pendingEscaped = false
        while (true) {
            var char = peek()
            if (char == '"') {
                if (pendingHigh) invalid()
                index++
                return result.toString()
            }
            if (char < ' ') invalid()
            index++
            val escaped = char == '\\'
            if (escaped) char = escapedChar()
            when {
                pendingHigh -> {
                    if (!char.isLowSurrogate() ||
                        pendingEscaped != escaped
                    ) {
                        invalid()
                    } else {
                        pendingHigh = false
                    }
                }

                char.isHighSurrogate() -> {
                    pendingHigh = true
                    pendingEscaped = escaped
                }

                char.isLowSurrogate() -> {
                    invalid()
                }
            }
            result.append(char)
        }
    }

    private fun escapedChar(): Char {
        val char = peek()
        index++
        return when (char) {
            '"', '\\', '/' -> char
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> unicodeChar(input, index).also { index += UNICODE_HEX_DIGITS }
            else -> invalid()
        }
    }

    private fun digits() {
        if (peek() !in '0'..'9') invalid()
        while (peek() in '0'..'9') index++
    }

    private fun number() {
        val start = index
        if (peek() == '-') index++
        if (peek() == '0') index++ else digits()
        if (peek() == '.') {
            index++
            digits()
        }
        if (peek() == 'e' || peek() == 'E') {
            index++
            if (peek() == '+' || peek() == '-') index++
            digits()
        }
        if (input.substring(start, index).toDoubleOrNull()?.isFinite() != true) invalid()
    }

    private fun literal(word: String) {
        if (!input.startsWith(word, index)) invalid()
        index += word.length
    }

    private fun collection(
        open: Char,
        close: Char,
        depth: Int,
    ) {
        take(open)
        whitespace()
        if (peek() == close) {
            index++
            return
        }
        val keys = mutableSetOf<String>()
        while (true) {
            if (open == '{') {
                if (!keys.add(string())) invalid()
                whitespace()
                take(':')
            }
            value(depth + 1)
            whitespace()
            if (peek() == close) {
                index++
                return
            }
            take(',')
            whitespace()
        }
    }

    private fun value(depth: Int) {
        if (depth >= MAX_DEPTH) invalid()
        whitespace()
        when (peek()) {
            '{' -> collection('{', '}', depth)
            '[' -> collection('[', ']', depth)
            '"' -> string()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> number()
            else -> invalid()
        }
    }

    companion object {
        private const val MAX_DEPTH = 128
        private const val UNICODE_HEX_DIGITS = 4
        private const val HEX_RADIX = 16

        private fun invalid(): Nothing = throw McProtocolException(WireErrorCode.INVALID_REQUEST)

        private fun unicodeChar(
            input: String,
            offset: Int,
        ): Char {
            if (offset + UNICODE_HEX_DIGITS > input.length) invalid()
            val code = input.substring(offset, offset + UNICODE_HEX_DIGITS)
            if (!code.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) invalid()
            return code.toInt(HEX_RADIX).toChar()
        }

        fun validate(input: String) {
            val parser = StrictJson(input)
            parser.value(0)
            parser.whitespace()
            if (parser.index != input.length) invalid()
        }
    }
}
