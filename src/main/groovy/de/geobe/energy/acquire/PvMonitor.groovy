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

import geb.Browser
import groovyx.gpars.actor.Actor
import groovyx.gpars.actor.DefaultActor

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class PvMonitor extends DefaultActor {

    private static final int STARTUP_DELAY = 3
    /** Delay between two read operations, trying ~ 4 ties to find new value */
    private static final int READ_DELAY = 60 / (4 * PvRecorder.UPDATE_RATE) + 1

    /** send messages to this Actor */
    Actor notificationTarget

    /** used to schedule repeated S10 readout */
    private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1)

    private PvRecorder recorder
    private S10Access s10Access
    private Browser browser
    private LocalDateTime lastTimestamp
    private TimedPvReadout readout = new TimedPvReadout()

    /**
     * initialize S10 website using S10Access
     */
    void afterStart() {
        s10Access = new S10Access(S10Access.CONFIGFILE)
        recorder = new PvRecorder()
        browser = s10Access.openSite()
        timer.schedule(readout, STARTUP_DELAY, TimeUnit.SECONDS)
        println "${this.class.name} actor started"
    }

    /** cleanup and close browser before leaving */
    void afterStop() {
        s10Access.doLogout()
        browser.close()
    }

    /**
     * wait for messages and handle them
     */
    void act() {
        loop {
            react { Object msg ->
                switch (msg) {
                    case Reading:
                        def timestamp = ((Reading) msg).timestamp
                        if (!lastTimestamp || lastTimestamp.compareTo(timestamp) < 0) {
                            recorder.addReading(msg)
                            def recording = recorder.val()
                            lastTimestamp = timestamp
                            notificationTarget?.send(recording)
                        }
                        timer.schedule(readout, READ_DELAY, TimeUnit.SECONDS)
                        break
                    case Terminator:
                        terminate()
                }
            }
        }

    }

    class TimedPvReadout implements Runnable {
//        Actor actor
//        S10Access s10Access

        @Override
        void run() {
            def v = s10Access.readCurrentValues()
            PvMonitor.this.send(new Reading(v))
        }
    }
}

class Terminator {}
