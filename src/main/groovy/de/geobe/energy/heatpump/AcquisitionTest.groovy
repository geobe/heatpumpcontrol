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

import de.geobe.energy.acquire.PvMonitor
import de.geobe.energy.acquire.Terminator
import groovyx.gpars.actor.DefaultActor

class AcquisitionTest extends DefaultActor {
    PvMonitor pvMonitor

    void afterStart() {
        pvMonitor = new PvMonitor()
        pvMonitor.notificationTarget = this
        pvMonitor.start()
        println "${this.class.name} actor started"
    }

    void afterStop() {
        pvMonitor << new Terminator()
        println "AcquisitionTest actor stoped"
    }

    void act() {
        loop {
            react { Object msg ->
                switch (msg) {
                    case Collection:
                        println "${msg.class.name} of ${msg.size()} elements"
                        msg.each {
                            println it
                        }
                        break
                    case Terminator:
                        println 'Terminator got me'
                    default:
                        println 'terminating'
                        terminate()
                }
            }
        }
    }
}

class TestRun {
    static void main(String[] args) {
        def acqt = new AcquisitionTest()
        acqt.start()
        println 'Hit ENTER to quit'
        def r1
        while (!r1) {
            r1 = System.in.newReader().readLine()
            Thread.sleep 1000
        }
        println "$r1"
        acqt << new Terminator()
        Thread.sleep 5000
    }
}
