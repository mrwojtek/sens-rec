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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;

/**
 * Helper class to obtain basic information from recording files.
 */
public class RecordReader {

    protected static final String TAG = "SensRec";

    protected static final int START_BINARY_LENGTH = 28 + SensorsRecorder.MAGIC_WORD.length();
    protected static final int END_BINARY_LENGTH = 36 + SensorsRecorder.MAGIC_WORD.length();
    protected static final int END_TEXT_EXPECTED_LENGTH = 50 + SensorsRecorder.MAGIC_WORD.length();
    protected static final int END_TEXT_MAX_LENGTH = 1024;

    protected SensorsRecorder recorder;
    protected Date startDate;
    protected Date endDate;
    protected Long duration;
    protected Double length;
    protected Long endMilliseconds;
    protected long startMilliseconds;
    protected int version;
    protected boolean binary;

    protected byte[] startPrefix;
    protected byte[] endPrefix;
    protected byte[] magicBytes;
    protected byte[] magicWord;
    protected byte[] newLineBytes;

    public RecordReader(SensorsRecorder recorder) {
        this.recorder = recorder;
        startPrefix = (recorder.getTypePrefix(SensorsRecorder.TYPE_START, (short) 0) +
                recorder.getTextSeparator() + SensorsRecorder.MAGIC_WORD +
                recorder.getTextSeparator()).getBytes();
        endPrefix = (recorder.getTypePrefix(SensorsRecorder.TYPE_END, (short) 0) +
                recorder.getTextSeparator() + SensorsRecorder.MAGIC_WORD +
                recorder.getTextSeparator()).getBytes();
        magicBytes = SensorsRecorder.MAGIC_WORD.getBytes();
        magicWord = new byte[magicBytes.length];
        newLineBytes = recorder.getTextNewLine().getBytes();
    }

    public Date getStartDate() {
        return startDate;
    }

    public long getStartMilliseconds() {
        return startMilliseconds;
    }

    public Long getDuration() {
        return duration;
    }

    public Double getLength() {
        return length;
    }

    public int getVersion() {
        return version;
    }

    public boolean isBinary() {
        return binary;
    }

    public boolean readStart(File file) {
        RandomAccessFile raf = null;
        try {
            return readStart(raf = new RandomAccessFile(file, "r"), new byte[1024]);
        } catch (FileNotFoundException ex) {
            Log.e(TAG, "Record file not found: " + ex.getMessage());
        } catch (IOException ex) {
            Log.e(TAG, "Record file read error: " + ex.getMessage());
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Record file close error: " + ex.getMessage());
                }
            }
        }
        return false;
    }

    private boolean readStart(RandomAccessFile raf, byte[] buffer) throws IOException {
        int read = raf.read(buffer, 0, Math.max(START_BINARY_LENGTH, startPrefix.length));
        return tryStartBinary(buffer, read) || tryStartText(buffer, read, raf);
    }

    public boolean readStartEnd(File file) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            byte[] buffer = new byte[1024];
            if (readStart(raf, buffer)) {
                if (!readEnd(raf, buffer)) {
                    clearEndData();
                }
                return true;
            }
        } catch (FileNotFoundException ex) {
            Log.e(TAG, "Record file not found: " + ex.getMessage());
        } catch (IOException ex) {
            Log.e(TAG, "Record file read error: " + ex.getMessage());
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Record file close error: " + ex.getMessage());
                }
            }
        }
        return false;
    }

    private void clearEndData() {
        endMilliseconds = null;
        endDate = null;
        duration = null;
        length = null;
    }

    private boolean tryStartBinary(byte[] buffer, int read) throws IOException {
        if (read >= START_BINARY_LENGTH) {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buffer));
            if (dis.readShort() == SensorsRecorder.TYPE_START &&
                    dis.readShort() == 0 &&
                    dis.readInt() == magicBytes.length) {
                if (dis.read(magicWord, 0, magicWord.length) == magicWord.length &&
                        Arrays.equals(magicBytes, magicWord)) {
                    version = dis.readInt();
                    if (version == 1100) {
                        startMilliseconds = dis.readLong();
                        startDate = new Date(dis.readLong());
                        binary = true;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean tryStartText(byte[] buffer, int read, RandomAccessFile raf) throws IOException {
        if (read >= startPrefix.length) {
            if (!equals(buffer, 0, startPrefix, 0, startPrefix.length)) {
                return false;
            }

            raf.seek(startPrefix.length);
            Scanner scanner = new Scanner(raf.getChannel());
            if (scanner.hasNextInt()) {
                version = scanner.nextInt();
                if (version == 1100) {
                    if (!scanner.hasNextLong()) {
                        return false;
                    } else {
                        startMilliseconds = scanner.nextLong();
                    }

                    if (!scanner.hasNextLong()) {
                        return false;
                    } else {
                        startDate = new Date(scanner.nextLong());
                    }

                    return true;
                }
            }
        }
        return false;
    }

    private boolean readEnd(RandomAccessFile raf, byte[] buffer) throws IOException {
        if (binary) {
            return tryEndBinary(buffer, raf);
        } else {
            return tryEndText(buffer, raf);
        }
    }

    private boolean tryEndBinary(byte[] buffer, RandomAccessFile raf) throws IOException {
        if (raf.length() > END_BINARY_LENGTH) {
            raf.seek(raf.length() - END_BINARY_LENGTH);
            if (raf.read(buffer, 0, END_BINARY_LENGTH) == END_BINARY_LENGTH) {
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buffer));

                // Check header
                if (dis.readShort() != SensorsRecorder.TYPE_END ||
                        dis.readShort() != 0 ||
                        dis.readInt() != magicBytes.length) {
                    return false;
                }

                // Check magic word
                if (dis.read(magicWord, 0, magicWord.length) != magicWord.length ||
                        !Arrays.equals(magicBytes, magicWord) ||
                        dis.readInt() != version) {
                    return false;
                }

                // Read data
                endMilliseconds = dis.readLong();
                endDate = new Date(dis.readLong());
                length = dis.readDouble();
                duration = endMilliseconds - startMilliseconds;
                return true;
            }
        }
        return false;
    }

    private boolean tryEndText(byte[] buffer, RandomAccessFile raf) throws IOException {
        long seek = Math.max(0, raf.length() - END_TEXT_EXPECTED_LENGTH);
        int count = (int) (raf.length() - seek);
        int pos = buffer.length - count;
        raf.seek(seek);
        if (raf.read(buffer, pos, count) != count) {
            return false;
        }

        int res = tryEndText(buffer, pos);
        if (res == 0) {
            return true;
        } else if (res == 1) {
            return false;
        } else {
            long secondSeek = Math.max(0, raf.length() - END_TEXT_MAX_LENGTH);
            if (secondSeek == seek) {
                return false;
            }
            count = (int) (seek - secondSeek);
            pos -= count;
            raf.seek(secondSeek);
            return raf.read(buffer, pos, count) == count &&
                    tryEndText(buffer, pos) == 0;
        }
    }

    private int tryEndText(byte[] buffer, int pos) {
        int end = buffer.length;
        if (end - newLineBytes.length > pos &&
                equals(buffer, end - newLineBytes.length, newLineBytes, 0, newLineBytes.length)) {
            end -= newLineBytes.length;
        }

        for (int p = end - 1; p >= pos; --p) {
            if (equals(buffer, p, newLineBytes, 0, newLineBytes.length)) {
                p += newLineBytes.length;
                if (!equals(buffer, p, endPrefix, 0, endPrefix.length)) {
                    return 1;
                }

                p += endPrefix.length;

                Scanner scanner = new Scanner(new ByteArrayInputStream(buffer, p, end - p));
                if (!scanner.hasNextInt() ||
                        scanner.nextInt() != version) {
                    return 1;
                }

                if (!scanner.hasNextLong()) {
                    return 1;
                }
                endMilliseconds = scanner.nextLong();

                if (!scanner.hasNextLong()) {
                    return 1;
                }
                endDate = new Date(scanner.nextLong());

                if (!scanner.hasNextDouble()) {
                    return 1;
                }
                length = scanner.nextDouble();

                duration = endMilliseconds - startMilliseconds;

                return 0;
            }
        }

        return 2;
    }

    private boolean equals(byte[] b1, int p1, byte[] b2, int p2, int count) {
        while (--count > -1) {
            if (b1[p1] != b2[p2]) {
                return false;
            }
        }
        return true;
    }
}
