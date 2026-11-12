package com.ventus.apisearch.parsing

import org.junit.Assert.assertEquals
import org.junit.Test

class DocCommentCleanerTest {

    @Test
    fun `strips javadoc markers`() {
        assertEquals("用户列表", DocCommentCleaner.clean("/**\n * 用户列表\n */"))
    }

    @Test
    fun `joins lines and drops tags`() {
        assertEquals("查询用户", DocCommentCleaner.clean("/**\n * 查询用户\n * @param id 主键\n */"))
    }

    @Test
    fun `returns empty for null`() {
        assertEquals("", DocCommentCleaner.clean(null))
    }
}
