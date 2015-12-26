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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
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
    }

    public Output.Record newRecord() {
        return new SocketRecord(writeLock, writeStream);
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
        if (outputThread != null &&
                ((protocol == SensorsRecorder.PROTOCOL_TCP
                        && outputThread instanceof TcpOutputThread) ||
                        (protocol == SensorsRecorder.PROTOCOL_UDP
                                && outputThread instanceof UdpOutputThread)) &&
                host.equals(outputThread.getHost()) &&
                port == outputThread.getPort() &&
                outputThread.restart()) {
            return;
        }

        if (outputThread != null) {
            outputThread.stop();
            outputThread.join();
            outputThread = null;
        }

        if (protocol == SensorsRecorder.PROTOCOL_TCP) {
            outputThread = new TcpOutputThread(host, port);
        } else if (protocol == SensorsRecorder.PROTOCOL_UDP) {
            outputThread = new UdpOutputThread(host, port);
        }
    }

    public void stop() {
        if (outputThread != null) {
            outputThread.stop();
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
        private static final long MAXIMUM_TIMEOUT = 300000;

        protected String host;
        protected int port;

        protected Thread thread;

        private boolean connected;
        private boolean stopping;

        public OutputThread(String host, int port, String threadName) {
            this.host = host;
            this.port = port;
            thread = new Thread(this, threadName);
            thread.start();
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        protected abstract int getProtocol();

        protected abstract boolean connect();
        protected abstract void disconnect();
        protected abstract void write();

        public void stop() {
            synchronized (writeStream) {
                if (thread != null) {
                    stopping = true;
                    writeStream.notify();
                    thread.interrupt();
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
                        // Reset wait time
                        Log.i(TAG, "Socket output connected");
                        connectWaitTime = FIRST_TIMEOUT;
                    } else {
                        // Increase connection retry time
                        Log.e(TAG, "Socket output connect failed, retry in " + connectWaitTime +
                                "ms");
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

        protected boolean isConnected() {
            synchronized (writeStream) {
                return connected;
            }
        }

        protected void setConnected(boolean connected) {
            synchronized (writeStream) {
                this.connected = connected;
            }
        }

    }

    private class TcpOutputThread extends OutputThread {

        private static final String THREAD_NAME = "TcpOutput";

        private Socket socket;
        private DataOutputStream dataStream;

        public TcpOutputThread(String host, int port) {
            super(host, port, THREAD_NAME);
        }

        @Override
        public int getProtocol() {
            return SensorsRecorder.PROTOCOL_TCP;
        }

        public void onException(IOException ex) {
            notifyError(0);
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException cex) {
                Log.e(TAG, "Error closing socket for write exception: " + cex.getMessage());
            }
            setConnected(false);
        }

        protected boolean connect() {
            notifyConnecting();
            try {
                socket = new Socket(host, port);
                dataStream = new DataOutputStream(socket.getOutputStream());

                writeLock.lock();
                try {
                    recorder.recordStart(newDirectRecord());
                } finally {
                    dataStream.flush();
                    writeLock.unlock();
                }

                setConnected(true);
                notifyConnected();
                return true;
            } catch (SocketException ex) {
                Log.e(TAG, "Connect SocketException: " + ex.getMessage());
                onException(ex);
            } catch (UnknownHostException ex) {
                Log.e(TAG, "Connect UnknownHostException: " + ex.getMessage());
                onException(ex);
            } catch (IOException ex) {
                Log.e(TAG, "Connect IOException: " + ex.getMessage());
                onException(ex);
            }

            return false;
        }

        protected void disconnect() {
            try {
                writeLock.lock();
                try {
                    recorder.recordStop(newDirectRecord());
                } finally {
                    dataStream.flush();
                    writeLock.unlock();
                }

                socket.shutdownOutput();
            } catch (IOException ex) {
                Log.e(TAG, "Disconnect IOException: " + ex.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException cex) {
                    Log.e(TAG, "Error closing socket for disconnect: " + cex.getMessage());
                }
            }

            setConnected(false);
        }

        protected void write() {
            try {
                writeStream.writeTo(socket.getOutputStream(), MAX_PACKET_SIZE);
            } catch (IOException ex) {
                Log.e(TAG, "Error writing stream [" + ex.getClass().getName() + "]: " +
                        ex.getMessage());
                onException(ex);
            }
        }

        private Output.Record newDirectRecord() {
            return output.formatRecord(new Output.DataOutputStreamRecord(writeLock) {

                @Override
                protected DataOutputStream getWriter() {
                    return dataStream;
                }

                @Override
                protected void onException(IOException ex) {
                    super.onException(ex);
                    TcpOutputThread.this.onException(ex);
                }
            });
        }
    }

    private class UdpOutputThread extends OutputThread implements Runnable {

        private static final String THREAD_NAME = "UdpOutput";

        private DatagramSocket socket;
        private DatagramPacket packet;

        public UdpOutputThread(String host, int port) {
            super(host, port, THREAD_NAME);
        }

        @Override
        protected int getProtocol() {
            return SensorsRecorder.PROTOCOL_UDP;
        }

        @Override
        protected boolean connect() {
            notifyConnecting();
            try {
                socket = new DatagramSocket();
                socket.connect(InetAddress.getByName(host), port);
                packet = new DatagramPacket(new byte[0], 0);

                writeLock.lock();
                try {
                    recorder.recordStart(newDirectRecord());
                } finally {
                    writeLock.unlock();
                }

                setConnected(true);
                notifyConnected();
                return true;
            } catch (SocketException ex) {
                Log.e(TAG, "Error creating datagram socket: " + ex.getMessage());
            } catch (UnknownHostException ex) {
                Log.e(TAG, "Error creating datagram socket: " + ex.getMessage());
            } catch (IOException ex) {
                Log.e(TAG, "Error creating datagram socket: " + ex.getMessage());
            }

            return false;
        }

        @Override
        protected void disconnect() {
            writeLock.lock();
            try {
                recorder.recordStop(newDirectRecord());
            } finally {
                writeLock.unlock();
            }

            if (socket != null) {
                socket.disconnect();
                socket.close();
            }
            setConnected(false);
        }

        @Override
        protected void write() {
            try {
                writeStream.writeTo(socket, packet, MAX_PACKET_SIZE);
            } catch (IOException ex) {
                Log.e(TAG, "Error writing stream [" + ex.getClass().getName() + "]: " +
                        ex.getMessage());
                onException(ex);
            }
        }

        public void onException(IOException ex) {
            notifyError(0);
            if (socket != null) {
                socket.close();
            }
            setConnected(false);
        }

        private Output.Record newDirectRecord() {
            return output.formatRecord(new Output.DataOutputStreamRecord(writeLock) {

                private ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
                private DataOutputStream dataStream = new DataOutputStream(byteArray);

                @Override
                protected DataOutputStream getWriter() {
                    return dataStream;
                }

                @Override
                public void save() {
                    super.save();
                    try {
                        if (socket != null) {
                            packet.setData(byteArray.toByteArray());
                            socket.send(packet);
                        }
                    } catch (IOException ex) {
                        Log.e(TAG, "Error writing direct stream [" + ex.getClass().getName() + "]: "
                                + ex.getMessage());
                        UdpOutputThread.this.onException(ex);
                    }
                }

                @Override
                protected void onException(IOException ex) {
                    super.onException(ex);
                    UdpOutputThread.this.onException(ex);
                }
            });
        }

    }

    private class SocketRecord extends Output.DataOutputStreamRecord {

        private final StepReadWriteStream writeStream;
        private final DataOutputStream dataStream;

        public SocketRecord(Lock writeLock, StepReadWriteStream writeStream) {
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

}
