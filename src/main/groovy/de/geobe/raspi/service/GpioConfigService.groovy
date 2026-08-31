package de.geobe.raspi.service

import com.pi4j.Pi4J
import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalOutputBuilder
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.pwm.Pwm
import com.pi4j.io.pwm.PwmConfigBuilder
import com.pi4j.io.pwm.PwmType

class GpioConfigService {

    private static Context pi4jContext = Pi4J.newAutoContext()
//    private static PwmConfigBuilder pwmCfgBuilder
    private static DigitalOutputBuilder doutBuilder

    static Context getContext() {
        pi4jContext
    }

//    private static synchronized getPwmConfigBuilder() {
//        if (!pwmCfgBuilder) {
//            pwmCfgBuilder = Pwm.newConfigBuilder(pi4jContext)
//        }
//        pwmCfgBuilder
//    }

    private static synchronized getDigitalOutputBuilder() {
        if (!doutBuilder) {
            doutBuilder = DigitalOutput.newBuilder(pi4jContext)
        }
        doutBuilder
    }

//    static Pwm createPwm(int pwmAddress,
//                         int dutyCycle = 0,
//                         int frequency = 0)
//            throws Exception {
//        Pwm pwm
//        def configPwm = pwmConfigBuilder
//                .address(pwmAddress)
//                .pwmType(PwmType.HARDWARE)
//                .provider("ffm-pwm")
//                .initial(dutyCycle)
//                .frequency(frequency)
//                .build()
//        pwm = pi4jContext.create(configPwm)
//        pwm
//    }

    static DigitalOutput createDigitalOutput(int bcmPin,
                                             DigitalState init = DigitalState.LOW,
                                             DigitalState exit = DigitalState.HIGH) {
        def output = digitalOutputBuilder
                .address(bcmPin)
                .id("DIGITAL-OUTPUT-PIN-$bcmPin".toString())
                .name("Digital Output Pin $bcmPin")
                .provider("ffm-digital-output")
                .initial(init)
                .shutdown(exit)
                .build()
        output
    }

//    static void main(String[] args) {
//        // Configure default logging level, accept a log level as the first program argument
//        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
//        println 'started'
//        def pwm0 = createPwm(12)
//        println 'pwm0 created'
//        def pwm1 = createPwm(13, 20, 5)
//        println 'pwm1 created'
//        def dout = [
//                createDigitalOutput(23, DigitalState.HIGH, DigitalState.LOW),
//                createDigitalOutput(24, DigitalState.HIGH, DigitalState.LOW),
//                createDigitalOutput(17, DigitalState.HIGH, DigitalState.LOW),
//                createDigitalOutput(27, DigitalState.HIGH, DigitalState.LOW),
//                ]
//        println 'digital out 23, 24, 17, 27 created'
//
//        Thread.sleep(3000)
//
//        pwm0.on(10, 1)
//        pwm1.on(50, 3)
//
//        println "pwm0 polarity ${pwm0.polarity}"
//
//        dout.each {outPin ->
//            outPin.low()
//        }
//        Thread.sleep(2000)
//        dout.each {outPin ->
//            outPin.high()
//        }
//        Thread.sleep(2000)
//        dout.each {outPin ->
//            outPin.low()
//        }
//        Thread.sleep(2000)
//        dout.each {outPin ->
//            outPin.high()
//        }
//        Thread.sleep(2000)
//        dout.each {outPin ->
//            outPin.low()
//        }
//        Thread.sleep(2000)
//
//        pwm0.off()
//        pwm1.off()
//
//        pwm0.shutdown(pi4jContext)
//        pwm1.shutdown(pi4jContext)
//        dout.each {outPin ->
//            outPin.shutdown(pi4jContext)
//        }
//
//        pi4jContext.shutdown()
//
//    }
}
