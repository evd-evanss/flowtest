package com.sugarspoon.flowtest

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalCoroutinesApi::class)
class AssertFlow<T> internal constructor(
    private val events: Channel<TestEvent<T>>,
    private val collectJob: Job,
    private val timeoutMs: Long
) {

    private suspend fun <T> withTimeout(body: suspend () -> T): T {
        return if (timeoutMs == 0L) {
            body()
        } else {
            withTimeout(timeoutMs) {
                body()
            }
        }
    }

    suspend fun expectedItem(): T {
        val event = withTimeout {
            events.receive()
        }
        if (event !is TestEvent.Item<T>) {
            throw AssertionError("Expected item but was $event")
        }
        return event.item
    }

    suspend fun expectedError(error: TestEvent<T>): Throwable {
        val event = withTimeout {
            events.receive()
        }
        if (event !is TestEvent.Error) {
            throw AssertionError("Expected error but was $event")
        }
        return event.throwable
    }

    suspend fun expectedComplete(): TestEvent<T> {
        val event = withTimeout {
            events.receive()
        }
        if (event !is TestEvent.Complete) {
            throw AssertionError("Expected complete but was $event")
        }
        return event
    }

    fun cancel() {
        collectJob.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> Flow<T>.test(
    timeoutMs: Long = 1000L,
    validate: suspend AssertFlow<T>.() -> Unit
) {
    coroutineScope {
        val events = Channel <TestEvent<T>>( UNLIMITED )
        val collectJob = launch {
            val terminalEvent = try {
                collect { item ->
                    events.send(com.sugarspoon.flowtest.TestEvent.Item(item))
                }
                com.sugarspoon.flowtest.TestEvent.Complete
            } catch (_: CancellationException) {
                null
            } catch (t: Throwable) {
                com.sugarspoon.flowtest.TestEvent.Error(t)
            }
            if (terminalEvent != null) {
                events.send(terminalEvent)
            }
            events.close()
        }
        val flowAssert = AssertFlow(events, collectJob, timeoutMs)
        try {
            flowAssert.validate()
        } catch (e: CancellationException) {
            if (e !== CancellationException("Ignore remaining events")) {
                throw e
            }
        }
    }
}

