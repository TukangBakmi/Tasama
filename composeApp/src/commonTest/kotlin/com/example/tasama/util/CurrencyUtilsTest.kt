package com.example.tasama.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyUtilsTest {

    @Test
    fun testFormatAmount() {
        assertEquals("1.000", 1000L.formatAmount())
        assertEquals("1.000.000", 1000000L.formatAmount())
        assertEquals("500", 500L.formatAmount())
        assertEquals("12.345.678", 12345678L.formatAmount())
    }

    @Test
    fun testFormatCurrency() {
        assertEquals("Rp 1.000", 1000L.formatCurrency("IDR"))
        assertEquals("$1.000", 1000L.formatCurrency("USD"))
        assertEquals("€1.000", 1000L.formatCurrency("EUR"))
        assertEquals("¥1.000", 1000L.formatCurrency("JPY"))
    }

    @Test
    fun testFormatShortAmount() {
        assertEquals("1.0K", 1000L.formatShortAmount())
        assertEquals("1.5K", 1500L.formatShortAmount())
        assertEquals("1.0M", 1000000L.formatShortAmount())
        assertEquals("1.2M", 1200000L.formatShortAmount())
        assertEquals("1.0B", 1000000000L.formatShortAmount())
        assertEquals("500", 500L.formatShortAmount())
    }
}
