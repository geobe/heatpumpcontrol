/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024.  Georg Beier. All rights reserved.
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


import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

import java.util.concurrent.TimeUnit

class HeatpumpSupervisor {

    static final TICK_TIME = 10
    static final TIMEUNIT_TICK = TimeUnit.SECONDS
    static final currentStateFile = 'heatpumpstate.json'
    static final stateChangeLog = 'heatpump.log'
    static final heatpumpDir = '.heatpump'
    static DateTimeFormatter stampPattern = DateTimeFormat.forPattern('dd.MM.yy HH:mm:ss')

    private JsonSlurper jsonSlurper = new JsonSlurper()
    /** check and remember processor type */
    boolean isRaspi
    /** Hardware interface object */
    private IHeatpumpController heatpumpController
    /** time table with run/suspend hours as booleans */
    volatile private boolean[] suspendedHours = new boolean[24]
    /** set default to normal */
    volatile HeatpumpSupervisorState supervisorState = HeatpumpSupervisorState.NORMALOPERATION

    HeatpumpSupervisor() {
        String osArch = System.getProperty('os.arch')
        isRaspi = osArch in ['aarch64', 'arm', 'arm32']
        if (isRaspi) {
            heatpumpController = new HeatpumpController()
        } else {
            heatpumpController = new HeatpumpMockController()
            println "This is only a mockup!\nMust be run on Raspberry Pi, OS Architecture is $osArch"
        }
        for (i in 0..<suspendedHours.size()) {
            suspendedHours[i] = false
        }
    }

    def getFullState() {
        [controller: heatpumpController.state,
         supervisor: supervisorState]
    }

    def getStateStamp() {
        [timestamp      : stampPattern.print(DateTime.now()),
         supervisorState: supervisorState,
         controlerState : heatpumpController.state,
         timetable      : suspendedHours]
    }

    File getLogFile(String filename) {
        def home = System.getProperty('user.home')
        def dir = new File("$home/$heatpumpDir/".toString())
        if (!dir.exists()) {
            dir.mkdir()
        }
        if (!dir.exists()) {
            dir.mkdir()
        }
        if (!dir.isDirectory()) {
            throw new RuntimeException('Logfile directory cannot be created')
        }
        new File(dir, filename)
    }

    def saveState() {
        def state = stateStamp
        def json = JsonOutput.toJson(state)
        def home = System.getProperty('user.home')
        def file = getLogFile(currentStateFile)
        file.withWriter { writer ->
            writer.write json
        }
    }

    def readState() {
        def file = getLogFile(currentStateFile)
        def savedState
        if (file.exists()) {
            file.withInputStream { inputStream ->
                savedState = jsonSlurper.parse(inputStream)
                try {
                    supervisorState = HeatpumpSupervisorState.valueOf(savedState.supervisorState)
                    heatpumpController.state = HeatpumpControllerState.valueOf(savedState.controlerState)
                    List<Boolean> timetable = savedState.timetable
                    suspendedHours = timetable as boolean[]
                } catch (Exception ex) {
                    println "Enum problem $ex"
                    supervisorState = HeatpumpSupervisorState.NORMALOPERATION
                    heatpumpController.state = HeatpumpControllerState.NORMALOPERATION
                }
            }
        } else {
            println "nothing saved"
        }

    }

    def writeLog(def now = DateTime.now()) {
        def suspHoursList = []
        suspendedHours.eachWithIndex { boolean isSuspendedHour, int i ->
            if (isSuspendedHour) {
                suspHoursList << i
            }
        }
        def logLine =
                "${stampPattern.print(now)}: ${heatpumpController.state}, $supervisorState, $suspHoursList\n"
        getLogFile(stateChangeLog).append(logLine)
    }

    def execStateChart(HpcEvent event, int hour = 0, boolean suspend = false) {
        switch (supervisorState) {
            case HeatpumpSupervisorState.NORMALOPERATION:
                switch (event) {
                    case HpcEvent.TIMETABLE_CHANGED:
                        suspendedHours[hour] = suspend
                        break
                    case HpcEvent.TICK:
                        // nothing to do
                        break
                    case HpcEvent.SUSPEND:
                        // change to Suspended
                        supervisorState = HeatpumpSupervisorState.SUSPENDED
                        heatpumpController.state = HeatpumpControllerState.SUSPENDED
                        break
                    case HpcEvent.TIMED:
                        // change to Suspended
                        supervisorState = HeatpumpSupervisorState.TIMED
                        checkUpdateControllerState()
                        break
                    case HpcEvent.BAD_REQUEST:
                        supervisorState = HeatpumpSupervisorState.UNDEFINED
                        return execStateChart(HpcEvent.BAD_REQUEST)
                    default:
                        return
                }
                break
//            case HeatpumpSupervisorState.UNDEFINED:
            case HeatpumpSupervisorState.SUSPENDED:
                switch (event) {
                    case HpcEvent.TIMETABLE_CHANGED:
                        suspendedHours[hour] = suspend
                        break
                    case HpcEvent.TICK:
                        // nothing to do
                        break
                    case HpcEvent.NORMAL:
                        // change to NormalOperation
                        supervisorState = HeatpumpSupervisorState.NORMALOPERATION
                        heatpumpController.state = HeatpumpControllerState.NORMALOPERATION
                        break
                    case HpcEvent.TIMED:
                        // change to Suspended
                        supervisorState = HeatpumpSupervisorState.TIMED
                        checkUpdateControllerState()
                        break
                    case HpcEvent.BAD_REQUEST:
                        supervisorState = HeatpumpSupervisorState.UNDEFINED
                        return execStateChart(HpcEvent.BAD_REQUEST)
                }
                break
            case HeatpumpSupervisorState.TIMED:
                switch (event) {
                    case HpcEvent.NORMAL:
                        // change to NormalOperation
                        supervisorState = HeatpumpSupervisorState.NORMALOPERATION
                        heatpumpController.state = HeatpumpControllerState.NORMALOPERATION
                        break
                    case HpcEvent.SUSPEND:
                        // change to Suspended
                        supervisorState = HeatpumpSupervisorState.SUSPENDED
                        heatpumpController.state = HeatpumpControllerState.SUSPENDED
                        break
                    case HpcEvent.TIMETABLE_CHANGED:
                        suspendedHours[hour] = suspend
                        checkUpdateControllerState()
                        break
                    case HpcEvent.TICK:
                        checkUpdateControllerState()
                        break
                    case HpcEvent.BAD_REQUEST:
                        supervisorState = HeatpumpSupervisorState.UNDEFINED
                        return execStateChart(HpcEvent.BAD_REQUEST)
                        break
                }
                break
            case HeatpumpSupervisorState.UNDEFINED:
                println "Fatal: $supervisorState"
                break
            default:
                break
        }
        if (event in [HpcEvent.NORMAL, HpcEvent.SUSPEND, HpcEvent.TIMED, HpcEvent.TIMETABLE_CHANGED]) {
            saveState()
            writeLog()
        }
    }

    private checkUpdateControllerState() {
        if (supervisorState == HeatpumpSupervisorState.TIMED) {
            def now = DateTime.now()
            def hour = now.hourOfDay
            def hourPlus = now.plusSeconds(TICK_TIME).hourOfDay
            def currentControllerState = heatpumpController.state
            if (suspendedHours[hour] || suspendedHours[hourPlus]) {
                heatpumpController.state = HeatpumpControllerState.SUSPENDED
            } else {
                heatpumpController.state = HeatpumpControllerState.NORMALOPERATION
            }
            if (currentControllerState != heatpumpController.state) {
                writeLog(now)
            }
        }
    }
}

enum HpcEvent {
    INIT,
    SUSPEND,
    NORMAL,
    TIMED,
    TICK,
    TIMETABLE_CHANGED,
    BAD_REQUEST
}

enum HeatpumpSupervisorState {
    NORMALOPERATION,    // Normalbetrieb, keine Einschränkungen
    SUSPENDED,          // Wärmepumpe gesperrt
    TIMED,              // Zeitsteuerung
    UNDEFINED
}
