package com.mink.app.core

import java.util.concurrent.CopyOnWriteArrayList

object EventBus {
    private val listeners = CopyOnWriteArrayList<(Any) -> Unit>()

    fun subscribe(listener: (Any) -> Unit): Cancelable {
        listeners.add(listener)
        return Cancelable { listeners.remove(listener) }
    }

    fun post(event: Any) {
        AppExecutors.main {
            for (listener in listeners) {
                listener(event)
            }
        }
    }
}
