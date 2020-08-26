package org.randomcat.agorabot

import kotlin.test.Test
import kotlin.test.assertEquals

class SplitArgumentsTest {
    @Test
    fun `empty string`() {
        assertEquals(listOf(), splitArguments(""))
    }

    @Test
    fun `simple string splits on spaces`() {
        assertEquals(
            listOf("this", "is", "a", "test"),
            splitArguments("this is a test")
        )
    }

    @Test
    fun `string with double spaces`() {
        assertEquals(
            listOf("this", "is", "a", "test"),
            splitArguments("this  is a   test")
        )
    }

    @Test
    fun `string with quotes`() {
        assertEquals(
            listOf("this", "is a", "test"),
            splitArguments("this \"is a\" test")
        )
    }

    @Test
    fun `string with escaped quotes`() {
        assertEquals(
            listOf("this", "is\"a", "test"),
            splitArguments("this \"is\\\"a\" test")
        )
    }

    @Test
    fun `string with internal quotes`() {
        assertEquals(
            listOf("this", "is", "a", "test"),
            splitArguments("th\"is\" is a test")
        )
    }

    @Test
    fun `string with random escapes`() {
        assertEquals(
            listOf("this", "is a", "test"),
            splitArguments("t\\his i\\s\\ a test")
        )
    }

    @Test
    fun `string with empty quoted argument`() {
        assertEquals(
            listOf("this", "", "is", "", "a", "test"),
            splitArguments("this \"\" is \"\" a test")
        )
    }
}