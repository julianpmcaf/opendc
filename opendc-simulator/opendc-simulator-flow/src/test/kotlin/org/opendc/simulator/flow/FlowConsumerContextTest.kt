/*
 * Copyright (c) 2021 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.flow

import io.mockk.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.simulator.flow.internal.FlowConsumerContextImpl
import org.opendc.simulator.flow.internal.FlowEngineImpl
import org.opendc.simulator.flow.source.FixedFlowSource

/**
 * A test suite for the [FlowConsumerContextImpl] class.
 */
class FlowConsumerContextTest {
    @Test
    fun testFlushWithoutCommand() = runBlockingSimulation {
        val engine = FlowEngineImpl(coroutineContext, clock)
        val consumer = object : FlowSource {
            override fun onPull(conn: FlowConnection, now: Long, delta: Long): Long {
                return if (now == 0L) {
                    conn.push(1.0)
                    1000
                } else {
                    conn.close()
                    Long.MAX_VALUE
                }
            }
        }

        val logic = object : FlowConsumerLogic {}
        val context = FlowConsumerContextImpl(engine, consumer, logic)

        engine.scheduleSync(engine.clock.millis(), context)
    }

    @Test
    fun testIntermediateFlush() = runBlockingSimulation {
        val engine = FlowEngineImpl(coroutineContext, clock)
        val consumer = FixedFlowSource(1.0, 1.0)

        val logic = spyk(object : FlowConsumerLogic {})
        val context = FlowConsumerContextImpl(engine, consumer, logic)
        context.capacity = 1.0

        context.start()
        delay(1) // Delay 1 ms to prevent hitting the fast path
        engine.scheduleSync(engine.clock.millis(), context)

        verify(exactly = 2) { logic.onPush(any(), any(), any(), any()) }
    }

    @Test
    fun testDoubleStart() = runBlockingSimulation {
        val engine = FlowEngineImpl(coroutineContext, clock)
        val consumer = object : FlowSource {
            override fun onPull(conn: FlowConnection, now: Long, delta: Long): Long {
                return if (now == 0L) {
                    conn.push(0.0)
                    1000
                } else {
                    conn.close()
                    Long.MAX_VALUE
                }
            }
        }

        val logic = object : FlowConsumerLogic {}
        val context = FlowConsumerContextImpl(engine, consumer, logic)

        context.start()

        assertThrows<IllegalStateException> {
            context.start()
        }
    }

    @Test
    fun testIdempotentCapacityChange() = runBlockingSimulation {
        val engine = FlowEngineImpl(coroutineContext, clock)
        val consumer = spyk(object : FlowSource {
            override fun onPull(conn: FlowConnection, now: Long, delta: Long): Long {
                return if (now == 0L) {
                    conn.push(1.0)
                    1000
                } else {
                    conn.close()
                    Long.MAX_VALUE
                }
            }
        })

        val logic = object : FlowConsumerLogic {}
        val context = FlowConsumerContextImpl(engine, consumer, logic)
        context.capacity = 4200.0
        context.start()
        context.capacity = 4200.0

        verify(exactly = 0) { consumer.onEvent(any(), any(), FlowEvent.Capacity) }
    }

    @Test
    fun testFailureNoInfiniteLoop() = runBlockingSimulation {
        val engine = FlowEngineImpl(coroutineContext, clock)

        val consumer = spyk(object : FlowSource {
            override fun onPull(conn: FlowConnection, now: Long, delta: Long): Long {
                conn.close()
                return Long.MAX_VALUE
            }

            override fun onEvent(conn: FlowConnection, now: Long, event: FlowEvent) {
                if (event == FlowEvent.Exit) throw IllegalStateException("onEvent")
            }

            override fun onFailure(conn: FlowConnection, cause: Throwable) {
                throw IllegalStateException("onFailure")
            }
        })

        val logic = object : FlowConsumerLogic {}

        val context = FlowConsumerContextImpl(engine, consumer, logic)

        context.start()

        delay(1)

        verify(exactly = 1) { consumer.onFailure(any(), any()) }
    }
}
