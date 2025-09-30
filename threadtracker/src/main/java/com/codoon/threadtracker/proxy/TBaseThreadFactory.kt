package com.codoon.threadtracker.proxy

import com.codoon.threadtracker.ThreadInfoManager
import com.codoon.threadtracker.bean.ThreadType
import java.util.concurrent.ThreadFactory


open class TBaseThreadFactory(
    private val threadFactory: ThreadFactory,
    private val poolName: String
) : ThreadFactory {
    override fun newThread(runnable: Runnable): Thread {
        // 注意这里面的runnable是被worker包装过的，已经不是用户传来的runnable
        val thread = threadFactory.newThread(runnable)
        val threadInfo = ThreadInfoManager.INSTANCE.recordThreadInfo(thread, ThreadType.POOL_THREAD, poolName = poolName)
        ThreadInfoManager.INSTANCE.recordThreadHistoryInfo(threadInfo)
        return thread
    }

    fun getReal(): ThreadFactory {
        return threadFactory
    }
}