package com.safetica.datasafe.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

suspend fun <A>Collection<A>.forEachParallel(f: suspend (A) -> Any): Unit = runBlocking {
    map { async(Dispatchers.IO) { f(it) } }.forEach { it.await() }
}