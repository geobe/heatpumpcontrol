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


import com.pi4j.io.gpio.GpioFactory
import com.pi4j.io.gpio.PinState
import com.pi4j.io.gpio.RaspiPin

/**
 * Interface class to two relays controlling smart grid state of Ochsner heat pump.
 * According to document AA-FE_Einstellung_Smart_Grid_OTE3_4_ab_V5.8x_DE_20160302.pdf,
 * four states are supported, see HeatPumpState below. <br>
 * In our HW configuration, we use 2 toggle relays K1 and K2. When the program is stopped or not functional,
 * the heat pump shall run in NORMALOPERATION mode. We have the following truth table:<br>
 * | PIN21 = K1 | PIN43 = K2 | MODE<br>
 * |  contact   |    open    | NORMALOPERATION<br>
 * |  contact   |   contact  | PRECEDENCE<br>
 * |   open     |    open    | SUSPENDED<br>
 * |   open     |   contact  | ENFORCED<br>
 * This implies that K1 must use the switch-off contact, K2 the switch-on contact of the toggle relay
 */
class HeatpumpController {
    static final K1PIN = RaspiPin.GPIO_00 // pi4j v2 17     // for Ochsner Pin21
    static final K2PIN = RaspiPin.GPIO_02 // pi4j v2 27     // for Ochsner Pin43
    private HeatPumpState state = HeatPumpState.NORMALOPERATION
    private def pi4j = GpioFactory.getInstance()//= Pi4J.newAutoContext()
    private def k1Pin = pi4j.provisionDigitalOutputPin(K1PIN, 'RelayK1', PinState.HIGH)
            //= pi4j.dout().create(K1PIN, 'RelayK1')
    private def k2Pin  = pi4j.provisionDigitalOutputPin(K2PIN, 'RelayK2', PinState.HIGH)
    //= pi4j.dout().create(K2PIN, 'RelayK2')

    HeatpumpController() {
        k1Pin.setShutdownOptions(true, PinState.HIGH)
        k2Pin.setShutdownOptions(true, PinState.LOW)
/*
        k1Pin.config().initialState(DigitalState.LOW)
        k1Pin.config().shutdownState(DigitalState.LOW)
        k2Pin.config().initialState(DigitalState.LOW)
        k2Pin.config().shutdownState(DigitalState.LOW)
*/
    }

    def shutdown() {
        pi4j.shutdown()
    }

    HeatPumpState getState() {
        return state
    }

    HeatPumpState setState(HeatPumpState s) throws IllegalArgumentException {
        switch (s) {
            case HeatPumpState.ENFORCED:
                throw new IllegalArgumentException("State $s not implemented")
            case HeatPumpState.NORMALOPERATION:
                k1Pin.high()
                k2Pin.high()
                break
            case HeatPumpState.SUSPENDED:
                k1Pin.low()
                k2Pin.high()
                break
            case HeatPumpState.PRECEDENCE:
                k1Pin.high()
                k2Pin.low()
        }
        state = s
    }

    static void main(String[] args) {
        def controller = new HeatpumpController()
        println 'both relays should be off'
        Thread.sleep(10000)
        println 'going to SUSPENDED mode, K1 should be high'
        controller.state = HeatPumpState.SUSPENDED
        Thread.sleep(10000)
        println 'going to PRECEDENCE mode, K2 should be high'
        controller.state = HeatPumpState.PRECEDENCE
        Thread.sleep(10000)
        println 'going to NORMALOPERATION mode, K1 and K2 should be low'
        controller.setState HeatPumpState.NORMALOPERATION
        Thread.sleep(10000)
//        println 'going to ENFORCED mode, should throw exceptionh'
//        Thread.sleep(1000)
//        controller.state = HeatPumpState.ENFORCED
        println 'shutdown, every relay should be off'
        controller.shutdown()
        Thread.sleep(5000)
    }
}

enum HeatPumpState {
    NORMALOPERATION,        // Normalbetrieb
    SUSPENDED,              // Sperre
    PRECEDENCE,             // Vorzugsbetrieb
    ENFORCED                // Abnahmezwang
}
