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

import android.os.Environment;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Records sensors data to file.
 */
public class FileOutput {

    public static final int ERROR_MEDIA_NOT_MOUNTED = 1;
    public static final int ERROR_DIRECTORY_MISSING = 2;
    public static final int ERROR_OPENING_FILE = 3;
    public static final int ERROR_WRITE_ERROR = 4;

    private static final String TAG = "SensRec";

    private RecorderOutput output;
    private SensorsRecorder recorder;
    private OnFileListener onFileListener;

    private boolean started;
    private FileOutputStream stream;
    private DataOutputStream writer;
    private int written;
    private final Lock writeLock = new ReentrantLock();

    private String lastFileName;
    private Integer lastError;

    public FileOutput(RecorderOutput output, SensorsRecorder recorder) {
        this.output = output;
        this.recorder = recorder;
    }

    public void setOnFileListener(OnFileListener onFileListener) {
        this.onFileListener = onFileListener;
        notifyListener();
    }

    public boolean isStarted() {
        return started;
    }

    public int getBytesWritten() {
        writeLock.lock();
        try {
            return written;
        } finally {
            writeLock.unlock();
        }
    }

    public Output.Record newRecord() {
        return new Output.DataOutputStreamRecord(writeLock) {

            @Override
            protected DataOutputStream getWriter() {
                return writer;
            }

            @Override
            protected void onException(IOException ex) {
                stop(true);
                notifyError(ERROR_WRITE_ERROR);
            }

            @Override
            protected void onWritten(int bytes) {
                written += bytes;
            }
        };
    }

    public void start() {
        // Do not start saving if disabled or recording is not active
        if (!recorder.isSaving() || !recorder.isActive()) {
            return;
        }

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.e(TAG, "External files directory is not mounted");
            notifyError(ERROR_MEDIA_NOT_MOUNTED);
            return;
        }

        File directory = recorder.getContext().getExternalFilesDir(null);
        if (directory == null) {
            Log.e(TAG, "Error accessing external files directory");
            notifyError(ERROR_DIRECTORY_MISSING);
            return;
        }

        String fileName = recorder.getOutputFileName(output.isBinary());
        String currentName = nextFreeName(directory.list(), fileName);

        Log.i(TAG, "Logging to " + currentName);

        writeLock.lock();
        try {
            if (started) {
                Log.w(TAG, "Trying to start second file write");
                return;
            }

            started = true;
            written = 0;
            stream = new FileOutputStream(new File(directory, currentName), false);
            writer = new DataOutputStream(stream);
            recorder.recordStart(output.formatRecord(newRecord()));
            notifyStart(currentName);
        } catch (FileNotFoundException ex) {
            Log.e(TAG, "Error opening file " + currentName + ": " + ex.getMessage());
            stop(true);
            notifyError(ERROR_OPENING_FILE);
        } finally {
            writeLock.unlock();
        }
    }

    private String nextFreeName(String[] files, String fileName) {
        // List all recorded files
        Set<String> set = new HashSet<>(Arrays.asList(files));

        // Find the new file name
        int index = 1;
        String name;
        while (true) {
            name = String.format(fileName, index++);
            if (!set.contains(name)) {
                return name;
            }
        }
    }

    public void stop() {
        stop(false);
    }

    private void stop(boolean quiet) {
        writeLock.lock();
        try {
            if (!started) {
                return;
            }

            started = false;
            if (writer != null) {
                recorder.recordStop(output.formatRecord(newRecord()));
                try {
                    writer.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Error closing file writer: " + ex.getMessage());
                } finally {
                    writer = null;
                }
            }
        } finally {
            writeLock.unlock();
        }

        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ex) {
                Log.e(TAG, "Error closing file stream: " + ex.getMessage());
            } finally {
                stream = null;
            }
        }

        if (!quiet) {
            notifyStop();
        }
    }

    private void notifyListener() {
        if (lastFileName != null) {
            notifyStart(lastFileName);
        } else if (lastError != null) {
            notifyError(lastError);
        } else {
            notifyStop();
        }
    }

    private void notifyError(int error) {
        lastFileName = null;
        lastError = error;
        if (onFileListener != null) {
            onFileListener.onError(error);
        }
    }

    private void notifyStart(String fileName) {
        lastFileName = fileName;
        lastError = null;
        if (onFileListener != null) {
            onFileListener.onStart(fileName);
        }
    }

    private void notifyStop() {
        lastFileName = null;
        lastError = null;
        if (onFileListener != null) {
            onFileListener.onStop();
        }
    }

    public interface OnFileListener {
        void onError(int error);
        void onStart(String fileName);
        void onStop();
    }
}
