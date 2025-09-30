package com.codoon.threadtracker.proxy

import com.codoon.threadtracker.ThreadInfoManager
import com.codoon.threadtracker.bean.ThreadInfo
import com.codoon.threadtracker.bean.ThreadType
import java.util.concurrent.Callable

/**
 * 静态代理线程池中的Runnable、Callable，时机一般是调用添加Runnable、Callable任务的方法时。这样PoolRunnableAndOther就可以获得调用栈与线程池名，并且可以在call或run时与当前线程建立联系
 * 有些类会同时继承Runnable、Callable，比如rxjava2，或同时继承Runnable、Comparable，比如使用PriorityBlockingQueue的线程池。为了避免类型转换失败，这里继承了已知所有接口，如有相关crash，可继续在这里添加接口
 */
class PoolRunnableAndOther constructor(
    private val any: Any,
    private val callStack: String,
    private val poolName: String? = null
) : Runnable, Callable<Any>, Comparable<Any> {
    private val callThreadId = Thread.currentThread().id

    override fun run() {
        val thread = Thread.currentThread()
        val info = updateThreadInfo(thread)

        (any as Runnable).run()

        // 任务已执行结束，callStack表示任务添加栈，此时应为空代表线程当前无任务在运行
        info.callStack = ""
        ThreadInfoManager.INSTANCE.recordThreadEndTime(thread, ThreadType.POOL_TASK)
    }

    override fun call(): Any {
        val thread = Thread.currentThread()
        val info = updateThreadInfo(thread)

        val v = (any as Callable<Any>).call()

        info.callStack = ""
        ThreadInfoManager.INSTANCE.recordThreadEndTime(thread, ThreadType.POOL_TASK)
        return v
    }

    private fun updateThreadInfo(thread: Thread): ThreadInfo {
        val threadInfo = ThreadInfoManager.INSTANCE.recordThreadInfo(
            thread,
            ThreadType.POOL_TASK,
            callStack,
            callThreadId,
            poolName
        )
        ThreadInfoManager.INSTANCE.recordThreadHistoryInfo(threadInfo)
        return threadInfo
    }

    override fun compareTo(other: Any): Int {
        if (any is Comparable<*> && other is PoolRunnableAndOther) {
            val c = (any as Comparable<Any>)
            // return c.compareTo(other)
            return c.compareTo(other.getReal())
        }
        return 0
    }

    fun getReal(): Any {
        return any
    }
}
