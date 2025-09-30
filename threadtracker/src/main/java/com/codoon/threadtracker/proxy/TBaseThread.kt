package com.codoon.threadtracker.proxy

import com.codoon.threadtracker.ThreadInfoManager
import com.codoon.threadtracker.TrackerUtils
import com.codoon.threadtracker.bean.ThreadType

/**
 * 如果有人自定义Thread，理论上一定会调用super，而继承通过字节码改成ProxyThread，因为调用了super所以这里也能调用到
 * start方法也可能是来自线程池，所以putThreadInfo时要先查找，以免用空的poolName覆盖原有poolName
 * 比如以下调用栈
 * com.codoon.threadtracker.proxy.ProxyThread.start(ProxyThread.kt:31)
 * java.util.concurrent.ThreadPoolExecutor.addWorker(ThreadPoolExecutor.java:970)
 * java.util.concurrent.ThreadPoolExecutor.ensurePrestart(ThreadPoolExecutor.java:1611)
 * java.util.concurrent.ScheduledThreadPoolExecutor.delayedExecute(ScheduledThreadPoolExecutor.java:342)
 * java.util.concurrent.ScheduledThreadPoolExecutor.scheduleWithFixedDelay(ScheduledThreadPoolExecutor.java:629)
 */
open class TBaseThread : Thread {
    internal constructor() : super()
    internal constructor(runnable: Runnable?) : super(runnable)
    internal constructor(group: ThreadGroup?, target: Runnable?) : super(group, target)
    internal constructor(group: ThreadGroup?, name: String) : super(group, name)
    internal constructor(target: Runnable?, name: String) : super(target, name)
    internal constructor(group: ThreadGroup?, target: Runnable?, name: String) : super(
        group,
        target,
        name
    )

    internal constructor(
        group: ThreadGroup?,
        target: Runnable?,
        name: String,
        stackSize: Long
    ) : super(group, target, name, stackSize)

    @Synchronized
    override fun start() {
        super.start()

        ThreadInfoManager.INSTANCE.recordThread(this, ThreadType.NORMAL, TrackerUtils.getStackString())
    }

    override fun run() {
        super.run()

        ThreadInfoManager.INSTANCE.recordThreadEnd(this, ThreadType.NORMAL)
    }
}