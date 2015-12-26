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

import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.locks.Lock;

/**
 * Interface for recording sensors data.
 */
public abstract class Output {

    public static final String TAG = "SensRec";

    public abstract Record start(short typeId, short deviceId);

    public interface Record {
        Record start(short typeId, short deviceId);
        Record write(short value);
        Record write(int value);
        Record write(long value);
        Record write(float value);
        Record write(double value);
        Record write(String value, int offset, int count);
        void save();
    }

    protected static class NullRecord implements Record {

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
        public Record write(double value) {
            return this;
        }

        @Override
        public Record write(String value, int offset, int count) {
            return this;
        }

        @Override
        public void save() {

        }
    }

    protected static class RecordWrapper implements Record {

        private Record wrapped;

        public RecordWrapper(Record record) {
            this.wrapped = record;
        }

        @Override
        public Record start(short typeId, short deviceId) {
            wrapped.start(typeId, deviceId);
            return this;
        }

        @Override
        public Record write(short value) {
            wrapped.write(value);
            return this;
        }

        @Override
        public Record write(int value) {
            wrapped.write(value);
            return this;
        }

        @Override
        public Record write(long value) {
            wrapped.write(value);
            return this;
        }

        @Override
        public Record write(float value) {
            wrapped.write(value);
            return this;
        }

        @Override
        public Record write(double value) {
            wrapped.write(value);
            return this;
        }

        @Override
        public Record write(String value, int offset, int count) {
            wrapped.write(value, offset, count);
            return this;
        }

        @Override
        public void save() {
            wrapped.save();
        }
    }

    public static abstract class DataOutputStreamRecord implements Record {

        protected Lock writeLock;

        public DataOutputStreamRecord(Lock writeLock) {
            this.writeLock = writeLock;
        }

        protected abstract DataOutputStream getWriter();

        protected void onException(IOException ex) {
            // Virtual
        }

        protected void onWritten(int bytes) {
            // Virtual
        }

        @Override
        public Output.Record start(short typeId, short deviceId) {
            writeLock.lock();
            return this;
        }

        @Override
        public void save() {
            writeLock.unlock();
        }

        @Override
        public Output.Record write(short value) {
            try {
                if (getWriter() != null) {
                    getWriter().writeShort(value);
                    onWritten(2);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing short: " + ex.getMessage());
                onException(ex);
            }
            return this;
        }

        @Override
        public Output.Record write(int value) {
            try {
                if (getWriter() != null) {
                    getWriter().writeInt(value);
                    onWritten(4);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing int: " + ex.getMessage());
                onException(ex);
            }
            return this;
        }

        @Override
        public Output.Record write(long value) {
            try {
                if (getWriter() != null) {
                    getWriter().writeLong(value);
                    onWritten(8);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing long: " + ex.getMessage());
                onException(ex);
            }
            return this;
        }

        @Override
        public Output.Record write(float value) {
            try {
                if (getWriter() != null) {
                    getWriter().writeFloat(value);
                    onWritten(4);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing float: " + ex.getMessage());
                onException(ex);
            }
            return this;
        }

        @Override
        public Output.Record write(double value) {
            try {
                if (getWriter() != null) {
                    getWriter().writeDouble(value);
                    onWritten(4);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing float: " + ex.getMessage());
                onException(ex);
            }
            return this;
        }

        @Override
        public Output.Record write(String value, int offset, int count) {
            try {
                if (getWriter() != null) {
                    getWriter().write(value.getBytes(), offset, count);
                    onWritten(count);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing String: " + ex.getMessage());
                onException(ex);
            }
            return this;
        }
    }

}
