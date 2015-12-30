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

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import pl.mrwojtek.sensrec.app.R;

/**
 * Various routines to help improve visual appearance and compatibility.
 */
public class MaterialUtils {

    private static Typeface robotoMedium;

    public static Typeface getRobotoMedium(Context context) {
        if (robotoMedium == null) {
            robotoMedium = Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Medium.ttf");
        }
        return robotoMedium;
    }

    /**
     * Substitutes alert dialog title font for Roboto-Medium on older
     * devices.
     *
     * @param context {@link Context} instance
     * @param dialog {@link AlertDialog} to be fixed
     * @return {@link AlertDialog} passed as a {@see dialog} parameter
     */
    public static AlertDialog fixTitle(final Context context, final AlertDialog dialog,
                                       final DialogInterface.OnShowListener onShowListener) {
        if (Build.VERSION.SDK_INT < 21) {
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface d) {
                    TextView tv = (TextView) dialog.getWindow().findViewById(R.id.alertTitle);
                    if (tv != null) {
                        tv.setTypeface(getRobotoMedium(context));
                    }
                    if (onShowListener != null) {
                        onShowListener.onShow(d);
                    }
                }
            });
        }

        return dialog;
    }

    /**
     * Transforms given {@link TextView} to be compatible with
     * {@link android.support.design.widget.TextInputLayout} component.
     * This method removes the distance between font baseline and font
     * bottom by setting it to the bottom margin.
     *
     * @param captionView {@link TextView} representing caption
     */
    public static void transformForSpinner(TextView captionView) {
        Paint.FontMetrics fm = captionView.getPaint().getFontMetrics();
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)
                captionView.getLayoutParams();
        params.bottomMargin = (int) Math.round(Math.ceil(fm.leading - fm.bottom));

        // TODO: Verify this offset for Marshmallow
        if (Build.VERSION.SDK_INT >= 23) {
            params.bottomMargin -= 1;
        }
    }

    /**
     * Calculates the minimum width to be assigned for this
     * {@link EditText} to fit all the given texts.
     *
     * @param editText {@link EditText} for width calculation
     * @param texts set of potential texts to be displayed in this
     *              {@link EditText}
     * @return calculated minimum width
     */
    public static int calculateWidth(EditText editText, String[] texts) {
        float maximumWidth = 0.0f;
        for (String text : texts) {
            maximumWidth = Math.max(maximumWidth, editText.getPaint().measureText(text));
        }
        return editText.getCompoundPaddingLeft() + editText.getCompoundPaddingRight() +
                (int) Math.round(Math.ceil(maximumWidth));
    }

    public static String formatBytesWritten(long written) {
        final String[] formats = new String[]{"%.0fB", "%.0fkB", "%.1fMB", "%.2fGB", "%.3fTB",
                "%.3fPB", "%.3fEB" };
        long remainder = 0;
        int i = 0;
        for (; i + 1 < formats.length && written > 999; ++i) {
            remainder = written % 1000;
            written /= 1000;
        }
        return String.format(formats[i], written + remainder / 1000.0f);
    }

}
