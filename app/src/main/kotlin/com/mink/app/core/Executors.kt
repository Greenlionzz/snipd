package com.mink.app.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object AppExecutors {
    private val mainHandler = Handler(Looper.getMainLooper())

    val io: ExecutorService = Executors.newFixedThreadPool(4, namedFactory("app-io"))
    val cpu: ExecutorService = Executors.newFixedThreadPool(2, namedFactory("app-cpu"))

    fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    fun main(block: () -> Unit) {
        if (isMainThread()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun mainDelayed(delayMs: Long, block: () -> Unit): Cancelable {
        val runnable = Runnable { block() }
        mainHandler.postDelayed(runnable, delayMs)
        return Cancelable { mainHandler.removeCallbacks(runnable) }
    }

    fun executeIo(block: () -> Unit): Cancelable {
        val future = io.submit { block() }
        return Cancelable { future.cancel(true) }
    }

    fun executeCpu(block: () -> Unit): Cancelable {
        val future = cpu.submit { block() }
        return Cancelable { future.cancel(true) }
    }

    private fun namedFactory(prefix: String) = object : ThreadFactory {
        private val count = AtomicInteger(1)
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "$prefix-${count.getAndIncrement()}").apply {
                isDaemon = true
            }
        }
    }
}
