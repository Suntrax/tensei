package com.blissless.tensei.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [ErrorHandler].
 *
 * Tests the error handling utility functions to ensure they behave
 * correctly and don't throw exceptions themselves.
 *
 * Note: These tests verify that the functions execute without throwing
 * and return the expected values. They don't verify log output (which
 * would require mocking android.util.Log).
 */
class ErrorHandlerTest {

    // ─── report ───────────────────────────────────────────────────────────

    @Test
    fun `report does not throw with message only`() {
        // Just verify it doesn't throw
        ErrorHandler.report("TestTag", "test message")
    }

    @Test
    fun `report does not throw with message and throwable`() {
        ErrorHandler.report("TestTag", "test message", RuntimeException("test"))
    }

    @Test
    fun `report does not throw with null throwable`() {
        ErrorHandler.report("TestTag", "test message", null)
    }

    @Test
    fun `report does not throw with empty message`() {
        ErrorHandler.report("TestTag", "")
    }

    @Test
    fun `report does not throw with empty tag`() {
        ErrorHandler.report("", "test message")
    }

    // ─── ignore ───────────────────────────────────────────────────────────

    @Test
    fun `ignore does not throw with message only`() {
        ErrorHandler.ignore("TestTag", "expected failure")
    }

    @Test
    fun `ignore does not throw with message and throwable`() {
        ErrorHandler.ignore("TestTag", "expected failure", RuntimeException("test"))
    }

    @Test
    fun `ignore does not throw with null throwable`() {
        ErrorHandler.ignore("TestTag", "expected failure", null)
    }

    // ─── reportAndNull ────────────────────────────────────────────────────

    @Test
    fun `reportAndNull returns result on success`() {
        val result = ErrorHandler.reportAndNull<String>("TestTag", "test") {
            "success"
        }
        assertThat(result).isEqualTo("success")
    }

    @Test
    fun `reportAndNull returns null on exception`() {
        val result = ErrorHandler.reportAndNull<String>("TestTag", "test") {
            throw RuntimeException("fail")
        }
        assertThat(result).isNull()
    }

    @Test
    fun `reportAndNull returns null when block returns null`() {
        val result = ErrorHandler.reportAndNull<String>("TestTag", "test") {
            null
        }
        assertThat(result).isNull()
    }

    @Test
    fun `reportAndNull handles nullable receiver types`() {
        val result = ErrorHandler.reportAndNull<Int>("TestTag", "test") {
            42
        }
        assertThat(result).isEqualTo(42)
    }

    // ─── reportOrDefault ──────────────────────────────────────────────────

    @Test
    fun `reportOrDefault returns result on success`() {
        val result = ErrorHandler.reportOrDefault("TestTag", "test", "default") {
            "success"
        }
        assertThat(result).isEqualTo("success")
    }

    @Test
    fun `reportOrDefault returns default on exception`() {
        val result = ErrorHandler.reportOrDefault("TestTag", "test", "default") {
            throw RuntimeException("fail")
        }
        assertThat(result).isEqualTo("default")
    }

    @Test
    fun `reportOrDefault returns default value type correctly`() {
        val result = ErrorHandler.reportOrDefault("TestTag", "test", 0) {
            42
        }
        assertThat(result).isEqualTo(42)
    }

    @Test
    fun `reportOrDefault returns default int on exception`() {
        val result = ErrorHandler.reportOrDefault("TestTag", "test", -1) {
            throw RuntimeException("fail")
        }
        assertThat(result).isEqualTo(-1)
    }

    // ─── reportAndBool ────────────────────────────────────────────────────

    @Test
    fun `reportAndBool returns true on success`() {
        val result = ErrorHandler.reportAndBool("TestTag", "test") {
            // no-op
        }
        assertThat(result).isTrue()
    }

    @Test
    fun `reportAndBool returns false on exception`() {
        val result = ErrorHandler.reportAndBool("TestTag", "test") {
            throw RuntimeException("fail")
        }
        assertThat(result).isFalse()
    }

    @Test
    fun `reportAndBool returns true when block completes without throwing`() {
        val result = ErrorHandler.reportAndBool("TestTag", "test") {
            val x = 1 + 1
            println(x)
        }
        assertThat(result).isTrue()
    }
}
