/*
 * (C) Copyright 2013, 2015 Wojciech Mruczkiewicz
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

package pl.mrwojtek.sensrec;

import java.util.Collection;
import java.util.Set;

/**
 * Subscribes for measurements and records them.
 */
public interface Recorder {

    /**
     * Sensor type identifier
     * @return type identifier
     */
    short getTypeId();

    /**
     * Device identifier for a given device type
     * @return unique device id per type
     */
    short getDeviceId();

    /**
     * Unique string that identifies this recorder in preferences
     *
     * @return preference key name
     */
    String getPrefKey();

    /**
     * Short name uniquely identifying this sensor.
     * @return short name
     */
    String getShortName();

    /**
     * Longer description with sensor details.
     * @return description
     */
    String getDescription();

    /**
     * Retrieves frequency measure object.
     *
     * @return frequency measure
     */
    FrequencyMeasure getFrequencyMeasure();


    /**
     * Android dangerous permission required for that sensor
     * @return a set of permissions or <code>null</code> if none required
     */
    Collection<String> getRequiredPermissions();

    /**
     * Starts sensor recording
     */
    void start();

    /**
     * Stops sensor recording
     */
    void stop();
}
