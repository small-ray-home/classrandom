package com.example.csv

import com.example.data.StudentEntity
import com.example.data.StudentRepository

enum class DuplicateStrategy(val title: String, val description: String) {
    OVERWRITE("覆蓋", "若座號已存在，以新資料覆蓋現有資料"),
    SKIP("略過", "若座號已存在，跳過不匯入該筆資料"),
    CREATE_NEW("建立新資料", "即使座號重複，仍作為獨立學生新增")
}

data class ColumnMapping(
    val numberColIndex: Int = -1, // -1 means Not mapped
    val nameColIndex: Int = -1,
    val englishNameColIndex: Int = -1
)

data class ParsedStudent(
    val rowNumber: Int,
    val number: Int,
    val name: String,
    val englishName: String
)

data class CsvRowError(
    val rowNumber: Int,
    val rawContent: String,
    val reason: String
)

data class CsvValidationResult(
    val validStudents: List<ParsedStudent>,
    val errors: List<CsvRowError>,
    val totalRows: Int
)

data class CsvHeaderAnalysis(
    val headers: List<String>,
    val rawRows: List<List<String>>,
    val suggestedMapping: ColumnMapping
)

object CsvImporter {

    /**
     * Inspects the parsed CSV data, extracts headers, and computes smart column recommendations.
     */
    fun analyzeCsv(parsedRows: List<List<String>>): CsvHeaderAnalysis? {
        if (parsedRows.isEmpty()) return null

        val headers = parsedRows.first()
        val dataRows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()

        var numberIndex = -1
        var nameIndex = -1
        var engNameIndex = -1

        for ((index, header) in headers.withIndex()) {
            val clean = header.trim().lowercase()
            // Check Seat / Student Number
            if (numberIndex == -1 && (
                    clean.contains("座號") || clean.contains("座号") ||
                    clean.contains("學號") || clean.contains("学号") ||
                    clean.contains("號碼") || clean == "號" || clean == "号" ||
                    clean.contains("number") || clean == "no" || clean == "no." ||
                    clean == "id" || clean.contains("seat")
                )) {
                numberIndex = index
            }
            // Check English Name (check before general name to avoid false matching)
            else if (engNameIndex == -1 && (
                    clean.contains("英文") || clean.contains("english") ||
                    clean.contains("eng name") || clean == "en_name" ||
                    clean == "eng"
                )) {
                engNameIndex = index
            }
            // Check Chinese / General Name
            else if (nameIndex == -1 && (
                    clean.contains("姓名") || clean.contains("中文") ||
                    clean.contains("學生姓名") || clean.contains("学生姓名") ||
                    clean == "名字" || clean == "name" || clean == "student name" || clean == "student"
                )) {
                nameIndex = index
            }
        }

        // Fallbacks if nothing matched by name
        if (numberIndex == -1 && headers.size >= 1) numberIndex = 0
        if (nameIndex == -1 && headers.size >= 2) nameIndex = 1
        if (engNameIndex == -1 && headers.size >= 3) engNameIndex = 2

        val suggested = ColumnMapping(
            numberColIndex = numberIndex,
            nameColIndex = nameIndex,
            englishNameColIndex = engNameIndex
        )

        return CsvHeaderAnalysis(
            headers = headers,
            rawRows = dataRows,
            suggestedMapping = suggested
        )
    }

    /**
     * Validates data rows according to selected column mapping.
     */
    fun validateRows(
        dataRows: List<List<String>>,
        mapping: ColumnMapping,
        startRowIndex: Int = 2 // Row 1 is header, data starts at Row 2
    ): CsvValidationResult {
        val validList = mutableListOf<ParsedStudent>()
        val errorList = mutableListOf<CsvRowError>()

        for ((i, row) in dataRows.withIndex()) {
            val rowNum = startRowIndex + i
            val rawStr = row.joinToString(", ")

            if (row.all { it.isBlank() }) {
                continue // Skip empty line
            }

            // Extract Number
            if (mapping.numberColIndex < 0 || mapping.numberColIndex >= row.size) {
                errorList.add(CsvRowError(rowNum, rawStr, "未對應座號欄位或該列資料不足"))
                continue
            }
            val numberStr = row[mapping.numberColIndex].trim()
            if (numberStr.isEmpty()) {
                errorList.add(CsvRowError(rowNum, rawStr, "座號不能為空"))
                continue
            }
            val number = numberStr.toIntOrNull()
            if (number == null || number < 0) {
                errorList.add(CsvRowError(rowNum, rawStr, "座號不是有效數字 (內容: $numberStr)"))
                continue
            }

            // Extract Name
            if (mapping.nameColIndex < 0 || mapping.nameColIndex >= row.size) {
                errorList.add(CsvRowError(rowNum, rawStr, "未對應姓名欄位或該列資料不足"))
                continue
            }
            val nameStr = row[mapping.nameColIndex].trim()
            if (nameStr.isEmpty()) {
                errorList.add(CsvRowError(rowNum, rawStr, "姓名不能為空"))
                continue
            }

            // Extract English Name (Optional)
            val englishNameStr = if (mapping.englishNameColIndex in 0 until row.size) {
                row[mapping.englishNameColIndex].trim()
            } else {
                ""
            }

            validList.add(
                ParsedStudent(
                    rowNumber = rowNum,
                    number = number,
                    name = nameStr,
                    englishName = englishNameStr
                )
            )
        }

        return CsvValidationResult(
            validStudents = validList,
            errors = errorList,
            totalRows = dataRows.size
        )
    }

    /**
     * Imports validated students into Room database according to the duplicate strategy.
     * Returns count of successfully imported / updated records.
     */
    suspend fun executeImport(
        repository: StudentRepository,
        studentsToImport: List<ParsedStudent>,
        strategy: DuplicateStrategy
    ): Int {
        var count = 0
        for (parsed in studentsToImport) {
            val existing = repository.getStudentByNumber(parsed.number)
            if (existing != null) {
                when (strategy) {
                    DuplicateStrategy.OVERWRITE -> {
                        repository.updateStudent(
                            existing.copy(
                                name = parsed.name,
                                englishName = parsed.englishName,
                                enabled = true,
                                drawnInCurrentRound = false
                            )
                        )
                        count++
                    }
                    DuplicateStrategy.SKIP -> {
                        // Skip without altering existing record
                    }
                    DuplicateStrategy.CREATE_NEW -> {
                        repository.insertStudent(
                            StudentEntity(
                                number = parsed.number,
                                name = parsed.name,
                                englishName = parsed.englishName,
                                enabled = true,
                                drawnInCurrentRound = false
                            )
                        )
                        count++
                    }
                }
            } else {
                // Not existing, insert directly
                repository.insertStudent(
                    StudentEntity(
                        number = parsed.number,
                        name = parsed.name,
                        englishName = parsed.englishName,
                        enabled = true,
                        drawnInCurrentRound = false
                    )
                )
                count++
            }
        }
        return count
    }
}
