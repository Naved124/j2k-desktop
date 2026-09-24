package android.util

import java.io.PrintWriter
import java.io.StringWriter

/** android.util.Log printing to the terminal. */
object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    @JvmStatic fun v(tag: String?, msg: String?): Int = println(VERBOSE, tag, msg)
    @JvmStatic fun v(tag: String?, msg: String?, tr: Throwable?): Int = println(VERBOSE, tag, withTrace(msg, tr))
    @JvmStatic fun d(tag: String?, msg: String?): Int = println(DEBUG, tag, msg)
    @JvmStatic fun d(tag: String?, msg: String?, tr: Throwable?): Int = println(DEBUG, tag, withTrace(msg, tr))
    @JvmStatic fun i(tag: String?, msg: String?): Int = println(INFO, tag, msg)
    @JvmStatic fun i(tag: String?, msg: String?, tr: Throwable?): Int = println(INFO, tag, withTrace(msg, tr))
    @JvmStatic fun w(tag: String?, msg: String?): Int = println(WARN, tag, msg)
    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int = println(WARN, tag, withTrace(msg, tr))
    @JvmStatic fun w(tag: String?, tr: Throwable?): Int = println(WARN, tag, withTrace(null, tr))
    @JvmStatic fun e(tag: String?, msg: String?): Int = println(ERROR, tag, msg)
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int = println(ERROR, tag, withTrace(msg, tr))
    @JvmStatic fun wtf(tag: String?, msg: String?): Int = println(ASSERT, tag, msg)
    @JvmStatic fun wtf(tag: String?, msg: String?, tr: Throwable?): Int = println(ASSERT, tag, withTrace(msg, tr))
    @JvmStatic fun isLoggable(tag: String?, level: Int): Boolean = level >= DEBUG

    @JvmStatic
    fun getStackTraceString(tr: Throwable?): String {
        if (tr == null) return ""
        val sw = StringWriter()
        tr.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    @JvmStatic
    fun println(priority: Int, tag: String?, msg: String?): Int {
        val level = "??VDIWEA".getOrElse(priority) { '?' }
        val line = "[ext] $level/$tag: $msg"
        if (priority >= WARN) System.err.println(line) else kotlin.io.println(line)
        return line.length
    }

    private fun withTrace(msg: String?, tr: Throwable?): String =
        listOfNotNull(msg, tr?.let { getStackTraceString(it) }).joinToString("\n")
}
