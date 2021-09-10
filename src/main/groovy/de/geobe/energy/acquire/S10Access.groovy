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

import de.geobe.energy.config.Configuration
import geb.Browser
import geb.Page
import geb.module.PasswordInput
import geb.module.TextInput
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class S10Access {

    static CONFIGFILE = 'liveConfig.cfg'

    static final boolean isRaspi = System.getProperty('os.arch') == 'arm'
    static overlayCloser
    static unitSelectId
    static logoutId
    static LinkedHashMap<String, Object> valIds
    private static final Logger LOG = LoggerFactory.getLogger(S10Access.class)

    def waitOnPage = true
    def sleeptime = 500
    def login
    def overlay
    def unitSelect
    def infoSelect
    def currentValues

    private Browser browser
    private ChromeOptions options = new ChromeOptions()

    /**
     * Read values to access E3DC - S10 web page from configuration file
     * and expose them as variables
     * @param configfile
     */
    S10Access(String configfile) {
        def config = new Configuration().init(configfile)
        login = config.s10site.login
        overlay = config.s10site.overlay
        overlayCloser = overlay.clickTargetId
        unitSelect = config.s10site.unitSelect
        unitSelectId = unitSelect.clickTargetId
        infoSelect = config.s10site.infoSelect
        currentValues = config.s10site.currentValues
        logoutId = config.s10site.logoutId
        valIds = [
                solarProd  : currentValues.solarProductionId,
                batPower   : currentValues.batteryPowerId,
                batState   : currentValues.batteryStateId,
                gridPower  : currentValues.gridPowerId,
                consumption: currentValues.localConsumptionId,
                timestamp  : currentValues.timestampId
        ]

        ChromeDriver driver
        // prepare driver and browser
        if (isRaspi) {
            // seems to run only with these options on raspi
            options.addArguments('--no-sandbox')
            options.addArguments('--disable-dev-shm-usage')
            options.addArguments('--headless')
            driver = new ChromeDriver(options)
            browser = new Browser(driver: driver)
        } else {
            browser = new Browser()
        }
    }

    /**
     * static fields of page classes are initialized from configurated values
     */
    private void initUris() {
        SinglePage.url = login.url
        SinglePage.at = { title == login.title }
    }

    def openSite() {
        def values = [:]
        Browser.drive(browser) {
            initUris()
            to SinglePage
            if (waitOnPage) Thread.sleep sleeptime
            login(login.username, login.password)
            if (waitOnPage) Thread.sleep sleeptime * 4
            closeOverlay()
            if (waitOnPage) Thread.sleep sleeptime
            selectUnit()
            if (waitOnPage) Thread.sleep sleeptime * 4
            selectInfo()
            if (waitOnPage) Thread.sleep sleeptime * 4
        }
    }

    def readCurrentValues() {
        LinkedHashMap<String, Object> result
        Browser.drive(browser) {
            result = currentValues()
        }
        result
    }

    def doLogout() {
        Browser.drive(browser) {
            logout()
            Thread.sleep 5000
        }
    }

    static void main(String[] args) {
        def s10Access = new S10Access(CONFIGFILE)
        def b = s10Access.openSite()
        def v = s10Access.readCurrentValues()
        def r = new Reading(v)
        println r
        s10Access.doLogout()
        Thread.sleep 5000
        b.close()
    }
}

/**
 * S10  web page
 */
class SinglePage extends Page {
    static url
    static at
    static atCheckWaiting = true

    static content = {
        userField { $("input", name: "username").module(TextInput) }
        passwordField { $("input", name: "password").module(PasswordInput) }
        commitButton { $("button", 0) }
        overlayCloser { $("img", id: S10Access.overlayCloser) }
        unitSelection { $("div", id: S10Access.unitSelectId) }
        infoSelection { $('div', class: 'brickColor0', id: startsWith('1-')) }
        logoutMenu { $('li', id: 'HIDEME') }
        confirmButton { $('button', id: 'button-0') }
    }

    void login(user, pw) {
        userField.text = user
        passwordField.text = pw
        commitButton.click()
    }

    void closeOverlay() {
        overlayCloser.click()
    }

    void selectUnit() {
        unitSelection.click()
    }

    void selectInfo() {
        infoSelection.click()
    }

    def currentValues() {
        def result = [:]
        S10Access.valIds.each { key, elementId ->
            def nav = $('span', id: elementId)
            result.(key) = nav.text()
        }
        result
    }

    def logout() {
        def m = logoutMenu
        m.click()
        Thread.sleep(200)
        confirmButton.click()
    }
}

