package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.csv.ColumnMapping
import com.example.csv.CsvImporter
import com.example.csv.CsvParser
import com.example.csv.DuplicateStrategy
import com.example.data.AppDatabase
import com.example.data.StudentEntity
import com.example.data.StudentRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("懸浮抽籤", appName)
    }

    @Test
    fun testCsvParserAndImporter() = runBlocking {
        val csvData = """
            座號,姓名,英文姓名
            1,王小明,Wang Xiao Ming
            2,"李大華, Jr.",Lee Da Hua
            3,張美麗,
        """.trimIndent()

        val parsed = CsvParser.readCsv(ByteArrayInputStream(csvData.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(4, parsed.size)

        val analysis = CsvImporter.analyzeCsv(parsed)
        assertNotNull(analysis)
        assertEquals(0, analysis!!.suggestedMapping.numberColIndex)
        assertEquals(1, analysis.suggestedMapping.nameColIndex)
        assertEquals(2, analysis.suggestedMapping.englishNameColIndex)

        val validation = CsvImporter.validateRows(analysis.rawRows, analysis.suggestedMapping)
        assertEquals(3, validation.validStudents.size)
        assertEquals(0, validation.errors.size)
        assertEquals(1, validation.validStudents[0].number)
        assertEquals("王小明", validation.validStudents[0].name)
        assertEquals("李大華, Jr.", validation.validStudents[1].name)
    }
}
