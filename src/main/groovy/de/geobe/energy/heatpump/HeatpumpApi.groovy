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

import de.geobe.energy.automation.PeriodicExecutor
import com.mitchellbosecke.pebble.PebbleEngine
import com.mitchellbosecke.pebble.template.PebbleTemplate
import groovy.json.JsonOutput
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WriteCallback
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import spark.Request
import spark.Response
import spark.Route

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import static spark.Spark.*

@WebSocket
class HeatpumpApi {

    /** store for websocket sessions */
    private ConcurrentHashMap<Session, Boolean> sessions = new ConcurrentHashMap<>()

    /** pebble templates */
    PebbleEngine engine = new PebbleEngine.Builder().build()
    def index = engine.getTemplate('public/template/index.peb')
    def stateButtons = engine.getTemplate('public/template/statebuttons.peb')
    def countdownInput = engine.getTemplate('public/template/countdown.peb')
    def heartbeat = engine.getTemplate('public/template/heartbeat.peb')

    /** Hardware interface object */
    private HeatpumpController heatpumpController

    enum HpcEvent {
        INIT,
        SUSPEND,
        NORMAL,
        TICK,
        SET_RUN_OUT,
        BAD_REQUEST
    }

    boolean isRaspi
    /** maximal duration of SUSPENDED state */
    static final MAX_SUSPEND = 66
    static final TIMEUNIT_SUSPEND = TimeUnit.SECONDS
    volatile int countdown = MAX_SUSPEND
    volatile HeatPumpState heatPumpState = HeatPumpState.NORMALOPERATION

    PeriodicExecutor heartbeatExecutor

    def heartbeatRunner = new Runnable() {
        def mark = 0

        @Override
        void run() {
            HeatpumpApi.this.execStateChart(HpcEvent.TICK, mark)
            mark = (mark + 1) % 2
        }
    }

    HeatpumpApi() {
        String osArch = System.getProperty('os.arch')
        isRaspi = osArch in ['aarch64', 'arm', 'arm32']
        if (isRaspi) {
            heatpumpController = new HeatpumpController()
            heartbeatExecutor = new PeriodicExecutor(heartbeatRunner, 1, TIMEUNIT_SUSPEND)
            heartbeatExecutor.start()
        } else {
            println "Must be run on Raspberry Pi, OS Architecture is $osArch"
            System.exit(-1)
        }
    }

    static void main(String[] args) {

        staticFiles.location("public")
        def api = new HeatpumpApi()

        webSocket('/update', api)

        post('state', api.postState)

        post('setCountdown', api.postCountdown)

        get('/', api.indexRoute)
    }

    Route indexRoute = { Request req, Response res ->
        HeatPumpState state //= HeatPumpState.NORMALOPERATION
        if (isRaspi) {
            state = heatpumpController.state // = state
        } else {
            state = HeatPumpState.UNDEFINED
        }
        def ctx = setStateContext(state)
        ctx['websiteTitle'] = 'Smart Grid state'
        res.status 200
        streamOut(index, ctx)
    }

    Route postState = { Request req, Response res ->
        def accept = req.headers('Accept')
        res.status 200
        def val = req.queryParams('sg-state')
        execStateChart(decodeStateRequest(val))
        ''
    }

    Route postCountdown = { Request req, Response res ->
        def accept = req.headers('Accept')
        res.status 200
        def timeout = req.queryParams('sg-time-off')?.toInteger() ?: countdown
        execStateChart(HpcEvent.SET_RUN_OUT, timeout)
        ''
    }

    def execStateChart(HpcEvent event, int val = 0) {
        def ctx = [:]
        PebbleTemplate template
        def json = [status: 'OK']
        switch (heatPumpState) {
            case HeatPumpState.NORMALOPERATION:
                switch (event) {
                    case HpcEvent.SUSPEND:
                        // change to Suspended
                        heatPumpState = heatpumpController.state = HeatPumpState.SUSPENDED
                        ctx['countdownDisabled'] = 'disabled'
                        ctx['countdownValue'] = countdown
                        ctx['stateNormal'] = HeatPumpState.NORMALOPERATION
                        ctx['stateSuspended'] = HeatPumpState.SUSPENDED
                        ctx['checkedSuspended'] = 'checked'
                        ctx['stateColor'] = 'w3-orange'
                        ctx['countdownDisabled'] = 'disabled'
                        json.state =  heatPumpState
                        json.countdown = countdown
                        template = stateButtons
                        break
                    case HpcEvent.SET_RUN_OUT:
                        // set countdown value
                        val = Math.min(val, MAX_SUSPEND)
                        val = Math.max(val, 0)
                        countdown = val
                        ctx['countdownDisabled'] = ''
                        ctx['countdownValue'] = countdown
                        json.countdown = countdown
                        template = countdownInput
                        break
                    case HpcEvent.TICK:
                        // heartbeat
                        ctx['heartbeat'] = val ? 'X' : 'O'
                        template = heartbeat
                        break
                    case HpcEvent.BAD_REQUEST:
                        heatPumpState = HeatPumpState.UNDEFINED
                        return execStateChart(HpcEvent.BAD_REQUEST)
                }
                break
            case HeatPumpState.UNDEFINED:
            case HeatPumpState.SUSPENDED:
                switch (event) {
                    case HpcEvent.TICK:
                        // countdown
                        if (countdown-- > 0) {
                            ctx['countdownDisabled'] = 'disabled'
                            ctx['countdownValue'] = countdown
                            json.countdown = countdown
                            template = countdownInput
                            break
                        }
                    case HpcEvent.BAD_REQUEST:
                        if (event == HpcEvent.BAD_REQUEST) {
                            ctx['stateAlert'] = 'error'
                            ctx['title'] = 'Fehlermeldung'
                        }
                    case HpcEvent.NORMAL:
                        // change to NormalOperation
                        heatPumpState = heatpumpController.state = HeatPumpState.NORMALOPERATION
                        countdown = MAX_SUSPEND
                        ctx['countdownDisabled'] = ''
                        ctx['countdownValue'] = countdown
                        ctx['stateNormal'] = HeatPumpState.NORMALOPERATION
                        ctx['stateSuspended'] = HeatPumpState.SUSPENDED
                        ctx['checkedNormal'] = 'checked'
                        ctx['stateColor'] = 'w3-light-green'
                        json.state =  heatPumpState
                        json.countdown = countdown
                        template = stateButtons
                        break
                }
                break
            case HeatPumpState.UNDEFINED:
                println "Fatal: $heatPumpState"
                break
        }
        def jsonOut  = JsonOutput.toJson json
        def htmlOut = streamOut(template, ctx)
        updateWsValues(htmlOut, jsonOut)
    }

    HpcEvent decodeStateRequest(String stateName) {
        HeatPumpState requested
        try {
            requested = HeatPumpState.valueOf(stateName?.toUpperCase())
        } catch (Exception ex) {
            requested = HeatPumpState.UNDEFINED
        }
        switch (requested) {
            case HeatPumpState.NORMALOPERATION:
                return HpcEvent.NORMAL
            case HeatPumpState.SUSPENDED:
                return HpcEvent.SUSPEND
            case HeatPumpState.UNDEFINED:
                return HpcEvent.BAD_REQUEST
        }
    }

    def wsGetState() {
        def state
        if (isRaspi) {
            state = heatpumpController.state
        } else {
            state = HeatPumpState.UNDEFINED
        }
        def json = [
                state : state,
                status: 'OK'
        ]
        def jsonOut = JsonOutput.toJson json
        def ctx = setStateContext(state)
        def htmlOut = streamOut(stateButtons, ctx)
        updateWsValues(htmlOut, jsonOut)
    }

    private streamOut(PebbleTemplate stateButtons, LinkedHashMap<Object, Object> ctx) {
        def out = new StringWriter()
        stateButtons.evaluate(out, ctx)
        out.toString()
    }

    private LinkedHashMap<Object, Object> setStateContext(HeatPumpState state) {
        def ctx = [:]
        ctx['stateNormal'] = HeatPumpState.NORMALOPERATION
        ctx['stateSuspended'] = HeatPumpState.SUSPENDED
        ctx['countdownValue'] = countdown
        switch (state) {
            case HeatPumpState.NORMALOPERATION:
                ctx['checkedNormal'] = 'checked'
                ctx['stateColor'] = 'w3-light-green'
                break
            case HeatPumpState.SUSPENDED:
                ctx['checkedSuspended'] = 'checked'
                ctx['stateColor'] = 'w3-orange'
                ctx['countdownDisabled'] = 'disabled'
                break
            default:
                throw new IllegalArgumentException()
        }
        ctx
    }

    /***************** websocket methods **************/

    @OnWebSocketConnect
    void onConnect(Session user) {
        sessions.put(user, false)
        println "$user.remoteAddress"
    }

    @OnWebSocketClose
    void onClose(Session user, int statusCode, String reason) {
        if (sessions.remove user) {
            println "removed on close: $user.remoteAddress"
        }
    }

    @OnWebSocketMessage
    void onMessage(Session user, String message) {
        println "message from $user.remoteAddress: $message"
        def msg = message.toUpperCase()
        if (msg.contains('JSON')) {
            sessions.put(user, true)
            wsGetState()
        } else if (msg.contains ('NORMALOPERATION')) {
            execStateChart(HpcEvent.NORMAL)
        } else if (msg.contains ('SUSPENDED')) {
            execStateChart(HpcEvent.SUSPEND)
        } else if (msg in ['QUERY']) {
            wsGetState()
        }
    }

    @OnWebSocketError
    void onError(Session session, Throwable error) {
        println error
    }

    def updateWsValues(String html, String json) {
        sessions.keySet().findAll { it.isOpen() }.each { socket ->
            String out
            if (sessions[socket]) {
                out = json
            } else {
                out = html
            }
            try {
                socket.remote.sendString(out, writeCallback)
            } catch (Exception ex) {
                ex.printStackTrace()
            }
        }
    }

    private WriteCallback writeCallback = new WriteCallback() {
        @Override
        void writeFailed(Throwable x) {
            if (x instanceof IllegalStateException) {
                println "Websocket exception: ${((java.lang.IllegalStateException) x.message)}"
            }
        }

        @Override
        void writeSuccess() {

        }
    }

    /************* end websocket methods **************/
}
