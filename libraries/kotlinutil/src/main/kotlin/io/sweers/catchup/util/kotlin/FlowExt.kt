/*
 * Copyright (C) 2019. Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sweers.catchup.util.kotlin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import java.util.concurrent.atomic.AtomicInteger

suspend fun <V, K> Flow<V>.groupBy(selector: (V) -> K): Flow<Pair<K, List<V>>> = flow {
  val map = mutableMapOf<K, MutableList<V>>()
  collect {
    map.getOrPut(selector(it), ::mutableListOf).add(it)
  }
  map.entries.forEach { (key, value) ->
    emit(key to value)
  }
}

suspend fun <T, K : Comparable<K>> Flow<T>.sortBy(selector: (T) -> K): Flow<T> = flow {
  val list = mutableListOf<T>()
  collect {
    list.add(it)
  }
  list.sortBy(selector)
  list.forEach { emit(it) }
}

/**
 * A terminal operator that returns `true` if at least one element emitted by the flow matches the
 * given [predicate]. If there's a match, it then cancels flow's collection. Returns `false` if no
 * elements matched the [predicate].
 */
suspend fun <T> Flow<T>.any(predicate: suspend (T) -> Boolean): Boolean {
  return firstOrNull(predicate) != null
}

fun <T> Flow<T>.mergeWith(other: Flow<T>): Flow<T> = merge(this, other)

fun <T> Flow<T>.windowed(
  size: Int,
  step: Int = 1,
  partialWindows: Boolean = false,
  reuseBuffer: Boolean = false
): Flow<List<T>> = flow {
  require(size > 0 && step > 0) {
    if (size != step) {
      "Both size $size and step $step must be greater than zero."
    } else {
      "size $size must be greater than zero."
    }
  }
  val gap = step - size
  if (gap >= 0) {
    var buffer = ArrayList<T>(size)
    val skip = AtomicInteger(0)
    // TODO if you just use an int here the kotlin compiler blows up
//    var skip = 0
    collect { e ->
      if (skip.get() > 0) {
//        skip -= 1
        skip.decrementAndGet()
        return@collect
      }
      buffer.add(e)
      if (buffer.size == size) {
        emit(buffer)
        if (reuseBuffer) buffer.clear() else buffer = ArrayList(size)
        skip.set(gap)
//        skip = gap
      }
    }
    if (buffer.isNotEmpty()) {
      if (partialWindows || buffer.size == size) emit(buffer)
    }
  } else {
    val buffer = RingBuffer<T>(size)
    collect { e ->
      buffer.add(e)
      if (buffer.isFull()) {
        emit(if (reuseBuffer) buffer else ArrayList(buffer))
        buffer.removeFirst(step)
      }
    }
    if (partialWindows) {
      while (buffer.size > step) {
        emit(if (reuseBuffer) buffer else ArrayList(buffer))
        buffer.removeFirst(step)
      }
      if (buffer.isNotEmpty()) emit(buffer)
    }
  }
}
