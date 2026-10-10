package com.example.nuviobridge

import java.util.concurrent.CountDownLatch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * kotlinx.coroutines'e bağımlı olmadan, suspend bir bloğu çağıran thread'i bekleterek çalıştırır
 * (runBlocking yerine, sadece Kotlin stdlib kullanır). Ana thread'den çağırma.
 */
fun <T> blocking(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
            latch.countDown()
        }
    })
    latch.await()
    return outcome!!.getOrThrow()
}
