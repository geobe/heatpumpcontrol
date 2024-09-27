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
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import spark.Request
import spark.Response
import spark.Route

import java.util.concurrent.ConcurrentHashMap

import static spark.Spark.*

@WebSocket
class HeatpumpApi {

    /** store for websocket sessions */
    private ConcurrentHashMap<Session, Boolean> sessions = new ConcurrentHashMap<>()

    /** pebble templates */
    PebbleEngine engine = new PebbleEngine.Builder().build()
    def index = engine.getTemplate('public/template/index.peb')
    def stateButtons = engine.getTemplate('public/template/statebuttons.peb')
    def heartbeat = engine.getTemplate('public/template/heartbeat.peb')
    def timetable = engine.getTemplate('public/template/timetable.peb')

    static DateTimeFormatter tickPattern = DateTimeFormat.forPattern('HH:mm:ss')

    /** Hardware interface object */
    private HeatpumpSupervisor heatpumpSupervisor

    PeriodicExecutor heartbeatExecutor

    def heartbeatRunner = new Runnable() {
        def mark = 0

        @Override
        void run() {
            HeatpumpApi.this.postHeartbeat(mark)
            mark = (mark + 1) % 2
        }
    }

    HeatpumpApi() {
        heatpumpSupervisor = new HeatpumpSupervisor()
        heartbeatExecutor = new PeriodicExecutor(heartbeatRunner,
                HeatpumpSupervisor.TICK_TIME,
                HeatpumpSupervisor.TIMEUNIT_TICK)
        heartbeatExecutor.start()
        heatpumpSupervisor.readState()
    }

    static void main(String[] args) {

        staticFiles.location("public")
        def api = new HeatpumpApi()

        webSocket('/update', api)

        post('state/*', api.postState)

        post('toggleHour/*', api.postToggleHour)

        get('/', api.indexRoute)
    }

    Route indexRoute = { Request req, Response res ->
        def ctx = stateTemplateContext
        def title = 'Steuerung Wärmepumpe' +
                (heatpumpSupervisor.isRaspi ? '' : ' Test Mockup')
        ctx.websiteTitle = title
        ctx.heartbeat = tickPattern.print(DateTime.now())
        res.status 200
        def html = streamOut(index, ctx)
        html
    }

    Route postState = { Request req, Response res ->
        def accept = req.headers('Accept')
        res.status 200
        def val = req.queryParams('sg-state')
        heatpumpSupervisor.execStateChart(decodeStateRequest(val))
        def ctx = stateTemplateContext
        def html = streamOut(stateButtons, ctx)
        def json = createJson([heartbeat: '-'])
        updateWsValues(html, json)
        ''
    }

    Route postToggleHour = { Request req, Response res ->
        def accept = req.headers('Accept')
        res.status 200
        try {
            def path = req.raw().pathInfo
            def parts = path.tokenize('/')
            def hour = parts[1]?.toInteger()
            def action = parts[2]
            if (hour in 0..23 && action in ['setNormal', 'suspend']) {
                def oldControllerState = heatpumpSupervisor.fullState.controller
                heatpumpSupervisor.execStateChart(HpcEvent.TIMETABLE_CHANGED, hour, action == 'suspend')
                def ctx = stateTemplateContext
                ctx.heartbeat = tickPattern.print(DateTime.now())
                String html
                if (oldControllerState == heatpumpSupervisor.fullState.controller) {
                    html = streamOut(timetable, ctx)
                } else {
                    html = streamOut(stateButtons, ctx)
                }
                def json = createJson()
                updateWsValues(html, json)
            }
        } catch (Exception e) {
            println "Exception $e"
        }
        ''
    }

    def postHeartbeat(int mark) {
        def oldControllerState = heatpumpSupervisor.fullState.controller
        heatpumpSupervisor.execStateChart(HpcEvent.TICK)
        def ctx = stateTemplateContext
        ctx.heartbeat = tickPattern.print(DateTime.now())
        String html
        if (oldControllerState == heatpumpSupervisor.fullState.controller) {
            html = streamOut(heartbeat, ctx)
        } else {
            html = streamOut(stateButtons, ctx)
        }
        def json = createJson([heartbeat: mark])
        updateWsValues(html, json)
    }

    /***************** ui data preparation methods **************/

    private LinkedHashMap<Object, Object> getStateTemplateContext() {
        def fullState = heatpumpSupervisor.fullState
        HeatpumpSupervisorState supervisorState = fullState.supervisor
        HeatpumpControllerState controllerState = fullState.controller
        def ctx = [:]
        ctx['controllerState'] =
                controllerState == HeatpumpControllerState.NORMALOPERATION ?
                        'Normalbetrieb' : 'Suspendiert'
        ctx['stateNormal'] = HeatpumpSupervisorState.NORMALOPERATION
        ctx['stateSuspended'] = HeatpumpSupervisorState.SUSPENDED
        ctx['stateTimed'] = HeatpumpSupervisorState.TIMED
        ctx['suspendedHours'] = heatpumpSupervisor.suspendedHours
        switch (supervisorState) {
            case HeatpumpSupervisorState.NORMALOPERATION:
                ctx['checkedNormal'] = 'checked'
                ctx['stateColor'] = 'w3-signal-green'
                break
            case HeatpumpSupervisorState.SUSPENDED:
                ctx['checkedSuspended'] = 'checked'
                ctx['stateColor'] = 'w3-signal-orange'
                break
            case HeatpumpSupervisorState.TIMED:
                ctx['checkedTimed'] = 'checked'
                ctx['stateColor'] = (controllerState == HeatpumpControllerState.NORMALOPERATION) ?
                        'w3-vivid-yellow-green' : 'w3-vivid-reddish-orange'
                break
            default:
                throw new IllegalArgumentException()
        }
        ctx
    }

    private createJson(Map json = [:]) {
        def fullState = heatpumpSupervisor.fullState
        json.status = 'OK'
        json.controllerState = fullState.controller
        json.supervisorState = fullState.supervisor
        json.suspendedHours = heatpumpSupervisor.suspendedHours//.toList()
        JsonOutput.toJson json
    }

    HpcEvent decodeStateRequest(String stateName) {
        HeatpumpSupervisorState requested
        try {
            requested = HeatpumpSupervisorState.valueOf(stateName?.toUpperCase())
        } catch (Exception ex) {
            requested = HeatpumpSupervisorState.UNDEFINED
        }
        switch (requested) {
            case HeatpumpSupervisorState.NORMALOPERATION:
                return HpcEvent.NORMAL
            case HeatpumpSupervisorState.SUSPENDED:
                return HpcEvent.SUSPEND
            case HeatpumpSupervisorState.TIMED:
                return HpcEvent.TIMED
            case HeatpumpSupervisorState.UNDEFINED:
                return HpcEvent.BAD_REQUEST
        }
    }

    def wsGetPumpState() {
        def state = heatpumpSupervisor.state
        def json = [
                state : state,
                status: 'OK'
        ]
        def jsonOut = JsonOutput.toJson json
        def ctx = createStateTemplateContext(state)
        def htmlOut = streamOut(stateButtons, ctx)
        updateWsValues(htmlOut, jsonOut)
    }

    private streamOut(PebbleTemplate stateButtons, LinkedHashMap<Object, Object> ctx) {
        def out = new StringWriter()
        stateButtons.evaluate(out, ctx)
        out.toString()
    }

    /***************** websocket methods **************/

    @OnWebSocketConnect
    void onConnect(Session user) {
        sessions.put(user, false)
//        println "$user.remoteAddress"
    }

    @OnWebSocketClose
    void onClose(Session user, int statusCode, String reason) {
        if (sessions.remove user) {
//            println "removed on close: $user.remoteAddress"
        }
    }

    @OnWebSocketMessage
    void onMessage(Session user, String message) {
//        println "message from $user.remoteAddress: $message"
        def msg = message.toUpperCase()
        if (msg.contains('JSON')) {
            sessions.put(user, true)
            wsGetPumpState()
        } else if (msg.contains('NORMALOPERATION')) {
            heatpumpSupervisor.execStateChart(HpcEvent.NORMAL)
        } else if (msg.contains('SUSPENDED')) {
            heatpumpSupervisor.execStateChart(HpcEvent.SUSPEND)
        } else if (msg.contains('EXTEND_RUN_OUT')) {
            heatpumpSupervisor.execStateChart(HpcEvent.EXTEND_RUN_OUT)
        } else if (msg.contains('SET_RUN_OUT')) {
            def msgParts = msg.tokenize(' \t,:;')
            def val = msgParts[1]?.isInteger() ? msgParts[1].toInteger() : MAX_SUSPEND
            heatpumpSupervisor.execStateChart(HpcEvent.SET_RUN_OUT, val)
        } else if (msg in ['QUERY']) {
            wsGetPumpState()
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

/**
 *
 */
enum HeatPumpColorState {
    NORMALOPERATION,    // Normalbetrieb, keine Einschränkungen
    SUSPENDED,          // Wärmepumpe gesperrt
    TIMED_NORMAL,       // Zeitsteuerung im Normalbetrieb
    TIMED_SUSPENDED,    // Zeitsteuerung im Sperrbetrieb
    UNDEFINED
}