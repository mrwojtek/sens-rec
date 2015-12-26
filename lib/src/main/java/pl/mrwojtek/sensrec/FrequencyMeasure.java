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

/**
 * Dynamic measurement of samples frequency.
 */
public class FrequencyMeasure {

    public static final int MEASURE_VALUE = 1;
    public static final int MEASURE_QUIET = 2;
    public static final int MEASURE_AMBIGUOUS = 3;
    public static final int MEASURE_MISSING = 4;
	
	private static final int MAXIMUM_MEASURES = 20;
	private static final int MAXIMUM_INTERVAL = 1000;
	private static final int QUIET_INTERVAL = 5000;

	private long[] measures;
    private int first;
    private int last;
    private int size;

	private int measure = MEASURE_QUIET;
    private float value;

    private int maximumInterval;
    private int quietInterval;

    public FrequencyMeasure() {
        this(MAXIMUM_INTERVAL, QUIET_INTERVAL, MAXIMUM_MEASURES);
    }

    public FrequencyMeasure(int maximumInterval, int quietInterval, int size) {
        this.maximumInterval = maximumInterval;
        this.quietInterval = quietInterval;
        measures = new long[size];
    }

    public synchronized long onNewSample() {
        try {
            return add(SystemClock.elapsedRealtime());
        } finally {
            while (size > 2 && measures[last] - measures[first] > maximumInterval) {
                remove();
            }

            if (size == 2 && measures[last] - measures[first] > quietInterval) {
                remove();
            }
        }
    }

    public int getMeasure() {
        return measure;
    }

    public float getValue() {
        return value;
    }
	
	public synchronized void resolveNow() {
		long millisecond = SystemClock.elapsedRealtime();

        while (size >= 1 && millisecond - measures[first] > quietInterval) {
            remove();
        }

		if (size < 2) {
            measure = MEASURE_QUIET;
        } else {
            long interval = measures[last] - measures[first];
			if (interval != 0) {
                measure = MEASURE_VALUE;
				value = 1000.0f * (size - 1) / interval;
			} else {
                measure = MEASURE_AMBIGUOUS;
            }
		}
	}

    private long add(long value) {
        if (size != 0) {
            if (size == measures.length) {
                last = first;
                first = (first + 1) % measures.length;
            } else {
                last = (last + 1) % measures.length;
                ++size;
            }
        } else {
            ++size;
        }
        return measures[last] = value;
    }

    private void remove() {
        if (--size != 0) {
            first = (first + 1) % measures.length;
        }
    }

}
