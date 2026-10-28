package com.gathin.tvhome.core.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/**
 * Lightweight, zero-channel effect bus for MVI Presenters.
 * Buffers effects until collected and supports direct synchronous emission.
 */
class EffectBus<T> {
    private val lock = Any()
    private var listener: ((T) -> Unit)? = null
    private val pending = ArrayDeque<T>()

    fun emit(effect: T) {
        val target: ((T) -> Unit)?
        synchronized(lock) {
            target = listener
            if (target == null) {
                pending.addLast(effect)
            }
        }
        target?.invoke(effect)
    }

    suspend fun collect(collector: (T) -> Unit): Nothing {
        synchronized(lock) {
            while (pending.isNotEmpty()) {
                collector(pending.removeFirst())
            }
            listener = collector
        }
        try {
            awaitCancellation()
        } finally {
            synchronized(lock) {
                if (listener === collector) {
                    listener = null
                }
            }
        }
    }

    suspend fun first(): T {
        synchronized(lock) {
            if (pending.isNotEmpty()) {
                return pending.removeFirst()
            }
        }
        val deferred = CompletableDeferred<T>()
        val previousListener: ((T) -> Unit)?
        synchronized(lock) {
            previousListener = listener
            listener = { effect ->
                deferred.complete(effect)
            }
        }
        try {
            return deferred.await()
        } finally {
            synchronized(lock) {
                listener = previousListener
            }
        }
    }
}
