package com.codoon.threadtracker.bean

/**
 * @author hechuan1 on 2025/4/18.
 */
enum class ThreadType(private val typeStr : String) {
    NORMAL("Normal"),
    POOL_THREAD("PoolThread"),
    POOL_TASK("PoolTask"),
    TIMER("Timer"),
    HANDLER_THREAD("HandlerThread");

    override fun toString() = typeStr
}