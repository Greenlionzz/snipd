package com.mink.app.core

import java.io.Closeable

fun interface Cancelable : Closeable {
    fun cancel()
    override fun close() = cancel()

    companion object {
        val NOOP = Cancelable {}

        fun composite(vararg items: Cancelable): Cancelable {
            return Cancelable {
                for (item in items) {
                    item.cancel()
                }
            }
        }
    }
}
