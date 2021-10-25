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

package org.opendc.compute.workload.export.parquet

import io.opentelemetry.sdk.common.CompletableResultCode
import org.opendc.telemetry.compute.ComputeMetricExporter
import org.opendc.telemetry.compute.ComputeMonitor
import org.opendc.telemetry.compute.table.HostData
import org.opendc.telemetry.compute.table.ServerData
import org.opendc.telemetry.compute.table.ServiceData
import java.io.File

/**
 * A [ComputeMonitor] that logs the events to a Parquet file.
 */
public class ParquetComputeMetricExporter(base: File, partition: String, bufferSize: Int) : ComputeMetricExporter() {
    private val serverWriter = ParquetServerDataWriter(
        File(base, "server/$partition/data.parquet").also { it.parentFile.mkdirs() },
        bufferSize
    )

    private val hostWriter = ParquetHostDataWriter(
        File(base, "host/$partition/data.parquet").also { it.parentFile.mkdirs() },
        bufferSize
    )

    private val serviceWriter = ParquetServiceDataWriter(
        File(base, "service/$partition/data.parquet").also { it.parentFile.mkdirs() },
        bufferSize
    )

    override fun record(data: ServerData) {
        serverWriter.write(data)
    }

    override fun record(data: HostData) {
        hostWriter.write(data)
    }

    override fun record(data: ServiceData) {
        serviceWriter.write(data)
    }

    override fun shutdown(): CompletableResultCode {
        hostWriter.close()
        serviceWriter.close()
        serverWriter.close()

        return CompletableResultCode.ofSuccess()
    }
}
