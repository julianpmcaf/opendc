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

package org.opendc.simulator.compute.kernel.interference

import java.util.*

/**
 * A participant of an interference domain.
 */
public interface VmInterferenceMember {
    /**
     * Mark this member as active in this interference domain.
     */
    public fun activate()

    /**
     * Mark this member as inactive in this interference domain.
     */
    public fun deactivate()

    /**
     * Compute the performance score of the member in this interference domain.
     *
     * @param random The source of randomness to apply when computing the performance score.
     * @param load The overall load on the interference domain.
     * @return A score representing the performance score to be applied to the member, with 1
     * meaning no influence, <1 means that performance degrades, and >1 means that performance improves.
     */
    public fun apply(random: SplittableRandom, load: Double): Double
}
