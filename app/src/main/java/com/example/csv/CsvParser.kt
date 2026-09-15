package com.example.csv

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object CsvParser {

    /**
     * Reads an input stream with automatic encoding detection (UTF-8 with/without BOM, Big5/CP950, GBK, ISO-8859-1).
     */
    fun readCsv(inputStream: InputStream): List<List<String>> {
        val bytes = inputStream.readBytes()
        val text = decodeWithFallback(bytes)
        return parseString(text)
    }

    fun decodeWithFallback(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // Check for UTF-8 BOM (0xEF, 0xBB, 0xBF)
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }

        // Check for UTF-16 LE BOM (0xFF, 0xFE)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }

        // Try standard UTF-8 with strict error handling
        try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
            val charBuffer = decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            var str = charBuffer.toString()
            if (str.startsWith("\uFEFF")) {
                str = str.substring(1)
            }
            return str
        } catch (_: Exception) {
            // Fallback to traditional Chinese / common Windows encodings
        }

        val fallbackCharsets = listOf("Big5", "windows-950", "GBK", "GB2312", "ISO-8859-1")
        for (charsetName in fallbackCharsets) {
            try {
                if (Charset.isSupported(charsetName)) {
                    val charset = Charset.forName(charsetName)
                    val decoder = charset.newDecoder()
                    val charBuffer = decoder.decode(java.nio.ByteBuffer.wrap(bytes))
                    return charBuffer.toString()
                }
            } catch (_: Exception) {
                // continue to next
            }
        }

        // Ultimate fallback
        return String(bytes, StandardCharsets.UTF_8)
    }

    /**
     * RFC 4180 compliant CSV parser supporting quotes, nested commas, and line breaks in fields.
     */
    fun parseString(csvContent: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        if (csvContent.isBlank()) return rows

        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuotes = false
        var i = 0
        val length = csvContent.length

        while (i < length) {
            val c = csvContent[i]

            if (insideQuotes) {
                if (c == '"') {
                    if (i + 1 < length && csvContent[i + 1] == '"') {
                        // Escaped quote "" -> "
                        currentField.append('"')
                        i++
                    } else {
                        // Closing quote
                        insideQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> {
                        insideQuotes = true
                    }
                    ',' -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < length && csvContent[i + 1] == '\n') {
                            i++ // Skip \n in CRLF
                        }
                        currentRow.add(currentField.toString().trim())
                        currentField.setLength(0)
                        if (currentRow.isNotEmpty() && currentRow.any { it.isNotEmpty() }) {
                            rows.add(ArrayList(currentRow))
                        }
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.setLength(0)
                        if (currentRow.isNotEmpty() && currentRow.any { it.isNotEmpty() }) {
                            rows.add(ArrayList(currentRow))
                        }
                        currentRow.clear()
                    }
                    else -> {
                        currentField.append(c)
                    }
                }
            }
            i++
        }

        // Flush last field and row if any
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotEmpty() }) {
                rows.add(ArrayList(currentRow))
            }
        }

        return rows
    }
}
