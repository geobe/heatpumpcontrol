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
package de.geobe.energy.api

import com.mitchellbosecke.pebble.PebbleEngine
import com.mitchellbosecke.pebble.template.PebbleTemplate
import de.geobe.energy.heatpump.HeatPumpState
import de.geobe.energy.heatpump.HeatpumpController
import groovy.json.JsonOutput
import spark.Request
import spark.Response

import static spark.Spark.*

class HeatpumpApi {

    static boolean isRaspi = System.getProperty('os.arch') == 'arm'
    static HeatpumpController heatpumpController

    static {
        if (isRaspi) {
            heatpumpController = new HeatpumpController()
        }
    }

    static void main(String[] args) {

        PebbleEngine engine = new PebbleEngine.Builder().build()

        def index = engine.getTemplate('public/template/index.peb')
        def stateButtons = engine.getTemplate('public/template/statebuttons.peb')

        staticFiles.location("public")

        post('state') { Request req, Response res ->

            def accept = req.headers('Accept')
            def val = req.queryParams('sg-state')
            try {
                def requestedState = HeatPumpState.valueOf(val?.toUpperCase())
                if (isRaspi) {
                    requestedState = heatpumpController.state = requestedState
                }
                res.status 200
                if (accept.endsWith('json')) {
                    def json = [
                            state : JsonOutput.toJson(requestedState),
                            status: 'OK'
                    ]
                    json
                } else {
                    def ctx = setStateContext(requestedState)
                    streamOut(stateButtons, ctx)
                }
            } catch (IllegalArgumentException) {
                HeatPumpState state = HeatPumpState.NORMALOPERATION
                res.status(200)
                res.body('Bad Request')
                if (isRaspi) {
                    state = heatpumpController.state = state
                }
                if (accept.endsWith('json')) {
                    def json = [
                            state : JsonOutput.toJson(state),
                            status: "Bad Request $val"
                    ]
//                    println "json: $json"
                    json
                } else {
                    def ctx = setStateContext(state)
                    ctx['stateAlert'] = 'title="Fehlermeldung"'
                    streamOut(stateButtons, ctx)
                }
            }
        }

        get('/state') { Request req, Response res ->
            def accept = req.headers('Accept')
            def state
            if (isRaspi) {
                state = heatpumpController.state
            } else {
                state = 'Not defined'
            }
            res.status 200
            if (accept.endsWith('json')) {
                def json = [
                        state : JsonOutput.toJson(state),
                        status: 'OK'
                ]
                json
            } else {
                def ctx = setStateContext(state)
                streamOut(stateButtons, ctx)
            }

        }

        get('/') { req, res ->
            HeatPumpState state = HeatPumpState.NORMALOPERATION
            if (isRaspi) {
                state = heatpumpController.state = state
            }
            def ctx = setStateContext(state)
            ctx['websiteTitle'] = 'Smart Grid state'
            res.status 200
            streamOut(index, ctx)
        }

    }

    private static streamOut(PebbleTemplate stateButtons, LinkedHashMap<Object, Object> ctx) {
        def out = new StringWriter()
        stateButtons.evaluate(out, ctx)
        out.toString()
    }

    private static LinkedHashMap<Object, Object> setStateContext(HeatPumpState state) {
        def ctx = [:]
        ctx['stateNormal'] = HeatPumpState.NORMALOPERATION
        ctx['stateSuspended'] = HeatPumpState.SUSPENDED
        ctx['statePrecedence'] = HeatPumpState.PRECEDENCE
        switch (state) {
            case HeatPumpState.NORMALOPERATION:
                ctx['checkedNormal'] = 'checked'
                ctx['stateColor'] = 'w3-light-green'
                break
            case HeatPumpState.PRECEDENCE:
                ctx['checkedPrecedence'] = 'checked'
                ctx['stateColor'] = 'w3-light-blue'
                break
            case HeatPumpState.SUSPENDED:
                ctx['checkedSuspended'] = 'checked'
                ctx['stateColor'] = 'w3-light-grey'
                break
            default:
                throw new IllegalArgumentException()
        }
        ctx
    }

}
