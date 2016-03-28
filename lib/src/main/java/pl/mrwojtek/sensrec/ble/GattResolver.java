/*
 * (C) Copyright 2013, 2015-2106 Wojciech Mruczkiewicz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Wojciech Mruczkiewicz
 */

package pl.mrwojtek.sensrec.ble;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lists bluetooth characteristics UUIDs for BLE recordings.
 */
public class GattResolver {

    public static final int FLAG_RETRIEVE = 0x1;
    public static final int FLAG_SUBSCRIBE = 0x2;

    // Generic Access
    public static final UUID DEVICE_NAME =
            UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    public static final UUID APPEARANCE =
            UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb");
    public static final UUID PERIPHERAL_PRIVACY_FLAG =
            UUID.fromString("00002a02-0000-1000-8000-00805f9b34fb");
    public static final UUID RECONNECTION_ADDRESS =
            UUID.fromString("00002a03-0000-1000-8000-00805f9b34fb");
    public static final UUID PERIPHERAL_PREFERRED_CONNECTION_PARAMETERS =
            UUID.fromString("00002a04-0000-1000-8000-00805f9b34fb");

    // Heart Rate
    public static final UUID HEART_RATE_MEASUREMENT =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    public static final UUID BODY_SENSOR_LOCATION =
            UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb");

    // Device Information
    public static final UUID SYSTEM_ID =
            UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb");
    public static final UUID MODEL_NUMBER_STRING =
            UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public static final UUID SERIAL_NUMBER_STRING =
            UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");
    public static final UUID FIRMWARE_REVISION_STRING =
            UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    public static final UUID HARDWARE_REVISION_STRING =
            UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");
    public static final UUID SOFTWARE_REVISION_STRING =
            UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");
    public static final UUID MANUFACTURER_NAME_STRING =
            UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");

    // Battery Service
    public static final UUID BATTERY_LEVEL =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    // Configuration
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final Map<UUID, Integer> flags;

    static {
        flags = new HashMap<>();
        flags.put(DEVICE_NAME, FLAG_RETRIEVE);
        flags.put(APPEARANCE, FLAG_RETRIEVE);
        flags.put(PERIPHERAL_PRIVACY_FLAG, FLAG_RETRIEVE);
        flags.put(PERIPHERAL_PREFERRED_CONNECTION_PARAMETERS, FLAG_RETRIEVE);
        flags.put(HEART_RATE_MEASUREMENT, FLAG_SUBSCRIBE);
        flags.put(BODY_SENSOR_LOCATION, FLAG_RETRIEVE);
        flags.put(SYSTEM_ID, FLAG_RETRIEVE);
        flags.put(MODEL_NUMBER_STRING, FLAG_RETRIEVE);
        flags.put(SERIAL_NUMBER_STRING, FLAG_RETRIEVE);
        flags.put(FIRMWARE_REVISION_STRING, FLAG_RETRIEVE);
        flags.put(HARDWARE_REVISION_STRING, FLAG_RETRIEVE);
        flags.put(SOFTWARE_REVISION_STRING, FLAG_RETRIEVE);
        flags.put(MANUFACTURER_NAME_STRING, FLAG_RETRIEVE);
        flags.put(BATTERY_LEVEL, FLAG_RETRIEVE);
    }

    public static int resolve(UUID uuid) {
        Integer f = flags.get(uuid);
        if (f != null) {
            return f;
        } else {
            return 0;
        }
    }
}
