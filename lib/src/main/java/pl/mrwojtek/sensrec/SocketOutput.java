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

import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Records sensors data over network.
 */
public class SocketOutput {

    private static final String TAG = "SensRec";

    private static final int BUFFER_CAPACITY = 32768;
    private static final int MAX_PACKET_SIZE = 16384;

    private RecorderOutput output;
    private SensorsRecorder recorder;
    private OutputThread outputThread;
    private OnSocketListener onSocketListener;

    private final Lock writeLock = new ReentrantLock();
    private final StepReadWriteStream writeStream = new StepReadWriteStream(BUFFER_CAPACITY);

    public SocketOutput(RecorderOutput output, SensorsRecorder recorder) {
        this.output = output;
        this.recorder = recorder;
    }

    public void setOnSocketListener(OnSocketListener onSocketListener) {
        this.onSocketListener = onSocketListener;

        if (outputThread != null) {
            outputThread.notifyListener();
        } else {
            notifyStop();
        }
    }

    public Output.Record newRecord() {
        return new StepReadWriteRecord(writeLock, writeStream);
    }

    public void start() {
        // Do not start streaming if disabled or recording is not active
        if (!recorder.isStreaming() || !recorder.isActive()) {
            return;
        }

        String host = recorder.getOutputHost(output.isBinary());
        int port = recorder.getOutputPort(output.isBinary());
        int protocol = recorder.getOutputProtocol(output.isBinary());

        // Try to reuse previous output of applicable
        if (outputThread != null && protocol == outputThread.getProtocol() &&
                port == outputThread.getPort() && host.equals(outputThread.getHost()) &&
                outputThread.restart()) {
            return;
        }

        if (outputThread != null) {
            outputThread.stop(true);
            outputThread.join();
            outputThread = null;
        }

        outputThread = new ChannelOutputThread(host, port, protocol);
    }

    public void stop() {
        if (outputThread != null) {
            outputThread.stop(false);
        }
    }

    protected void notifyError(int error) {
        if (onSocketListener != null) {
            onSocketListener.onError(outputThread.getProtocol(), outputThread.getHost(),
                    outputThread.getPort(), error);
        }
    }

    protected void notifyConnecting() {
        if (onSocketListener != null) {
            onSocketListener.onConnecting(outputThread.getProtocol(), outputThread.getHost(),
                    outputThread.getPort());
        }
    }

    protected void notifyConnected() {
        if (onSocketListener != null) {
            onSocketListener.onConnected(outputThread.getProtocol(), outputThread.getHost(),
                    outputThread.getPort());
        }
    }

    protected void notifyStop() {
        if (onSocketListener != null) {
            onSocketListener.onStop();
        }
    }

    public interface OnSocketListener {
        void onError(int protocol, String host, int port, int error);
        void onConnecting(int protocol, String host, int port);
        void onConnected(int protocol, String host, int port);
        void onStop();
    }

    private abstract class OutputThread implements Runnable {

        private static final long FIRST_TIMEOUT = 5000;
        private static final long MAXIMUM_TIMEOUT = 60000;

        protected String host;
        protected int port;

        protected Thread thread;

        protected boolean connected;
        protected boolean stopping;

        public OutputThread(String host, int port, String threadName) {
            this.host = host;
            this.port = port;
            thread = new Thread(this, threadName);
            thread.start();
        }

        protected abstract int getProtocol();

        protected abstract boolean connect();
        protected abstract void disconnect();
        protected abstract void write();
        protected abstract void stopSocket(boolean force);

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public void stop(boolean force) {
            synchronized (writeStream) {
                if (thread != null) {
                    stopping = true;
                    writeStream.notify();
                    stopSocket(force);
                }
            }
        }

        public boolean restart() {
            synchronized (writeStream) {
                if (thread != null) {
                    stopping = false;
                    return true;
                } else {
                    return false;
                }
            }
        }

        public void join() {
            Thread t;
            synchronized (writeStream) {
                t = thread;
            }
            if (t != null) {
                while (t.isAlive()) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            }
        }

        @Override
        public void run() {
            long connectWaitTime = FIRST_TIMEOUT;
            long connectTime = SystemClock.elapsedRealtime() - connectWaitTime;
            while (true) {
                boolean doConnect = false;
                boolean doDisconnect = false;
                boolean doWrite = false;
                synchronized (writeStream) {
                    if (stopping) {
                        if (connected) {
                            doDisconnect = true;
                        } else {
                            notifyStop();
                            thread = null;
                            return;
                        }
                    } else if (!connected) {
                        long waitTime = connectTime - SystemClock.elapsedRealtime();
                        if (waitTime > 0) {
                            try {
                                writeStream.wait(waitTime);
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                            continue;
                        } else {
                            doConnect = true;
                        }
                    } else if (writeStream.available() == 0) {
                        try {
                            writeStream.wait();
                            continue;
                        } catch (InterruptedException e) {
                            continue;
                        }
                    } else {
                        doWrite = true;
                    }
                }

                if (doConnect) {
                    if (connect()) {
                        Log.i(TAG, "Network connected");

                        // Reset wait time
                        connectWaitTime = FIRST_TIMEOUT;
                    } else {
                        if (!stopping) {
                            Log.e(TAG, "Network connect failed, retry in " +
                                    connectWaitTime + "ms");
                        }

                        // Calculate new connect time and increase connection retry time
                        connectTime = SystemClock.elapsedRealtime() + connectWaitTime;
                        connectWaitTime = Math.min(connectWaitTime << 1, MAXIMUM_TIMEOUT);
                    }
                } else if(doDisconnect) {
                    disconnect();
                } else if (doWrite) {
                    write();
                }
            }
        }

        protected void setConnected(boolean connected) {
            synchronized (writeStream) {
                this.connected = connected;
            }
        }

        public void notifyListener() {
            // TODO: Update activity state
        }

    }

    private class ChannelOutputThread extends OutputThread {

        private static final String THREAD_NAME = "TcpOutput";

        private ByteChannel socket;
        private int protocol;

        public ChannelOutputThread(String host, int port, int protocol) {
            super(host, port, THREAD_NAME);
            this.protocol = protocol;
        }

        @Override
        public int getProtocol() {
            return protocol;
        }

        public void onException(IOException ex) {
            notifyError(0);
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException cex) {
                Log.e(TAG, "Error closing socket for write exception: " + cex.getMessage());
            } finally {
                socket = null;
                setConnected(false);
            }
        }

        protected boolean connect() {
            notifyConnecting();
            try {
                if ((protocol == SensorsRecorder.PROTOCOL_UDP && connectUdp()) ||
                        (protocol == SensorsRecorder.PROTOCOL_TCP && connectTcp())) {
                    writeLock.lock();
                    try {
                        recorder.recordStart(newDirectRecord());
                    } finally {
                        writeLock.unlock();
                    }

                    setConnected(true);
                    notifyConnected();
                    return true;
                }
            } catch (IOException ex) {
                Log.e(TAG, "Connect IOException: " + ex.getMessage());
                onException(ex);
            }

            return false;
        }

        private boolean connectTcp() throws IOException {
            SocketChannel socket;
            synchronized (writeStream) {
                if (!stopping) {
                    this.socket = socket = SocketChannel.open();
                } else {
                    return false;
                }
            }

            socket.connect(new InetSocketAddress(host, port));
            return true;
        }

        private boolean connectUdp() throws IOException {
            DatagramChannel socket;
            synchronized (writeStream) {
                if (!stopping) {
                    this.socket = socket = DatagramChannel.open();
                } else {
                    return false;
                }
            }

            socket.connect(new InetSocketAddress(host, port));
            return true;
        }

        protected void disconnect() {
            writeLock.lock();
            try {
                recorder.recordStop(newDirectRecord());
            } finally {
                writeLock.unlock();
            }

            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException cex) {
                Log.e(TAG, "Error closing socket for disconnect: " + cex.getMessage());
            } finally {
                socket = null;
                setConnected(false);
            }
        }

        protected void write() {
            try {
                writeStream.writeTo(socket, MAX_PACKET_SIZE);
            } catch (IOException ex) {
                Log.e(TAG, "Error writing stream [" + ex.getClass().getName() + "]: " +
                        ex.getMessage());
                onException(ex);
            }
        }

        @Override
        protected void stopSocket(boolean force) {
            if (socket != null && (force || !connected)) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Error stopping socket: " + ex.getMessage());
                }
            }
        }

        private Output.Record newDirectRecord() {
            return output.formatRecord(new ByteChannelRecord(writeLock, socket) {
                @Override
                protected void onChannelException(IOException ex) {
                    ChannelOutputThread.this.onException(ex);
                }
            });
        }

    }

    private class StepReadWriteRecord extends Output.DataOutputStreamRecord {

        private final StepReadWriteStream writeStream;
        private final DataOutputStream dataStream;

        public StepReadWriteRecord(Lock writeLock, StepReadWriteStream writeStream) {
            super(writeLock);
            this.writeStream = writeStream;
            this.dataStream = new DataOutputStream(writeStream);
        }

        @Override
        protected DataOutputStream getWriter() {
            return dataStream;
        }

        @Override
        public Output.Record start(short typeId, short deviceId) {
            super.start(typeId, deviceId);
            writeStream.mark();
            return this;
        }

        @Override
        public void save() {
            synchronized (writeStream) {
                writeStream.submit();
                writeStream.notify();
            }
            super.save();
        }
    }

    private abstract class ByteChannelRecord extends Output.DataOutputStreamRecord {

        private WritableByteChannel channel;
        private ByteArrayOutputStream byteArray;
        private DataOutputStream dataStream;

        public ByteChannelRecord(Lock writeLock, WritableByteChannel channel) {
            super(writeLock);
            this.channel = channel;
            byteArray = new ByteArrayOutputStream();
            dataStream = new DataOutputStream(byteArray);
        }

        @Override
        protected DataOutputStream getWriter() {
            return dataStream;
        }

        @Override
        public void save() {
            super.save();

            try {
                dataStream.flush();
                channel.write(ByteBuffer.wrap(byteArray.toByteArray()));
            } catch (IOException ex) {
                Log.e(TAG, "Error writing byte channel[" + ex.getClass().getName() + "]: "
                        + ex.getMessage());
                onChannelException(ex);
            }
        }

        protected abstract void onChannelException(IOException ex);
    }

}
