package com.ventus.apisearch.ui

import java.awt.Color

internal object MethodColors {

    fun of(httpMethod: String): Color {
        val first = httpMethod.substringBefore(',').trim().uppercase()
        return when (first) {
            "GET" -> Color(0x2E, 0x7D, 0x32)
            "POST" -> Color(0xE6, 0x51, 0x00)
            "PUT" -> Color(0x15, 0x65, 0xC0)
            "DELETE" -> Color(0xC6, 0x28, 0x28)
            "PATCH" -> Color(0x6A, 0x1B, 0x9A)
            "HEAD", "OPTIONS" -> Color(0x00, 0x83, 0x8F)
            else -> Color(0x61, 0x61, 0x61)
        }
    }
}
