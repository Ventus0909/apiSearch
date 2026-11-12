package com.ventus.apisearch.parsing

import org.junit.Assert.assertEquals
import org.junit.Test

class PathJoinerTest {

    @Test
    fun `joins nested segments`() {
        assertEquals("/api/user/list", PathJoiner.join("/api", "/user/", "list"))
    }

    @Test
    fun `returns root for empty input`() {
        assertEquals("/", PathJoiner.join(null, "", "/"))
    }

    @Test
    fun `keeps path variables and wildcards`() {
        assertEquals("/api/user/{id}/**", PathJoiner.join("/api/", "/user/{id}/", "**"))
    }

    @Test
    fun `combines class and method paths`() {
        assertEquals(
            listOf("/api/a", "/api/b"),
            PathJoiner.combine(listOf("/api"), listOf("/a", "/b")),
        )
    }

    @Test
    fun `combines when class path is missing`() {
        assertEquals(listOf("/a"), PathJoiner.combine(emptyList(), listOf("/a")))
    }
}
