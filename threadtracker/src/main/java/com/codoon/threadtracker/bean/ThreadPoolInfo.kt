package com.codoon.threadtracker.bean

import android.util.Log
import com.codoon.threadtracker.TrackerUtils
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * 线程池信息
 * 在获取当前所有线程时会根据SingleThreadInfo#poolName填写threadIds
 * 填写后如果threadIds为空并且已经被调用过shutDown，则清除此对象
 */
data class ThreadPoolInfo(
    var poolName: String = "",
    var createStack: String = "", // 线程池对象创建栈
    var createThreadId: Long = -1L, // 被创建时所处线程id
    var threadIds: MutableList<Long> = mutableListOf(), // 包含的线程，在获取当前所有线程信息时填写
    var shutDown: Boolean = false, // 是否已被调用shutDown或shutDownNow
    var poolObj: Executor? = null // 线程池对象
) {

    companion object {
        const val TAG = "ThreadPoolInfo"
    }

    fun dump() : String {
        return "ThreadPoolInfo { poolName: $poolName, createThreadId: $createThreadId, threadIds: ${threadIds}, shutDown: $shutDown\n" +
                "  * createStack: \n${TrackerUtils.addIndentSpace(createStack)}"
    }

    fun fullDumpForMarkDown() : String {
        val info = StringBuilder()
        info.append("\"$poolName\" - createThreadId: $createThreadId, threadIds: ${threadIds}\n")

        val realThreadPoolObj = getRealThreadPoolObj(poolObj)
        if (realThreadPoolObj is ThreadPoolExecutor) {
            info.append(
                        "corePoolSize: ${realThreadPoolObj.corePoolSize}, maximumPoolSize: ${realThreadPoolObj.maximumPoolSize}, " +
                        "Active threads: ${realThreadPoolObj.activeCount}, Completed tasks: ${realThreadPoolObj.completedTaskCount}, " +
                        "Total tasks: ${realThreadPoolObj.taskCount}, Queue size: ${realThreadPoolObj.queue.size}\n")
        }
        info.append("createStack: \n" +
                    "```" + "\n" +
                    createStack +
                    "```" + "\n" )

        return info.toString()
    }

    // 如果pool的类型是私有类DelegatedExecutorService, 使用反射获取pool的真实线程池对象
    private fun getRealThreadPoolObj(pool: Executor?): Executor? {
        if (pool == null) {
            return null
        }

        var realExecutorObj: Executor = pool

        if (pool.javaClass.name == "java.util.concurrent.Executors\$FinalizableDelegatedExecutorService") {
            try {
                val field = pool.javaClass.superclass?.getDeclaredField("e")
                field?.apply {
                    isAccessible = true
                    realExecutorObj = get(pool) as Executor
                }
            } catch (e: NoSuchFieldException) {
                Log.w(TAG, "getRealThreadPoolObj: NoSuchFieldException, pool: $pool, e: $e")
            } catch (e: IllegalAccessException) {
                Log.w(TAG, "getRealThreadPoolObj: IllegalAccessException, pool: $pool, e: $e")
            } catch (e: ClassCastException) {
                Log.w(TAG, "getRealThreadPoolObj: ClassCastException, pool: $pool, e: $e")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "getRealThreadPoolObj: IllegalArgumentException, pool: $pool, e: $e")
            }
        }

        return realExecutorObj
    }
}