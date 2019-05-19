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

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Circular buffer with concurrent read and write for buffering packets
 * of data.
 */
public class StepReadWriteStream extends OutputStream {

    private final byte[] content;
    private int readPos;
    private int writePos;
    private int count;

    private int markPos;
    private int markCount;
    private boolean valid;

    public StepReadWriteStream(int capacity) {
        content = new byte[capacity];
        readPos = 0;
        writePos = 0;
        markPos = 0;
    }

    public void mark() {
        synchronized (content) {
            valid = true;
            markPos = writePos;
            markCount = count;
        }
    }

    public boolean submit() {
        synchronized (content) {
            if (valid) {
                writePos = markPos;
                count = markCount;
                valid = false;
                return true;
            } else {
                return false;
            }
        }
    }

    public int available() {
        synchronized (content) {
            return count;
        }
    }

    @Override
    public void write(int oneByte) throws IOException {
        synchronized (content) {
            if (valid && markCount + 1 < content.length) {
                content[markPos] = (byte) oneByte;
                markPos = (markPos + 1) % content.length;
                ++markCount;
            } else {
                valid = false;
            }
        }
    }

    @Override
    public void write(@NonNull byte[] buffer, int offset, int count) throws IOException {
        synchronized (content) {
            if (valid && markCount + count < content.length) {
                int space = content.length - markPos;
                if (space >= count) {
                    System.arraycopy(buffer, offset, content, markPos, count);
                    markPos += count;
                } else {
                    int wrap = count - space;
                    System.arraycopy(buffer, offset, content, markPos, space);
                    System.arraycopy(buffer, offset + space, content, 0, wrap);
                    markPos = wrap;
                }
                markCount += count;
            } else {
                valid = false;
            }
        }
    }

    public int writeTo(OutputStream os, int bytes) throws IOException {
        int readCount;
        synchronized (content) {
            readCount = Math.min(count, bytes);
        }
        if (readCount > 0) {
            int space = content.length - readPos;
            if (space >= readCount) {
                os.write(content, readPos, readCount);
            } else {
                os.write(content, readPos, space);
                os.write(content, 0, readCount - space);
            }
            synchronized (content) {
                readPos = (readPos + readCount) % content.length;
                count -= readCount;
                markCount -= readCount;
            }
        }
        return readCount;
    }

    public int writeTo(DatagramSocket socket, DatagramPacket packet, int bytes) throws IOException {
        int readCount;
        synchronized (content) {
            readCount = Math.min(count, bytes);
        }
        if (readCount > 0) {
            int space = content.length - readPos;
            if (space >= readCount) {
                packet.setData(content, readPos, readCount);
                socket.send(packet);
            } else {
                packet.setData(content, readPos, space);
                socket.send(packet);
                packet.setData(content, 0, readCount - space);
                socket.send(packet);
            }
            synchronized (content) {
                readPos = (readPos + readCount) % content.length;
                count -= readCount;
                markCount -= readCount;
            }
        }
        return readCount;
    }

    public int writeTo(WritableByteChannel byteChannel, int bytes) throws IOException {
        int readCount;
        if (byteChannel == null) {
            return 0;
        }
        synchronized (content) {
            readCount = Math.min(count, bytes);
        }
        if (readCount > 0) {
            int space = content.length - readPos;
            if (space >= readCount) {
                byteChannel.write(ByteBuffer.wrap(content, readPos, readCount));
            } else {
                byteChannel.write(ByteBuffer.wrap(content, readPos, space));
                byteChannel.write(ByteBuffer.wrap(content, 0, readCount - space));
            }
            synchronized (content) {
                readPos = (readPos + readCount) % content.length;
                count -= readCount;
                markCount -= readCount;
            }
        }
        return readCount;
    }

}
