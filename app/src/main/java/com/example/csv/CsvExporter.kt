package com.example.csv

import com.example.data.StudentEntity
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object CsvExporter {

    /**
     * Exports students to CSV formatted stream with UTF-8 BOM so Excel opens with proper Chinese characters.
     */
    fun exportToCsv(students: List<StudentEntity>, outputStream: OutputStream) {
        val writer = OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
        // Write UTF-8 BOM
        writer.write("\uFEFF")

        // Write Header
        writer.write("座號,姓名,英文姓名\r\n")

        // Write Rows
        for (student in students) {
            val numStr = student.number.toString()
            val nameStr = escapeCsvField(student.name)
            val engNameStr = escapeCsvField(student.englishName)
            writer.write("$numStr,$nameStr,$engNameStr\r\n")
        }

        writer.flush()
    }

    private fun escapeCsvField(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            val escaped = value.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return value
    }
}
