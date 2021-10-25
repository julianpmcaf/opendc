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

import mu.KotlinLogging
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecordBuilder
import org.apache.parquet.avro.AvroParquetWriter
import org.apache.parquet.hadoop.ParquetFileWriter
import org.apache.parquet.hadoop.ParquetWriter
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.opendc.trace.util.parquet.LocalOutputFile
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import kotlin.concurrent.thread

/**
 * A writer that writes data in Parquet format.
 */
public abstract class ParquetDataWriter<in T>(
    path: File,
    private val schema: Schema,
    bufferSize: Int = 4096
) : AutoCloseable {
    /**
     * The logging instance to use.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The queue of commands to process.
     */
    private val queue: BlockingQueue<T> = ArrayBlockingQueue(bufferSize)

    /**
     * An exception to be propagated to the actual writer.
     */
    private var exception: Throwable? = null

    /**
     * The thread that is responsible for writing the Parquet records.
     */
    private val writerThread = thread(start = false, name = this.toString()) {
        val writer = let {
            val builder = AvroParquetWriter.builder<GenericData.Record>(LocalOutputFile(path))
                .withSchema(schema)
                .withCompressionCodec(CompressionCodecName.ZSTD)
                .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
            buildWriter(builder)
        }

        val queue = queue
        val buf = mutableListOf<T>()
        var shouldStop = false

        try {
            while (!shouldStop) {
                try {
                    process(writer, queue.take())
                } catch (e: InterruptedException) {
                    shouldStop = true
                }

                if (queue.drainTo(buf) > 0) {
                    for (data in buf) {
                        process(writer, data)
                    }
                    buf.clear()
                }
            }
        } catch (e: Throwable) {
            logger.error(e) { "Failure in Parquet data writer" }
            exception = e
        } finally {
            writer.close()
        }
    }

    /**
     * Build the [ParquetWriter] used to write the Parquet files.
     */
    protected open fun buildWriter(builder: AvroParquetWriter.Builder<GenericData.Record>): ParquetWriter<GenericData.Record> {
        return builder.build()
    }

    /**
     * Convert the specified [data] into a Parquet record.
     */
    protected abstract fun convert(builder: GenericRecordBuilder, data: T)

    /**
     * Write the specified metrics to the database.
     */
    public fun write(data: T) {
        val exception = exception
        if (exception != null) {
            throw IllegalStateException("Writer thread failed", exception)
        }

        queue.put(data)
    }

    /**
     * Signal the writer to stop.
     */
    override fun close() {
        writerThread.interrupt()
        writerThread.join()
    }

    init {
        writerThread.start()
    }

    /**
     * Process the specified [data] to be written to the Parquet file.
     */
    private fun process(writer: ParquetWriter<GenericData.Record>, data: T) {
        val builder = GenericRecordBuilder(schema)
        convert(builder, data)
        writer.write(builder.build())
    }
}
