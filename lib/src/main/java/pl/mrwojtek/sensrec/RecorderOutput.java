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

import java.util.LinkedList;
import java.util.List;

/**
 * Records sensors data to file or over network in various formats.
 */
public class RecorderOutput extends Output {

    private static final String TAG = "SensRec";

    protected SensorsRecorder sensorsRecorder;
    protected FileOutput fileOutput;
    protected SocketOutput socketOutput;
    protected boolean binary;

    public RecorderOutput(SensorsRecorder sensorsRecorder) {
        this.sensorsRecorder = sensorsRecorder;
        this.fileOutput = new FileOutput(this, sensorsRecorder);
        this.socketOutput = new SocketOutput();
    }

    // Recording objects cache
    private final List<Record> records = new LinkedList<>();

    public FileOutput getFileOutput() {
        return fileOutput;
    }

    public SocketOutput getSocketOutput() {
        return socketOutput;
    }

    public void start(boolean binary, String fileName) {
        this.binary = binary;
        fileOutput.start(fileName);
    }

    @Override
    public Output.Record start(short typeId, short deviceId) {
        Output.Record record = null;
        synchronized (records) {
            if (!records.isEmpty()) {
                record = records.remove(0);
            }
        }

        if (record == null) {
            record = formattedRecord(new Record());
        }

        record.start(typeId, deviceId);

        return record;
    }

    public void stop() {
        fileOutput.stop();
    }

    protected Output.Record formattedRecord(Output.Record record) {
        if (binary) {
            return record;
        } else {
            return new TextRecord(record);
        }
    }

    private class Record implements Output.Record {

        Output.Record fileRecord;
        Output.Record socketRecord;

        public Record() {
            fileRecord = fileOutput.newRecord();
            socketRecord = socketOutput.newRecord();
        }

        @Override
        public Output.Record start(short typeId, short deviceId) {
            fileRecord.start(typeId, deviceId);
            socketRecord.start(typeId, deviceId);
            return this;
        }

        @Override
        public void save() {
            fileRecord.save();
            socketRecord.save();
            synchronized (records) {
                records.add(this);
            }
        }

        @Override
        public Output.Record write(short value) {
            fileRecord.write(value);
            socketRecord.write(value);
            return this;
        }

        @Override
        public Output.Record write(int value) {
            fileRecord.write(value);
            socketRecord.write(value);
            return this;
        }

        @Override
        public Output.Record write(long value) {
            fileRecord.write(value);
            socketRecord.write(value);
            return this;
        }

        @Override
        public Output.Record write(float value) {
            fileRecord.write(value);
            socketRecord.write(value);
            return this;
        }

        @Override
        public Output.Record write(String value) {
            fileRecord.write(value);
            socketRecord.write(value);
            return this;
        }
    }

    private class TextRecord implements Output.Record {

        private Output.Record record;
        private StringBuilder builder;

        public TextRecord(Output.Record record) {
            this.record = record;
            this.builder = new StringBuilder();
        }

        @Override
        public Output.Record start(short typeId, short deviceId) {
            builder.setLength(0);
            builder.append(sensorsRecorder.getTypePrefix(typeId, deviceId));
            return this;
        }

        @Override
        public void save() {
            builder.append(sensorsRecorder.getTextNewLine());
            record.write(builder.toString());
        }

        @Override
        public Output.Record write(short value) {
            builder.append(sensorsRecorder.getTextSeparator());
            builder.append(value);
            return this;
        }

        @Override
        public Output.Record write(int value) {
            builder.append(sensorsRecorder.getTextSeparator());
            builder.append(value);
            return this;
        }

        @Override
        public Output.Record write(long value) {
            builder.append(sensorsRecorder.getTextSeparator());
            builder.append(value);
            return this;
        }

        @Override
        public Output.Record write(float value) {
            builder.append(sensorsRecorder.getTextSeparator());
            builder.append(value);
            return this;
        }

        @Override
        public Output.Record write(String value) {
            builder.append(sensorsRecorder.getTextSeparator());
            builder.append(value);
            return this;
        }

    }

}
