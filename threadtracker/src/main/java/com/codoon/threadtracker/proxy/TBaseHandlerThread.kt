package com.codoon.threadtracker.proxy

import android.os.HandlerThread
import com.codoon.threadtracker.ThreadInfoManager
import com.codoon.threadtracker.TrackerUtils
import com.codoon.threadtracker.bean.ThreadType

/**
 * 因HandlerThread继承自Thread，所以复制ProxyThread方法
 */
open class TBaseHandlerThread : HandlerThread {
    constructor(name: String) : super(name)

    constructor(name: String, priority: Int) : super(name, priority)

    @Synchronized
    override fun start() {
        super.start()

        ThreadInfoManager.INSTANCE.recordThread(this, ThreadType.HANDLER_THREAD, TrackerUtils.getStackString())
    }

    override fun run() {
        super.run()

        ThreadInfoManager.INSTANCE.recordThreadEnd(this, ThreadType.HANDLER_THREAD)
    }
}