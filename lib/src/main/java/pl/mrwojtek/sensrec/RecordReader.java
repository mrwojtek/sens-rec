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
    protected static final int END_BINARY_LENGTH_1100 = 36 + SensorsRecorder.MAGIC_WORD.length();
    protected static final int END_BINARY_LENGTH_1200 = 52 + SensorsRecorder.MAGIC_WORD.length();
    protected static final int END_TEXT_EXPECTED_LENGTH = 70 + SensorsRecorder.MAGIC_WORD.length();
    protected static final int END_TEXT_MAX_LENGTH = 1024;
    protected static final int BUFFER_SIZE = 1024;

    protected SensorsRecorder recorder;

    // Start frame data
    protected boolean binary;
    protected int version;
    protected Date startDate;
    protected long startTime;

    // End frame data
    protected Date endDate;
    protected Long endTime;
    protected Long duration;
    protected Long movingDuration;
    protected Double totalDistance;

    protected byte[] startPrefix;
    protected byte[] endPrefix;
    protected byte[] magicBytes;
    protected byte[] magicWord;
    protected byte[] newLineBytes;
    protected byte[] buffer;

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
        buffer = new byte[BUFFER_SIZE];
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public long getStartTime() {
        return startTime;
    }

    public Long getDuration() {
        return duration;
    }

    public Double getTotalDistance() {
        return totalDistance;
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
            return readStart(raf = new RandomAccessFile(file, "r"));
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

    public boolean readStartEnd(File file) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            if (readStart(raf)) {
                if (!readEnd(raf)) {
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
        endTime = null;
        endDate = null;
        duration = null;
        movingDuration = null;
        totalDistance = null;
    }

    private boolean readStart(RandomAccessFile raf) throws IOException {
        int read = raf.read(buffer, 0, Math.max(START_BINARY_LENGTH, startPrefix.length));
        return tryStartBinary(read) || tryStartText(read, raf);
    }

    private boolean tryStartBinary(int read) throws IOException {
        if (read >= START_BINARY_LENGTH) {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buffer));

            // Check start frame header
            if (dis.readShort() != SensorsRecorder.TYPE_START ||
                    dis.readShort() != 0 ||
                    dis.readInt() != magicBytes.length) {
                return false;
            }

            // Check magic word
            if (dis.read(magicWord, 0, magicWord.length) != magicWord.length ||
                    !Arrays.equals(magicBytes, magicWord)) {
                return false;
            }

            // Resolve start frame version and read data
            version = dis.readInt();
            if (version == 1100 || version == 1200) {
                startTime = dis.readLong();
                startDate = new Date(dis.readLong());
                binary = true;
                return true;
            }
        }
        return false;
    }

    private boolean tryStartText(int read, RandomAccessFile raf) throws IOException {
        if (read >= startPrefix.length) {
            // Check start frame header and magic word
            if (!equals(buffer, 0, startPrefix, 0, startPrefix.length)) {
                return false;
            }

            raf.seek(startPrefix.length);
            Scanner scanner = new Scanner(raf.getChannel());
            if (!scanner.hasNextInt()) {
                return false;
            }

            // Resolve start frame version and read data
            version = scanner.nextInt();
            if (version == 1100 || version == 1200) {
                if (!scanner.hasNextLong()) {
                    return false;
                }
                startTime = scanner.nextLong();

                if (!scanner.hasNextLong()) {
                    return false;
                }
                startDate = new Date(scanner.nextLong());

                return true;
            }
        }
        return false;
    }

    private boolean readEnd(RandomAccessFile raf) throws IOException {
        if (binary) {
            return tryEndBinary(raf);
        } else {
            return tryEndText(raf);
        }
    }

    private boolean tryEndBinary(RandomAccessFile raf) throws IOException {
        int binaryLength = version == 1200 ? END_BINARY_LENGTH_1200 : END_BINARY_LENGTH_1100;
        if (raf.length() > binaryLength) {
            // Read last bytes that could potentially be an ending frame from file
            raf.seek(raf.length() - binaryLength);
            if (raf.read(buffer, 0, binaryLength) != binaryLength) {
                return false;
            }

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buffer));

            // Check end frame header
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

            // Read end frame data
            if (version == 1100 || version == 1200) {
                endTime = dis.readLong();
                endDate = new Date(dis.readLong());

                if (version == 1200) {
                    duration = dis.readLong();
                    movingDuration = dis.readLong();
                } else {
                    duration = endTime - startTime;
                }

                totalDistance = dis.readDouble();
                return true;
            }
        }
        return false;
    }

    private boolean tryEndText(RandomAccessFile raf) throws IOException {
        // First, try to parse end frame of expected length
        long seek = Math.max(0, raf.length() - END_TEXT_EXPECTED_LENGTH);
        int count = (int) (raf.length() - seek);
        int pos = buffer.length - count;

        // Read expected length bytes
        raf.seek(seek);
        if (raf.read(buffer, pos, count) != count) {
            return false;
        }

        int res = tryEndText(pos);
        if (res == 0) {
            return true;
        } else if (res == 1) {
            return false;
        } else {
            // Frame was not resolved, try the maximum size
            long secondSeek = Math.max(0, raf.length() - END_TEXT_MAX_LENGTH);
            if (secondSeek == seek) {
                return false;
            }
            count = (int) (seek - secondSeek);
            pos -= count;

            // Read the missing bytes
            raf.seek(secondSeek);
            return raf.read(buffer, pos, count) == count && tryEndText(pos) == 0;
        }
    }

    private int tryEndText(int pos) {
        int end = buffer.length;
        int endNewLine = end - newLineBytes.length;
        if (endNewLine > pos && equals(buffer, endNewLine, newLineBytes, 0, newLineBytes.length)) {
            end = endNewLine;
        }

        // Search for end frame preceeding new line
        for (int p = end - 1; p >= pos; --p) {
            if (equals(buffer, p, newLineBytes, 0, newLineBytes.length)) {
                // New line found, skip it
                p += newLineBytes.length;

                // Match end frame header
                if (!equals(buffer, p, endPrefix, 0, endPrefix.length)) {
                    return 1;
                }
                p += endPrefix.length;

                // Resolve version
                Scanner scanner = new Scanner(new ByteArrayInputStream(buffer, p, end - p));
                if (!scanner.hasNextInt() || scanner.nextInt() != version) {
                    return 1;
                }

                // Read end frame data
                if (version == 1100 || version == 1200) {
                    if (!scanner.hasNextLong()) {
                        return 1;
                    }
                    endTime = scanner.nextLong();

                    if (!scanner.hasNextLong()) {
                        return 1;
                    }
                    endDate = new Date(scanner.nextLong());

                    if (version == 1200) {
                        if (!scanner.hasNextLong()) {
                            return 1;
                        }
                        duration = scanner.nextLong();

                        if (!scanner.hasNextLong()) {
                            return 1;
                        }
                        movingDuration = scanner.nextLong();
                    } else {
                        duration = endTime - startTime;
                    }

                    if (!scanner.hasNextDouble()) {
                        return 1;
                    }
                    totalDistance = scanner.nextDouble();

                    return 0;
                }
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
