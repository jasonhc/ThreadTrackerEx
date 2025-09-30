package com.codoon.threadtracker.proxy

import android.util.Log
import com.codoon.threadtracker.LOG_TAG
import com.codoon.threadtracker.ThreadInfoManager
import com.codoon.threadtracker.TrackerUtils
import com.codoon.threadtracker.bean.ThreadType
import java.util.*

open class TBaseTimer : Timer {
    constructor() : super() {
        init()
    }

    constructor(isDaemon: Boolean) : super(isDaemon) {
        init()
    }

    constructor(name: String) : super(name) {
        init()
    }

    constructor(name: String, isDaemon: Boolean) : super(name, isDaemon) {
        init()
    }

    private fun init() {
        var hasProxy = false
        try {
            val fields = javaClass.superclass?.declaredFields
            fields?.forEach {
                it.isAccessible = true
                val any = it.get(this)
                if (any is Thread && any.isAlive) {
                    hasProxy = true
                    any.apply {
                        ThreadInfoManager.INSTANCE.recordThreadInfo(
                            this,
                            ThreadType.TIMER,
                            TrackerUtils.getStackString(),
                            Thread.currentThread().id
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ProxyTimer err: ${e.message}")
        }
        if (!hasProxy) {
            Log.e(LOG_TAG, "ProxyTimer fail")
        }
    }
}