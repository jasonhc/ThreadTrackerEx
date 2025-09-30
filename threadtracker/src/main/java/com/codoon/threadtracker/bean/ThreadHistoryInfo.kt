package com.codoon.threadtracker.bean

import com.codoon.threadtracker.TrackerUtils
import java.text.SimpleDateFormat

/**
 * 线程信息历史记录
 */
data class ThreadHistoryInfo(
    var id: Long = -1L,
    var name: String = "",
    var type: ThreadType,
    var callStack: String = "", // 如果是单个线程，则是start被调用堆栈，如果是线程池中线程，此字段意义为当前正在执行的task被添加的栈。因task执行完马上被置空，后续可以考虑记录最近一次任务的添加栈信息
    var callThreadId: Long = -1L, // 被调用/添加时所处线程id，方便查看调用链
    var poolName: String? = null, // 此线程所属线程池，没有就是null
    var startTime: Long = -1L, // 线程start的cpu时间，用于计算线程运行时间。线程池中的线程无此信息
    var endTime: Long = -1L // 线程结束的cpu时间，用于计算线程运行时间。线程池中的线程无此信息
) {

    companion object {
        fun fromThreadInfo(info: ThreadInfo): ThreadHistoryInfo {
            return ThreadHistoryInfo(
                id = info.id,
                name = info.name,
                type = info.type,
                callStack = info.callStack,
                callThreadId = info.callThreadId,
                poolName = info.poolName,
                startTime = info.startTime)
        }
    }

    fun fullDump() : String {
        return dump() + "\n" +
                "  * callStack: \n${TrackerUtils.addIndentSpace(callStack)}"
    }

    fun fullDumpForMarkDown() : String {
        val info = StringBuilder()
        info.append(dump()).append("\n")
            .append("callStack: \n" +
                "```" + "\n" +
                callStack +
                "```" + "\n" )

        return info.toString()
    }

    fun dump() : String {
        val execDuration : String = if (endTime > 0) {
            "${endTime - startTime}ms"
        } else {
            "N/A"
        }

        return "id: $id, name: $name, type: ${type}, callThreadId: $callThreadId, poolName: $poolName, startTime: ${formatTime(startTime)}, duration: $execDuration"
    }

    // System.currentTimeMillis()的值转换为"HH:mm:ss.SSS"格式
    private fun formatTime(time: Long): String {
        val date = java.util.Date(time)
        return SimpleDateFormat("HH:mm:ss.SSS").format(date)
    }
}