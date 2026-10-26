package com.gathin.tvhome.feature.smarthome.data
import com.gathin.tvhome.feature.smarthome.data.HassClient

import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeActions
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class HassClientTest {
    @Test fun preservesEntityNamesAndUnavailableState() {
        val rows = HassClient.parseStates(JSONArray("""[
            {"entity_id":"fan.room", "state":"unavailable",
             "attributes":{"friendly_name":"Quạt thật"}}
        ]"""))
        assertEquals("Quạt thật", rows.single().name)
        assertFalse(rows.single().available)
        assertNull(SmartHomeActions.toggle(rows.single()))
    }

    @Test fun cpuAndSetpointAreNotRoomSensors() {
        val rows = HassClient.parseStates(JSONArray("""[
            {"entity_id":"sensor.cpu_temperature", "state":"46",
             "attributes":{"device_class":"temperature","unit_of_measurement":"°C"}},
            {"entity_id":"climate.master", "state":"off",
             "attributes":{"temperature":16}},
            {"entity_id":"sensor.living_room", "state":"27.5",
             "attributes":{"device_class":"temperature","unit_of_measurement":"°C"}}
        ]"""))
        assertFalse(rows[0].indoorSensor)
        assertFalse(rows[1].indoorSensor)
        assertNull(rows[1].currentTemperature)
        assertEquals(16.0, rows[1].temperature!!, 0.0)
        assertTrue(rows[2].indoorSensor)
    }

    @Test fun commandsUseActualEntityDomain() {
        assertEquals(SmartHomeAction("fan", "toggle"),
            SmartHomeActions.toggle(SmartHomeDevice("fan.room", "Fan", "on")))
        assertEquals(SmartHomeAction("climate", "turn_on"),
            SmartHomeActions.toggle(SmartHomeDevice("climate.room", "AC", "off")))
        assertEquals(SmartHomeAction("cover", "open_cover"),
            SmartHomeActions.toggle(SmartHomeDevice("cover.room", "Curtain", "closed")))
        assertEquals(SmartHomeAction("scene", "turn_on"),
            SmartHomeActions.toggle(SmartHomeDevice("scene.home", "Home", "2026-01-01")))
        assertNull(SmartHomeActions.toggle(SmartHomeDevice("lock.door", "Door", "locked")))
    }

    @Test fun rejectsDashboardAndCredentialUrls() {
        for (value in listOf("http://ha:8123/lovelace", "http://user:pass@ha:8123")) {
            try {
                HassClient.normalize(value)
                fail("Rejected URL expected")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals("http://ha:8123", HassClient.normalize("http://ha:8123/"))
    }
}
