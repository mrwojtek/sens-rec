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

/**
 * Records sensors data over network.
 */
public class SocketOutput {

    public Record newRecord() {
        return new Record();
    }

    private class Record implements Output.Record {

        public Record() {

        }

        @Override
        public Output.Record start(short typeId, short deviceId) {
            return null;
        }

        @Override
        public Output.Record write(short value) {
            return null;
        }

        @Override
        public Output.Record write(int value) {
            return null;
        }

        @Override
        public Output.Record write(long value) {
            return null;
        }

        @Override
        public Output.Record write(float value) {
            return null;
        }

        @Override
        public Output.Record write(String value) {
            return null;
        }

        @Override
        public void save() {

        }
    }

}
