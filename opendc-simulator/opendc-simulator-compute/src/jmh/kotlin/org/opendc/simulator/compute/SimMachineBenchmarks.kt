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

package org.opendc.simulator.compute

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.opendc.simulator.compute.kernel.SimFairShareHypervisor
import org.opendc.simulator.compute.kernel.SimSpaceSharedHypervisor
import org.opendc.simulator.compute.model.MachineModel
import org.opendc.simulator.compute.model.MemoryUnit
import org.opendc.simulator.compute.model.ProcessingNode
import org.opendc.simulator.compute.model.ProcessingUnit
import org.opendc.simulator.compute.power.ConstantPowerModel
import org.opendc.simulator.compute.power.SimplePowerDriver
import org.opendc.simulator.compute.workload.SimTraceWorkload
import org.opendc.simulator.core.SimulationCoroutineScope
import org.opendc.simulator.core.runBlockingSimulation
import org.opendc.simulator.flow.FlowEngine
import org.openjdk.jmh.annotations.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@OptIn(ExperimentalCoroutinesApi::class)
class SimMachineBenchmarks {
    private lateinit var scope: SimulationCoroutineScope
    private lateinit var engine: FlowEngine
    private lateinit var machineModel: MachineModel

    @Setup
    fun setUp() {
        scope = SimulationCoroutineScope()
        engine = FlowEngine(scope.coroutineContext, scope.clock)

        val cpuNode = ProcessingNode("Intel", "Xeon", "amd64", 2)

        machineModel = MachineModel(
            cpus = List(cpuNode.coreCount) { ProcessingUnit(cpuNode, it, 1000.0) },
            memory = List(4) { MemoryUnit("Crucial", "MTA18ASF4G72AZ-3G2B1", 3200.0, 32_000) }
        )
    }

    @State(Scope.Thread)
    class Workload {
        lateinit var trace: Sequence<SimTraceWorkload.Fragment>

        @Setup
        fun setUp() {
            val random = ThreadLocalRandom.current()
            val entries = List(10000) { SimTraceWorkload.Fragment(it * 1000L, 1000, random.nextDouble(0.0, 4500.0), 1) }
            trace = entries.asSequence()
        }
    }

    @Benchmark
    fun benchmarkBareMetal(state: Workload) {
        return scope.runBlockingSimulation {
            val machine = SimBareMetalMachine(
                engine, machineModel, SimplePowerDriver(ConstantPowerModel(0.0))
            )
            return@runBlockingSimulation machine.run(SimTraceWorkload(state.trace))
        }
    }

    @Benchmark
    fun benchmarkSpaceSharedHypervisor(state: Workload) {
        return scope.runBlockingSimulation {
            val machine = SimBareMetalMachine(
                engine, machineModel, SimplePowerDriver(ConstantPowerModel(0.0))
            )
            val hypervisor = SimSpaceSharedHypervisor(engine)

            launch { machine.run(hypervisor) }

            val vm = hypervisor.createMachine(machineModel)

            try {
                return@runBlockingSimulation vm.run(SimTraceWorkload(state.trace))
            } finally {
                vm.close()
                machine.close()
            }
        }
    }

    @Benchmark
    fun benchmarkFairShareHypervisorSingle(state: Workload) {
        return scope.runBlockingSimulation {
            val machine = SimBareMetalMachine(
                engine, machineModel, SimplePowerDriver(ConstantPowerModel(0.0))
            )
            val hypervisor = SimFairShareHypervisor(engine)

            launch { machine.run(hypervisor) }

            val vm = hypervisor.createMachine(machineModel)

            try {
                return@runBlockingSimulation vm.run(SimTraceWorkload(state.trace))
            } finally {
                vm.close()
                machine.close()
            }
        }
    }

    @Benchmark
    fun benchmarkFairShareHypervisorDouble(state: Workload) {
        return scope.runBlockingSimulation {
            val machine = SimBareMetalMachine(
                engine, machineModel, SimplePowerDriver(ConstantPowerModel(0.0))
            )
            val hypervisor = SimFairShareHypervisor(engine)

            launch { machine.run(hypervisor) }

            coroutineScope {
                repeat(2) {
                    val vm = hypervisor.createMachine(machineModel)

                    launch {
                        try {
                            vm.run(SimTraceWorkload(state.trace))
                        } finally {
                            machine.close()
                        }
                    }
                }
            }
            machine.close()
        }
    }
}
