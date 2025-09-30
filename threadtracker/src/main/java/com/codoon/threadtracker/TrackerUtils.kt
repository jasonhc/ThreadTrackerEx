package com.codoon.threadtracker

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Window
import android.view.WindowManager

object TrackerUtils {

    private const val MARKDOWN_INDENT_STR = "    "

    private data class TextStyleRule(val textRegex: String, val style: String)

    private val gTextStyleRules = arrayOf(
        TextStyleRule("\\w+@(\\w+)", "<span style=\"color:red\">"),
        TextStyleRule("threadIds: (\\[.+\\])", "<span style=\"color:red\">"),
        TextStyleRule("id: (\\d+)", "<span style=\"color:red\">"),
        TextStyleRule("startTime: (\\d+:\\d+:\\d+.\\d+)", "<span style=\"color:blue\">"),
        TextStyleRule("duration: (\\d+ms)", "<span style=\"color:blue\">")
    )

    // 获取线程池名称时使用
    @JvmStatic
    fun toObjectString(any: Any): String {
        // 防止线程池名称出现TBase类
        var className = any.javaClass.simpleName
        val simpleName = any.javaClass.simpleName
        if (simpleName == "TBaseThreadPoolExecutor") {
            className = any.javaClass.superclass?.simpleName ?: "ThreadPoolExecutor"
        } else if (simpleName == "TBaseScheduledThreadPoolExecutor") {
            className = any.javaClass.superclass?.simpleName ?: "ProxyScheduledThreadPoolExecutor"
        }
        return className + "@" + Integer.toHexString(any.hashCode())
    }

    @JvmStatic
    fun logStack() {
        Log.d(LOG_TAG, "logStack :\n")
        val stackElements =
            Throwable().stackTrace
        for (stackElement in stackElements) Log.d(
            LOG_TAG,
            "" + stackElement
        )
    }

    @JvmStatic
    fun getStackString(deleteProxy: Boolean = false): String {
        var str = ""
        val stackElements = Throwable().stackTrace
        var i = 0
        for (stackElement in stackElements) {
            i++
            if (deleteProxy && i <= 4 && stackElement.toString().contains("Proxy")) {
                // 前几行是动态代理的栈
                continue
            }
            if (stackElement.toString().contains("com.codoon.threadtracker")) {
                continue
            }
            str += stackElement.toString() + "\n"
        }
        return str
    }

    fun getThreadRunningStack(stacks: Array<StackTraceElement>): String {
        var str = ""
        for (stackElement in stacks) {
            if (stackElement.toString().contains("com.codoon.threadtracker"))
                continue
            str += stackElement.toString() + "\n"
        }
        return str
    }

    fun setStatusBarColor(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.argb(0xff, 0x00, 0xac, 0x61)
        }
    }

    fun addIndentSpace(stackStr: String): String {
        if (stackStr.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        val lines = stackStr.split("\n")
        for (line in lines) {
            sb.append("    ").append(line).append("\n")
        }
        return sb.toString()
    }

    // 将多行文本转换为Markdown格式的无序列表和段落文本, 第一行前面加上"-"字符, 后面的每一行前面加上缩进的空格
    // level  无序列表的缩进级别, 从1开始
    fun text2MarkdownListItem(text: String, level: Int = 1): String {
        val sb = StringBuilder()
        val lines = text.split("\n")

        for (i in lines.indices) {
            if (i == 0) {
                sb.append(MARKDOWN_INDENT_STR.repeat(level - 1))
                sb.append("- ").append(addMarkdownTextStyle(lines[i])).append("\n")
            } else {
                sb.append(MARKDOWN_INDENT_STR.repeat(level))
                sb.append(lines[i]).append("\n")
            }
        }
        return sb.toString()
    }

    // 把一行文本中的指定文字加上Markdown语法的字体或颜色
    private fun addMarkdownTextStyle(text: String) : String {
        var str = text
        for (rule in gTextStyleRules) {
            Regex(rule.textRegex).find(str)?.apply {
                val matchStart = range.last + 1 - groupValues[1].length
                val matchEnd = range.last
                // 每个匹配的字串都要改为粗体
                str = str.replaceRange(
                    matchStart,
                    matchEnd + 1,
                    "**" + rule.style + str.substring(matchStart, matchEnd + 1) + closeTagFor(rule.style) + "**"
                )
            }
        }

        return str
    }

    // TODO: 2025/4/30 在text中有多个匹配正则的字串的情况, 使用findAll来处理
    private fun addMarkdownTextStyleAll(text: String) : String {
        var str = text
        for (rule in gTextStyleRules) {
//                Regex(rule.textRegex).findAll(text).forEach { matchResult ->
//                    val matchStart = matchResult.range.last + 1 - matchResult.groupValues[1].length
//                    val matchEnd = matchResult.range.last
//                    str = str.replaceRange(matchStart, matchEnd, rule.style + text.substring(start, end) + "</span>")
//                }
        }

        return str
    }

    private fun closeTagFor(openTag: String): String {
        return if (openTag.startsWith("<")) {
            "</${openTag.substring(1, openTag.indexOfAny(charArrayOf(' ', '>')))}>"
        } else {
            openTag
        }
    }
}

fun Number.toPx(context: Context) = toPxF(context).toInt()

fun Number.toPxF(context: Context) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    this.toFloat(),
    context.resources.displayMetrics
)

fun Number.toDp(context: Context) = toDpF(context).toInt()

fun Number.toDpF(context: Context) =
    this.toFloat() * DisplayMetrics.DENSITY_DEFAULT / context.resources.displayMetrics.densityDpi

@JvmField
val LOG_TAG = "ThreadTracker"