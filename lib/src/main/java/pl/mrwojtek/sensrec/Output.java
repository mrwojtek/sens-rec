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
 * Interface for recording sensors data.
 */
public abstract class Output {

    public abstract Record start(short typeId, short deviceId);

    public interface Record {
        Record start(short typeId, short deviceId);
        Record write(short value);
        Record write(int value);
        Record write(long value);
        Record write(float value);
        Record write(String value);
        void save();
    }

    protected class NullRecord implements Record {

        @Override
        public Record start(short typeId, short deviceId) {
            return this;
        }

        @Override
        public Record write(short value) {
            return this;
        }

        @Override
        public Record write(int value) {
            return this;
        }

        @Override
        public Record write(long value) {
            return this;
        }

        @Override
        public Record write(float value) {
            return this;
        }

        @Override
        public Record write(String value) {
            return this;
        }

        @Override
        public void save() {

        }
    }

}
