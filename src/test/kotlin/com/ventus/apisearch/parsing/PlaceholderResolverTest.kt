package com.ventus.apisearch.parsing

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceholderResolverTest {

    private fun resolve(value: String, props: Map<String, String> = emptyMap()): String =
        PlaceholderResolver.resolve(value, props, { null }, { null })

    @Test
    fun `uses default value when variable is missing`() {
        assertEquals("8080", resolve("\${SERVER_PORT:8080}"))
    }

    @Test
    fun `resolves from configuration properties`() {
        assertEquals("9090", resolve("\${server.port}", mapOf("server.port" to "9090")))
    }

    @Test
    fun `resolves nested properties`() {
        val props = mapOf("base.port" to "8081", "server.port" to "\${base.port}")
        assertEquals("8081", resolve("\${server.port}", props))
    }

    @Test
    fun `prefers environment over default`() {
        val resolved = PlaceholderResolver.resolve(
            "\${PORT:8080}",
            emptyMap(),
            { key -> if (key == "PORT") "7070" else null },
            { null },
        )
        assertEquals("7070", resolved)
    }

    @Test
    fun `leaves plain text untouched`() {
        assertEquals("/api", resolve("/api"))
    }
}
