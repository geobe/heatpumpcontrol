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

import com.pi4j.Pi4J
import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import de.geobe.raspi.service.GpioConfigService

/**
 * Updated: if no additional input module is installed, only normal operation and suspended state
 * are supported.
 * Interface class to one relay controlling smart grid state of Ochsner heat pump.
 * See document AA-FE_Einstellung_Smart_Grid_OTE3_4_ab_V5.8x_DE_20160302.pdf.
 * Two states are supported with a single input contact, see HeatpumpControllerState below. <br>
 * In our HW configuration, we use 1 toggle relay K1. When the program is stopped or not functional,
 * the heat pump shall run in NORMALOPERATION mode. We have the following truth table:<br>
 * | PIN21 = K1 | MODE<br>
 * |  contact   | NORMALOPERATION<br>
 * |   open     | SUSPENDED<br>
 * This implies that K1 must use the switch-off contact of the toggle relay<br>
 * Relays have inverse logic i.e. PinState.High => relay is off
 *
 * PIN43 (GPIO02) is not used but still wired with a relay. Set it HIGH to switch off completely
 */
class HeatpumpController implements IHeatpumpController {
    static final DigitalOutput k1Pin = GpioConfigService.createDigitalOutput(17, DigitalState.HIGH, DigitalState.HIGH)
    //aktuell nur ein pin verwendet, pin 2 immer auf HIGH
    static final DigitalOutput k2Pin = GpioConfigService.createDigitalOutput(27, DigitalState.HIGH, DigitalState.HIGH)

    private HeatpumpControllerState state = HeatpumpControllerState.NORMALOPERATION

//    HeatpumpController() {
//        k1Pin.setShutdownOptions(false, PinState.HIGH)
//        k2Pin.setShutdownOptions(false, PinState.HIGH)
//    }

    void shutdown() {
        GpioConfigService.context.shutdown()
    }

    /**
     * Make sure gpio controller has really changed states
     * @return updated state
     */
    HeatpumpControllerState getState() {
        def s1 = k1Pin.state
        if(k1Pin.isHigh()) {
            return HeatpumpControllerState.NORMALOPERATION
        } else if(k1Pin.isLow()) {
            return HeatpumpControllerState.SUSPENDED
        }
        throw new RuntimeException('Internal error reading pin states')
    }

    HeatpumpControllerState setState(HeatpumpControllerState s) throws IllegalArgumentException {
        switch (s) {
            case HeatpumpControllerState.NORMALOPERATION:
                k1Pin.high()
                break
            case HeatpumpControllerState.SUSPENDED:
                k1Pin.low()
                break
        }
        state
    }

//    static void main(String[] args) {
//        def controller = new HeatpumpController()
//        println 'both relays should be off'
//        while(true){
//            print "Eingabe Normal, Suspend, eXit > "
//            def r = java.lang.System.in.newReader().readLine()
//            if(r.toUpperCase().startsWith('N')) {
//                controller.state = HeatpumpControllerState.NORMALOPERATION
//            } else if(r.toUpperCase().startsWith('S')) {
//                controller.state = HeatpumpControllerState.SUSPENDED
//            } else if(r.toUpperCase().startsWith('X')) {
//                break
//            } else {
//                println "read: $r"
//            }
//        }
//        controller.shutdown()
//        Thread.sleep(1000)
//    }
}

class HeatpumpMockController implements IHeatpumpController {

    private HeatpumpControllerState state = HeatpumpControllerState.NORMALOPERATION

    @Override
    HeatpumpControllerState getState() {
        return state
    }

    @Override
    HeatpumpControllerState setState(HeatpumpControllerState s) throws IllegalArgumentException {
        state = s
        return state
    }

    @Override
    void shutdown() {

    }
}

enum HeatpumpControllerState {
    NORMALOPERATION,        // Normalbetrieb
    SUSPENDED,              // Sperre
    UNDEFINED
}

interface IHeatpumpController {
    HeatpumpControllerState getState()
    HeatpumpControllerState setState(HeatpumpControllerState s) throws IllegalArgumentException
    void shutdown()
}
