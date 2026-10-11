package com.darkneslord.iptv

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * kotlinx.coroutines kullanmadan, öğeleri paralel işler ve sonuçları aynı sırada döndürür.
 * Bloklayıcıdır; ana thread'den çağırma.
 */
fun <T, R> parallelMap(items: List<T>, maxThreads: Int = 6, transform: (T) -> R): List<R> {
    if (items.isEmpty()) return emptyList()
    val pool = Executors.newFixedThreadPool(min(maxThreads, items.size))
    try {
        val futures = items.map { item -> pool.submit(Callable { transform(item) }) }
        return futures.map { it.get() }
    } finally {
        pool.shutdown()
    }
}
