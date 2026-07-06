package com.blissless.tensei.util

import android.util.Log

/**
 * Centralized error handling and logging utility.
 *
 * Problem this solves: the codebase has 160+ empty catch blocks
 * (`catch (_: Exception) {}`) that silently swallow exceptions, making
 * debugging extremely difficult. Many of these hide real failures that
 * the user never sees and the developer never hears about.
 *
 * This object provides:
 *   - [report] — log an exception with context, for cases where you want
 *     a record but don't want to crash
 *   - [ignore] — explicitly document that an exception is expected and
 *     safe to swallow (replaces `catch (_: Exception) {}` with intent)
 *   - [reportAndNull] / [reportOrDefault] — convenience wrappers for the
 *     common `try { ... } catch (e) { Log.e(...); null }` pattern
 *
 * Usage:
 *   try {
 *       repository.fetchX()
 *   } catch (e: Exception) {
 *       ErrorHandler.report(TAG, "fetchX failed", e)
 *   }
 *
 *   // When the exception is expected and safe:
 *   try { h.pause() } catch (e: Exception) {
 *       ErrorHandler.ignore(TAG, "pause failed (best-effort cleanup)", e)
 *   }
 *
 *   // Common pattern: try-or-null with logging
 *   val result = ErrorHandler.reportAndNull(TAG, "fetchY") { repository.fetchY() }
 */
object ErrorHandler {

    /**
     * Report an unexpected exception. Always logs at ERROR level.
     *
     * In the future this could also:
     *   - Send to Crashlytics / Sentry
     *   - Increment a counter in analytics
     *   - Show a toast for user-facing errors
     *
     * @param tag      Log tag, usually the calling class name or a constant
     * @param message  Human-readable description of what was being attempted
     * @param throwable The exception that was caught (optional)
     */
    fun report(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    /**
     * Document that an exception is expected and safe to ignore.
     *
     * Use this instead of `catch (_: Exception) {}` to make intent explicit.
     * Logs at DEBUG level so it can be turned on for diagnosis but doesn't
     * spam the logcat at default log levels.
     *
     * @param tag      Log tag
     * @param reason   Why this exception is safe to ignore
     * @param throwable The exception (optional)
     */
    fun ignore(tag: String, reason: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.d(tag, "$reason (ignored: ${throwable.message})")
        } else {
            Log.d(tag, reason)
        }
    }

    /**
     * Run a block and return its result, or null if it throws.
     * The exception is logged at ERROR level with context.
     *
     * Equivalent to:
     *   try { block() } catch (e: Exception) { Log.e(tag, msg, e); null }
     *
     * @param tag  Log tag
     * @param what Description of what the block does (for the error message)
     * @param block The code to run
     * @return The block's result, or null on exception
     */
    inline fun <T> reportAndNull(tag: String, what: String, block: () -> T?): T? {
        return try {
            block()
        } catch (e: Exception) {
            report(tag, what, e)
            null
        }
    }

    /**
     * Run a block and return its result, or [default] if it throws.
     * The exception is logged at ERROR level with context.
     *
     * @param tag     Log tag
     * @param what    Description of what the block does
     * @param default Value to return on exception
     * @param block   The code to run
     * @return The block's result, or [default] on exception
     */
    inline fun <T> reportOrDefault(tag: String, what: String, default: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            report(tag, what, e)
            default
        }
    }

    /**
     * Run a block and return true on success, false on exception.
     * The exception is logged at ERROR level with context.
     *
     * @param tag  Log tag
     * @param what Description of what the block does
     * @param block The code to run
     * @return true if the block completed without throwing, false otherwise
     */
    inline fun reportAndBool(tag: String, what: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            report(tag, what, e)
            false
        }
    }
}
