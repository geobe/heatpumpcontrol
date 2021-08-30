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

package de.geobe.energy.acquire

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import static java.lang.Math.*

/**
 * Trigger cyclic readout of values from S10 web interface,
 * store these values for a certain interval of time and provide
 * methods to evaluate them for heatpump control. <br>
 * All methods are synchronized to allow concurrent updates and reads
 * from different threads.
 */
class PvMonitor {
    static final int SHORT_INTERVAL = 5
    static final int LONG_INTERVAL = 15
    static final int UPDATE_RATE = 3
    static final int MAX_READINGS = LONG_INTERVAL * UPDATE_RATE

    /** a "sliding" store for readings of the last several minutes */
    ArrayDeque<Reading> log = new ArrayDeque<>(MAX_READINGS + 1)

    /**
     * add new reading to the log and remove oldest reading, if it is
     * older then LONG_INTERVAL minutes
     * @param reading values read from web interface
     */
    synchronized addReading(Reading reading) {
        if (log.size() >= MAX_READINGS) {
            log.remove()
        }
        log.add reading
        return
    }

    /**
     * Test if readings for a given time interval are available.
     * Important for system startup.
     * @param intervalTime in minutes
     * @return true if enough values are available
     */
    synchronized isReady(int intervalTime) {
        log.size() >= intervalTime * UPDATE_RATE
    }

    /**
     * Could be useful for debugging
     */
    synchronized logSize() {
        log.size()
    }

    /**
     * get average values that occurred during the last several minutes
     * <ul>
     *     <li> prod: production in W</li>
     *     <li>cons: local consumption in W</li>
     *     <li>surplus: difference between production and local consumption in W</li>
     * </ul>
     * @param intervalTime in minutes
     * @return map of min and max values
     */
    synchronized averageOf(int intervalTime) {
        int lim = intervalTime * UPDATE_RATE
        int loop = 0
        int prod = 0
        int surplus = 0
        int cons = 0
        for (def it = log.descendingIterator(); it.hasNext() && loop < lim;) {
            def r = it.next()
            loop++
            prodMin = r.production
            surplus += r.production - r.consumption
            cons += r.consumption
        }
        [
                production : prod / loop,
                surplus    : surplus / loop,
                consumption: cons / loop
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
    synchronized minMaxOf(int intervalTime) {
        int lim = intervalTime * UPDATE_RATE
        int loop = 0
        int prodMin = 0
        int surplusMin = 0
        int consMin = 0
        int prodMax = 0
        int surplusMax = 0
        int consMax = 0
        for (def it = log.descendingIterator(); it.hasNext() && loop < lim;) {
            def r = it.next()
            loop++
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
}
/**
 * Datatype class containing data of a single reading from S10 web interface
 */
class Reading {
    LocalDateTime timestamp
    int production
    int consumption
    int batteryPower
    int batteryState
    int gridPower
    static formatter = DateTimeFormatter.ofPattern('d.M.yyyy, HH:mm:ss')

    /**
     * Initialize from map read from the web interface
     * @param v reding of web page
     */
    Reading(Map v) {
        timestamp = LocalDateTime.parse(v.timestamp, formatter)
        production = Integer.parseInt(v.solarProd)
        consumption = Integer.parseInt(v.consumption)
        batteryPower = Integer.parseInt(v.batPower)
        batteryState = Integer.parseInt(v.batState)
        gridPower = Integer.parseInt(v.gridPower)
    }

    @Override
    String toString() {
        return "@ $timestamp: p = $production, c = $consumption, load = $batteryPower," +
                " state = $batteryState %, grid = $gridPower"
    }
}
