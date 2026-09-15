package com.example

import com.example.data.ClassScheduleManager
import com.example.data.ScheduleStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {
    @Test
    fun testPasswordVerification() {
        assertTrue(ClassScheduleManager.verifyPassword("034565"))
        assertFalse(ClassScheduleManager.verifyPassword("000000"))
        assertFalse(ClassScheduleManager.verifyPassword("123456"))
        assertFalse(ClassScheduleManager.verifyPassword(""))
    }

    @Test
    fun testClassPeriodsDefinition() {
        val periods = ClassScheduleManager.PERIODS
        assertEquals(9, periods.size)

        // zm0s 08:30:00 zm1x 09:15:00
        assertEquals(8, periods[0].startHour)
        assertEquals(30, periods[0].startMinute)
        assertEquals(9, periods[0].endHour)
        assertEquals(15, periods[0].endMinute)

        // zm2s 09:25:00 zm2x 10:10:00
        assertEquals(9, periods[1].startHour)
        assertEquals(25, periods[1].startMinute)
        assertEquals(10, periods[1].endHour)
        assertEquals(10, periods[1].endMinute)

        // zm3s 10:20:00 zm3x 11:05:00
        assertEquals(10, periods[2].startHour)
        assertEquals(20, periods[2].startMinute)
        assertEquals(11, periods[2].endHour)
        assertEquals(5, periods[2].endMinute)

        // zm4s 11:15:00 zm4x 12:00:00
        assertEquals(11, periods[3].startHour)
        assertEquals(15, periods[3].startMinute)
        assertEquals(12, periods[3].endHour)
        assertEquals(0, periods[3].endMinute)

        // zm5s 12:05:00 zm5x 13:10:00
        assertEquals(12, periods[4].startHour)
        assertEquals(5, periods[4].startMinute)
        assertEquals(13, periods[4].endHour)
        assertEquals(10, periods[4].endMinute)

        // zm6s 13:15:00 zm6x 14:00:00
        assertEquals(13, periods[5].startHour)
        assertEquals(15, periods[5].startMinute)
        assertEquals(14, periods[5].endHour)
        assertEquals(0, periods[5].endMinute)

        // zm7s 14:10:00 zm7x 14:55:00
        assertEquals(14, periods[6].startHour)
        assertEquals(10, periods[6].startMinute)
        assertEquals(14, periods[6].endHour)
        assertEquals(55, periods[6].endMinute)

        // zm8s 15:05:00 zm8x 15:50:00
        assertEquals(15, periods[7].startHour)
        assertEquals(5, periods[7].startMinute)
        assertEquals(15, periods[7].endHour)
        assertEquals(50, periods[7].endMinute)

        // zm9s 16:00:00 zm9x 16:45:00
        assertEquals(16, periods[8].startHour)
        assertEquals(0, periods[8].startMinute)
        assertEquals(16, periods[8].endHour)
        assertEquals(45, periods[8].endMinute)
    }

    @Test
    fun testScheduleStatusBreakAndClass() {
        val cal = Calendar.getInstance()

        // Test during Class Period 1 (08:45)
        cal.set(Calendar.HOUR_OF_DAY, 8)
        cal.set(Calendar.MINUTE, 45)
        cal.set(Calendar.SECOND, 0)
        val statusClass1 = ClassScheduleManager.getCurrentStatus(cal)
        assertTrue(statusClass1 is ScheduleStatus.InClass)
        assertEquals(1, (statusClass1 as ScheduleStatus.InClass).period.periodNumber)
        assertFalse(ClassScheduleManager.isBreakTime(cal))

        // Test during Break between Period 1 and 2 (09:20)
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 20)
        val statusBreak1 = ClassScheduleManager.getCurrentStatus(cal)
        assertTrue(statusBreak1 is ScheduleStatus.InBreak)
        assertTrue(ClassScheduleManager.isBreakTime(cal))

        // Test during Class Period 2 (09:30)
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 30)
        val statusClass2 = ClassScheduleManager.getCurrentStatus(cal)
        assertTrue(statusClass2 is ScheduleStatus.InClass)
        assertEquals(2, (statusClass2 as ScheduleStatus.InClass).period.periodNumber)
        assertFalse(ClassScheduleManager.isBreakTime(cal))

        // Test during Break between Period 2 and 3 (10:15)
        cal.set(Calendar.HOUR_OF_DAY, 10)
        cal.set(Calendar.MINUTE, 15)
        val statusBreak2 = ClassScheduleManager.getCurrentStatus(cal)
        assertTrue(statusBreak2 is ScheduleStatus.InBreak)
        assertTrue(ClassScheduleManager.isBreakTime(cal))
    }
}
