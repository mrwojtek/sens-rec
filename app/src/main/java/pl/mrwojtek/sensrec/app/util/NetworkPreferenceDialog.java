package pl.mrwojtek.sensrec.app.util;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

import pl.mrwojtek.sensrec.SensorRecorder;
import pl.mrwojtek.sensrec.SensorsRecorder;
import pl.mrwojtek.sensrec.app.R;

/**
 * Dialog for setting network preferences.
 */
public class NetworkPreferenceDialog extends DialogFragment implements
        DialogInterface.OnClickListener {

    public static final String DIALOG_TAG = "NetworkPreferenceDialog";
    public static final String MAXIMUM_PORT = "65535";

    private Switch networkSwitch;
    private EditText hostEdit;
    private Spinner protocolSpinner;
    private EditText portEdit;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ContextThemeWrapper context = new ContextThemeWrapper(getActivity(), R.style.DialogTheme);
        LayoutInflater inflater = getActivity().getLayoutInflater().cloneInContext(context);
        View view = inflater.inflate(R.layout.network_dialog, null);

        initializePortEdit(view);
        networkSwitch = (Switch) view.findViewById(R.id.network_switch);
        protocolSpinner = (Spinner) view.findViewById(R.id.protocol_spinner);
        hostEdit = (EditText) view.findViewById(R.id.host_edit);
        portEdit = (EditText) view.findViewById(R.id.port_edit);

        if (savedInstanceState == null) {
            setFromPreferences();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
        builder.setTitle(R.string.network_title);
        builder.setView(view);
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.setPositiveButton(R.string.action_ok, this);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                //okButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                //updateOkButton();
            }
        });

        return dialog;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        int port;
        try {
            port = Integer.parseInt(portEdit.getText().toString());
        } catch (NumberFormatException ex) {
            port = 0;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.edit()
                .putString(SensorsRecorder.PREF_NETWORK_HOST, hostEdit.getText().toString())
                .putInt(SensorsRecorder.PREF_NETWORK_PROTOCOL,
                        protocolSpinner.getSelectedItemPosition())
                .putInt(SensorsRecorder.PREF_NETWORK_PORT, port)
                .putBoolean(SensorsRecorder.PREF_NETWORK_SAVE, networkSwitch.isChecked())
                .apply();
    }

    private void setFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        networkSwitch.setChecked(prefs.getBoolean(SensorsRecorder.PREF_NETWORK_SAVE,
                SensorsRecorder.DEFAULT_NETWORK_SAVE));
        hostEdit.setText(prefs.getString(SensorsRecorder.PREF_NETWORK_HOST,
                SensorsRecorder.DEFAULT_HOST));
        protocolSpinner.setSelection(prefs.getInt(SensorsRecorder.PREF_NETWORK_PROTOCOL,
                SensorsRecorder.DEFAULT_PROTOCOL));
        portEdit.setText(Integer.toString(prefs.getInt(SensorsRecorder.PREF_NETWORK_PORT,
                SensorsRecorder.DEFAULT_PORT)));
    }

    private void initializePortEdit(View view) {
        portEdit = (EditText) view.findViewById(R.id.port_edit);
        portEdit.setMinimumWidth(portEdit.getCompoundPaddingLeft() +
                portEdit.getCompoundPaddingRight() + (int) Math.round(Math.ceil(Math.max(
                portEdit.getPaint().measureText(MAXIMUM_PORT),
                portEdit.getPaint().measureText(getString(R.string.network_port))))));
    }

}
