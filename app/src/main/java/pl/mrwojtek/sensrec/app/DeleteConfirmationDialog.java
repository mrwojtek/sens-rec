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

package pl.mrwojtek.sensrec.app;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;

/**
 * Dialog asking for confirmation to delete records.
 */
public class DeleteConfirmationDialog extends DialogFragment implements
        DialogInterface.OnClickListener {

    public static final String DIALOG_TAG = "DeleteConfirmationDialog";

    private static final String ARG_ACTIVATED_COUNT = "activatedCount";
    private static final String ARG_RESULT_TAG = "resultTag";

    public static DeleteConfirmationDialog newInstance(int activatedCount, String resultTag) {
        Bundle args = new Bundle();
        args.putInt(ARG_ACTIVATED_COUNT, activatedCount);
        args.putString(ARG_RESULT_TAG, resultTag);
        DeleteConfirmationDialog dialog = new DeleteConfirmationDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
                R.style.DialogTheme);

        if (savedInstanceState != null) {
            dismissAllowingStateLoss();
            return builder.create();
        }

        int activatedCount = getArguments().getInt(ARG_ACTIVATED_COUNT);
        String message = getResources().getQuantityString(R.plurals.records_delete_confirm,
                activatedCount, activatedCount);
        builder.setMessage(message);
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.setPositiveButton(R.string.action_ok, this);
        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            Fragment fragment = getActivity().getSupportFragmentManager().findFragmentByTag(
                    getArguments().getString(ARG_RESULT_TAG));

            OnDeleteListener listener = null;
            if (fragment instanceof OnDeleteListener) {
                listener = (OnDeleteListener) fragment;
            } else if (getActivity() instanceof OnDeleteListener) {
                listener = (OnDeleteListener) getActivity();
            }

            if (listener != null) {
                listener.onDelete();
            }
        }
    }

    public interface OnDeleteListener {
        void onDelete();
    }
}
