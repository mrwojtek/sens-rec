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

package pl.mrwojtek.sensrec.app.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.w3c.dom.Text;

/**
 * {@link android.preference.SwitchPreference} that fixes
 * https://code.google.com/p/android/issues/detail?id=26194 bug and
 * corrects title text color for older devices.
 */
public class SwitchPreference extends android.preference.SwitchPreference {

    private static final int[] ATTRS = new int[]{ android.R.attr.textColorPrimary };

    private int titleColor;

    public SwitchPreference(Context context) {
        super(context);
        init();
    }

    public SwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @SuppressLint("NewApi")
    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        if (Build.VERSION.SDK_INT < 21) {
            final TypedArray a = getContext().obtainStyledAttributes(ATTRS);
            titleColor = a.getColor(0, 0);
            a.recycle();
        }
    }

    @Override
    public View getView(View convertView, ViewGroup parent) {
        View v = super.getView(convertView, parent);
        if (Build.VERSION.SDK_INT < 21) {
            TextView textView = (TextView) v.findViewById(android.R.id.title);
            textView.setTextColor(titleColor);
        }
        return v;
    }
}
