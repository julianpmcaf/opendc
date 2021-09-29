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

package org.opendc.simulator.power

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.simulator.flow.FlowEngine
import org.opendc.simulator.flow.FlowEvent
import org.opendc.simulator.flow.FlowSource
import org.opendc.simulator.flow.source.FixedFlowSource

/**
 * Test suite for the [SimPowerSource]
 */
internal class SimPowerSourceTest {
    @Test
    fun testInitialState() = runBlockingSimulation {
        val engine = FlowEngine(coroutineContext, clock)
        val source = SimPowerSource(engine, capacity = 100.0)

        assertFalse(source.isConnected)
        assertNull(source.inlet)
        assertEquals(100.0, source.capacity)
    }

    @Test
    fun testDisconnectIdempotent() = runBlockingSimulation {
        val engine = FlowEngine(coroutineContext, clock)
        val source = SimPowerSource(engine, capacity = 100.0)

        assertDoesNotThrow { source.disconnect() }
        assertFalse(source.isConnected)
    }

    @Test
    fun testConnect() = runBlockingSimulation {
        val engine = FlowEngine(coroutineContext, clock)
        val source = SimPowerSource(engine, capacity = 100.0)
        val inlet = SimpleInlet()

        source.connect(inlet)

        assertTrue(source.isConnected)
        assertEquals(inlet, source.inlet)
        assertTrue(inlet.isConnected)
        assertEquals(source, inlet.outlet)
        assertEquals(100.0, source.powerDraw)
    }

    @Test
    fun testDisconnect() = runBlockingSimulation {
        val engine = FlowEngine(coroutineContext, clock)
        val source = SimPowerSource(engine, capacity = 100.0)
        val consumer = spyk(FixedFlowSource(100.0, utilization = 1.0))
        val inlet = object : SimPowerInlet() {
            override fun createConsumer(): FlowSource = consumer
        }

        source.connect(inlet)
        source.disconnect()

        verify { consumer.onEvent(any(), any(), FlowEvent.Exit) }
    }

    @Test
    fun testDisconnectAssertion() = runBlockingSimulation {
        val engine = FlowEngine(coroutineContext, clock)
        val source = SimPowerSource(engine, capacity = 100.0)
        val inlet = mockk<SimPowerInlet>(relaxUnitFun = true)
        every { inlet.isConnected } returns false
        every { inlet._outlet } returns null
        every { inlet.createConsumer() } returns FixedFlowSource(100.0, utilization = 1.0)

        source.connect(inlet)

        assertThrows<AssertionError> {
            source.disconnect()
        }
    }

    @Test
    fun testOutletAlreadyConnected() = runBlockingSimulation {
        val engine = FlowEngine(coroutineContext, clock)
        val source = SimPowerSource(engine, capacity = 100.0)
        val inlet = SimpleInlet()

        source.connect(inlet)
        assertThrows<IllegalStateException> {
            source.connect(SimpleInlet())
        }

        assertEquals(inlet, source.inlet)
    }

    @Test
    fun testInletAlreadyConnected() = runBlockingSimulation {
        val engine = FlowEngine(coroutineContext, clock)
        val source = SimPowerSource(engine, capacity = 100.0)
        val inlet = mockk<SimPowerInlet>(relaxUnitFun = true)
        every { inlet.isConnected } returns true

        assertThrows<IllegalStateException> {
            source.connect(inlet)
        }
    }

    class SimpleInlet : SimPowerInlet() {
        override fun createConsumer(): FlowSource = FixedFlowSource(100.0, utilization = 1.0)
    }
}
