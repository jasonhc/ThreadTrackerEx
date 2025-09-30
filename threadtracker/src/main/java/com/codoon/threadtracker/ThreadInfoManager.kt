package com.codoon.threadtracker

import android.content.Context
import android.util.Log
import com.codoon.threadtracker.bean.ShowInfo
import com.codoon.threadtracker.bean.ThreadHistoryInfo
import com.codoon.threadtracker.bean.ThreadInfo
import com.codoon.threadtracker.bean.ThreadInfoResult
import com.codoon.threadtracker.bean.ThreadPoolInfo
import com.codoon.threadtracker.bean.ThreadType
import java.io.File
import java.util.*

private const val TAG = "ThreadInfoManager"

class ThreadInfoManager private constructor() {

    companion object {
        @JvmStatic
        val INSTANCE: ThreadInfoManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { ThreadInfoManager() }
    }

    // 随时在更新
    private val threadInfo = HashMap<Long, ThreadInfo>()
    private val threadPoolInfo = HashMap<String, ThreadPoolInfo>()

    // 线程历史记录
    private val threadHistory = ArrayList<ThreadHistoryInfo>()

    // 用于展示页面获取细节，只在buildAllThreadInfo时更新
    private val lastThreadInfo = HashMap<Long, ThreadInfo>()
    private val lastThreadPoolInfo = HashMap<String, ThreadPoolInfo>()

    @Synchronized
    fun recordThread(thread: Thread, type: ThreadType, callStack: String) {
        // TODO: 2025/4/16 为了判断旧的逻辑是否有用而添加的log
        val info = getThreadInfoById(thread.id)
        info?.also {
            if (it.callStack.isNotEmpty()) { // 如果来自线程池，callStack意义为任务添加栈，可能已经有值了，不能更新为start调用栈
                Log.w(TAG,
                    "HCDBG, 如果来自线程池，callStack意义为任务添加栈，可能已经有值了，不能更新为start调用栈, thread id: ${thread.id}, name: ${thread.name}, callStack: ${TrackerUtils.getStackString()}")
            }
        }

        val threadInfo = recordThreadInfo(
            thread,
            type,
            callStack,
//            TrackerUtils.getStackString(),
            Thread.currentThread().id
        )
        recordThreadHistoryInfo(threadInfo)
    }

    @Synchronized
    fun recordThreadInfo(
        thread: Thread, type: ThreadType, callStack: String = "", callThreadId: Long = 0, poolName: String? = null
    ) : ThreadInfo {
        var info = threadInfo[thread.id]
        info = (info ?: ThreadInfo()).apply {
            id = thread.id
            name = thread.name
            state = thread.state
            this.type = type
            this.callStack = callStack
            this.poolName = poolName
            this.callThreadId = callThreadId
            startTime = System.currentTimeMillis()
        }
        putThreadInfo(thread.id, info)

        return info
    }

    @Synchronized
    fun putThreadInfo(threadId: Long, info: ThreadInfo) {
        threadInfo[threadId] = info
    }

    @Synchronized
    fun recordThreadHistoryInfo(info: ThreadInfo) {
        threadHistory.add(ThreadHistoryInfo.fromThreadInfo(info))
    }

    @Synchronized
    fun recordThreadEnd(thread: Thread, threadType: ThreadType) {
        recordThreadEndTime(thread, threadType)
        removeThreadInfo(thread.id)
    }

    @Synchronized
    fun recordThreadEndTime(thread: Thread, threadType: ThreadType) {
        val threadId = thread.id
        val threadNum = threadHistory.size
        var found = false

        for (i in threadNum-1 downTo 0) {
            if (threadHistory[i].id == threadId && threadHistory[i].type == threadType) {
                if (threadHistory[i].endTime == -1L) {
                    threadHistory[i].endTime = System.currentTimeMillis()
                } else {
                    Log.w(TAG, "recordThreadEndTime: thread $threadId already has endTime")
                }
                found = true
                break
            }
        }

        if (!found) {
            Log.w(TAG, "recordThreadEndTime: thread $threadId not found in thread history")
        }
    }

    @Synchronized
    fun removeThreadInfo(threadId: Long) {
        threadInfo.remove(threadId)
    }

    @Synchronized
    fun getThreadInfoById(threadId: Long): ThreadInfo? {
        return threadInfo[threadId]
    }

    fun getLastThreadInfoById(threadId: Long): ThreadInfo? {
        return lastThreadInfo[threadId]
    }


    fun getLastThreadPoolInfoByName(poolName: String?): ThreadPoolInfo? {
        return poolName?.let { lastThreadPoolInfo[it] }
    }

    fun shutDownPool(poolName: String) {
        threadPoolInfo[poolName]?.shutDown = true
    }

    fun removePool(poolName: String) {
        threadPoolInfo.remove(poolName)
    }

    fun putThreadPoolInfo(poolName: String, pool: ThreadPoolInfo) {
        threadPoolInfo[poolName] = pool
    }

    // 获取当前所有线程并和已保存线程信息比较、融合，返回用于展示的结果
    @Synchronized
    fun buildAllThreadInfo(log: Boolean = false): ThreadInfoResult {

        // 暂时规定必须在TrackerActivity刷新线程中
//        if (Thread.currentThread().name != "ThreadTracker-Refresh") {
//            throw RuntimeException("not in ThreadTracker-Refresh thread")
//        }
        val ignoreId = Thread.currentThread().id

        val threadInfoResult = ThreadInfoResult()

        // 初始化一些字段
        val values = threadInfo.values.iterator()
        while (values.hasNext()) {
            values.next().hit = ThreadInfo.HIT_NO
        }

        val poolValues = threadPoolInfo.values.iterator()
        while (poolValues.hasNext()) {
            poolValues.next().threadIds.clear()
        }

        // 获取当前所有线程
        val threadMap = Thread.getAllStackTraces()
        for ((thread, stackElements) in threadMap) {
            if (thread.id == ignoreId)
                continue
            var info = threadInfo[thread.id]
            // info存在就更新不算在就新建
            info = (info ?: ThreadInfo()).apply {
                hit = ThreadInfo.HIT_YES // 已被找到，表示此线程信息对应线程当前正在活动
                id = thread.id
                name = thread.name // 这个name可以随时改 这里更新下
                state = thread.state
                poolName?.also { poolName ->
                    // 在线程池信息中查出poolName对应的线程池，并把此线程id添加进去，建立线程池与线程关系
                    val pool = threadPoolInfo[poolName]
                    pool?.threadIds?.add(id)
                }
                runningStack = TrackerUtils.getThreadRunningStack(stackElements)
            }
            putThreadInfo(thread.id, info)
        }

        lastThreadInfo.clear()
        lastThreadPoolInfo.clear()

        // 清除没有被命中的thread，将剩下的复制到lastThreadInfo中
        val afterValues = threadInfo.values.iterator()
        while (afterValues.hasNext()) {
            val it = afterValues.next()
            if (it.hit == ThreadInfo.HIT_NO) {
                afterValues.remove()
            } else {
                lastThreadInfo[it.id] = it.copy()
            }
        }

        // 清除threadIds为空并且shutdown的pool，将剩下的复制到lastThreadPoolInfo中
        val afterPoolValues = threadPoolInfo.values.iterator()
        while (afterPoolValues.hasNext()) {
            afterPoolValues.next().apply {
                if (threadIds.isEmpty() && shutDown) {
                    afterPoolValues.remove()
                } else {
                    val newInfo = this.copy()
                    newInfo.threadIds = mutableListOf()
                    this.threadIds.forEach {
                        newInfo.threadIds.add(it)
                    }
                    lastThreadPoolInfo[poolName] = newInfo
                }
            }
        }

        if (log) print()

        // 生成listShowInfo供列表显示
        val listShowInfo = mutableListOf<ShowInfo>()
        lastThreadInfo.forEach {
            if (it.value.poolName.isNullOrEmpty() && it.value.callStack.isNotEmpty()) {
                // 独立的、有调用栈的线程
                listShowInfo.add(ShowInfo().apply {
                    threadId = it.value.id
                    threadName = it.value.name
                    threadState = it.value.state
                    type = ShowInfo.SINGLE_THREAD
                })
            }
        }
        lastThreadPoolInfo.forEach {
            if (it.value.threadIds.isNotEmpty()) { // 忽略没有线程在运行的线程池
                listShowInfo.add(ShowInfo().apply {
                    poolName = it.value.poolName
                    type = ShowInfo.POOL
                })
                it.value.threadIds.forEach { id ->
                    lastThreadInfo[id]?.also {
                        listShowInfo.add(ShowInfo().apply {
                            threadId = it.id
                            threadName = it.name
                            threadState = it.state
                            poolName = it.poolName
                            type = ShowInfo.POOL_THREAD
                        })
                    }
                }
            }
        }
        lastThreadInfo.forEach {
            if (it.value.poolName == null && it.value.callStack.isEmpty()) {
                // 未知、系统线程
                listShowInfo.add(ShowInfo().apply {
                    threadId = it.value.id
                    threadName = it.value.name
                    threadState = it.value.state
                    type = ShowInfo.SINGLE_THREAD
                })
                threadInfoResult.unknownNum++
            }
        }

        threadInfoResult.list = listShowInfo
        threadInfoResult.totalNum = threadMap.size - 1 // 不算当前线程

        return threadInfoResult
    }

    private fun print() {
//        Log.d(LOG_TAG, "\n\n—————————————————thread———————————————")
//        val keys = threadInfo.keys()
//        while (keys.hasMoreElements()) {
//            threadInfo[keys.nextElement()]?.apply {
//                Log.d(LOG_TAG, "${toString()}\n")
//            }
//        }
//
//        Log.d(LOG_TAG, "\n\n—————————————————pool—————————————————")
//        val poolKeys = threadPoolInfo.keys()
//        while (poolKeys.hasMoreElements()) {
//            threadPoolInfo[poolKeys.nextElement()]?.apply {
//                if (threadIds.isNotEmpty()) {
//                    Log.d(LOG_TAG, "${toString()}\n")
//                }
//            }
//        }
    }

    @Synchronized
    fun dumpThreadHistoryInfo(context: Context) {
        val allThreadInfo = buildAllThreadInfo()

        val dumpFile = getNextDumpFile(context)

        dumpFile.writeText("\n\n—————————————————thread history———————————————  total number: ${threadHistory.size}\n")
        threadHistory.forEach {
            dumpFile.appendText("# ${it.dump()}\n")
        }
        dumpFile.appendText("\n\n—————————————————thread history (with stack)———————————————  total number: ${threadHistory.size}\n")
        threadHistory.forEach {
            dumpFile.appendText("# ${it.fullDump()}\n")
        }

        // 把threadInfo列表按照id排序，方便查看
        val allSortedThreadInfo = ArrayList<ThreadInfo>(threadInfo.values)
        Collections.sort(allSortedThreadInfo, object : Comparator<ThreadInfo> {
            override fun compare(o1: ThreadInfo?, o2: ThreadInfo?): Int {
                if (o1 == null || o2 == null) {
                    return 0
                }
                return (o1.id - o2.id).toInt()
            }
        })
        dumpFile.appendText("\n\n—————————————————current threads———————————————  total number: ${allSortedThreadInfo.size}\n")
        allSortedThreadInfo.forEach {
            dumpFile.appendText("# ${it.dump()}\n")
        }
        dumpFile.appendText("\n\n—————————————————current threads (with stack)———————————————  total number: ${allSortedThreadInfo.size}\n")
        allSortedThreadInfo.forEach {
            dumpFile.appendText("# ${it.fullDump()}\n")
        }

        dumpFile.appendText("\n\n—————————————————pool————————————————— total number: ${threadPoolInfo.size}\n")
        threadPoolInfo.values.forEach {
            if (it.threadIds.isNotEmpty()) {
                dumpFile.appendText("# ${it.dump()}\n")
            }
        }

        dumpFile.appendText("\n\n—————————————————pool (no threads) ————————————————— total number: ${threadPoolInfo.size}\n")
        threadPoolInfo.values.forEach {
            if (it.threadIds.isEmpty()) {
                dumpFile.appendText("# ${it.dump()}\n")
            }
        }

        toMarkdownFile(dumpFile).writeText(dumpThreadPoolInfoMarkDown())
    }

    private fun dumpThreadPoolInfoMarkDown() : String {
        val info = StringBuilder()

        info.append("## —————————————————thread pool———————————————  total number: ${threadPoolInfo.size}\n")
        threadPoolInfo.values.forEach {
            if (it.threadIds.isNotEmpty()) {
                info.append(TrackerUtils.text2MarkdownListItem(it.fullDumpForMarkDown()))

                it.threadIds.forEach { id ->
                    info.append(dumpThreadHistoryInfoMarkDown(id))
                }
            }
        }

        info.append("## —————————————————thread pool (no threads) ————————————————— total number: ${threadPoolInfo.size}\n")
        threadPoolInfo.values.forEach {
            if (it.threadIds.isEmpty()) {
                info.append(TrackerUtils.text2MarkdownListItem(it.fullDumpForMarkDown()))
            }
        }

        return info.toString()
    }

    private fun dumpThreadHistoryInfoMarkDown(threadId: Long) : String {
        val info = StringBuilder()

        threadHistory.filter { it.id == threadId }.forEach {
            info.append(TrackerUtils.text2MarkdownListItem(it.fullDumpForMarkDown(), 2))
        }

        return info.toString()
    }

    fun dumpThreadInfo(context: Context) {
        val allThreadInfo = buildAllThreadInfo()

        val dumpFile = getNextDumpFile(context)

        // 把threadInfo列表按照id排序，方便查看
        val allSortedThreadInfo = ArrayList<ThreadInfo>(threadInfo.values)
        Collections.sort(allSortedThreadInfo, object : Comparator<ThreadInfo> {
            override fun compare(o1: ThreadInfo?, o2: ThreadInfo?): Int {
                if (o1 == null || o2 == null) {
                    return 0
                }
                return (o1.id - o2.id).toInt()
            }
        })
        dumpFile.writeText("\n\n—————————————————thread———————————————  total number: ${allThreadInfo.totalNum}\n")
        allSortedThreadInfo.forEach {
            dumpFile.appendText("# ${it.dump()}\n")
        }
        dumpFile.appendText("\n\n—————————————————thread (with stack)———————————————  total number: ${allThreadInfo.totalNum}\n")
        allSortedThreadInfo.forEach {
            dumpFile.appendText("# ${it.fullDump()}\n")
        }

        dumpFile.appendText("\n\n—————————————————pool————————————————— total number: ${threadPoolInfo.size}\n")
        threadPoolInfo.values.forEach {
            if (it.threadIds.isNotEmpty()) {
                dumpFile.appendText("# ${it.dump()}\n")
            }
        }

        dumpFile.appendText("\n\n—————————————————pool (no threads) ————————————————— total number: ${threadPoolInfo.size}\n")
        threadPoolInfo.values.forEach {
            if (it.threadIds.isEmpty()) {
                dumpFile.appendText("# ${it.dump()}\n")
            }
        }
    }

    private fun getNextDumpFile(context: Context) : File {
        // 首先尝试thread_dump_1.txt, 如果已存在, 再尝试thread_dump_2.txt, 以此类推
        var dumpFile : File
        var index = 1
        do {
            dumpFile = File(context.filesDir.absolutePath, "/thread_dump_${index}.txt")
            index++
        } while (dumpFile.exists())

        return dumpFile
    }

    // 根据thread_dump_x.txt文件的名字生成Markdown文件名thread_dump_x.md
    private fun toMarkdownFile(textFile: File) : File {
        val fileName = textFile.nameWithoutExtension
        return File(textFile.parent, "$fileName.md")
    }
}