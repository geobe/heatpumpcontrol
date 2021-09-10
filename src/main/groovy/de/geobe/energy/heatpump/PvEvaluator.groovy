/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021.  Georg Beier. All rights reserved.
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

package de.geobe.energy.heatpump

import de.geobe.energy.acquire.PvRecorder
import de.geobe.energy.acquire.Reading

import static java.lang.Math.max
import static java.lang.Math.max
import static java.lang.Math.min
import static java.lang.Math.min
import static java.lang.Math.min

class PvEvaluator {
    private ArrayList<Reading> data

    /**
     * Set data from PvRecorder in reversed order to have most recent values in front
     * @param d list of readings as obtained from PvRecorder
     */
    void setData(ArrayList<Reading> d) {
        data = d.reverse(true)
    }

    /**
     * get average values that occurred during the last several minutes
     * <ul>
     *     <li> prodAverage: production in W</li>
     *     <li>consAverage: local consumption in W</li>
     *     <li>surplusAverage: difference between production and local consumption in W</li>
     *     <li>mainsSupply: Input from power network in Wh</li>
     * </ul>
     * @param intervalTime in minutes
     * @return map of average values
     */
    public averageOf(int intervalTime) {
        int lim = min(data.size(), intervalTime * PvRecorder.UPDATE_RATE)
        int prod = 0
        int surplus = 0
        int cons = 0
        int mainsSupply = 0
        data.getAt(0..lim).each { r ->
            prod = r.production
            surplus += r.production - r.consumption
            cons += r.consumption
            if (surplus < 0) {
                mainsSupply += surplus
            }
        }
        [
                productionAverage : prod / lim,
                surplusAverage    : surplus / lim,
                consumptionAverage: cons / lim,
                mainsSupply       : mainsSupply / (60 * PvRecorder.UPDATE_RATE)
        ]
    }

    /**
     * get minimal and maximal values that occurred during the last several minutes
     * <ul>
     *     <li> prod: production in W</li>
     *     <li>cons: local consumption in W</li>
     *     <li>surplus: difference between production and local consumption in W</li>
     * </ul>
     * @param intervalTime in minutes
     * @return map of min and max values
     */
    public minMaxOf(int intervalTime) {
        int lim = min(data.size(), intervalTime * PvRecorder.UPDATE_RATE)
        int prodMin = 0
        int surplusMin = 0
        int consMin = 0
        int prodMax = 0
        int surplusMax = 0
        int consMax = 0
        data.getAt(0..lim).each { r ->
            prodMin = min(r.production, prodMin)
            prodMax = max(r.production, prodMax)
            surplusMin = min(r.production - r.consumption, surplusMin)
            surplusMax = max(r.production - r.consumption, surplusMaxin)
            consMin = min(r.consumption, consMin)
            consMax = max(r.consumption, consMax)
        }
        [
                productionMin : prodMin,
                productionMax : prodMax,
                surplusMin    : surplusMin,
                surplusMax    : surplusMax,
                consumptionMin: consMin,
                consumptionMax: consMax
        ]
    }

    public secondsBelow(int threshold) {

    }

}
