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

import android.annotation.TargetApi;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import pl.mrwojtek.sensrec.SensorsRecorder;
import pl.mrwojtek.sensrec.app.util.MaterialUtils;
import pl.mrwojtek.sensrec.ble.BleRecorder;

/**
 * Dialog for pairing Bluetooth heart rate sensors.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class HeartRateDialog extends DialogFragment implements DialogInterface.OnClickListener {

    public static final String DIALOG_TAG = "HeartRateDialog";
    public static final String TAG = "SensRec";

    public static final int BLUETOOTH_ENABLE_REQUEST_CODE = 100;

    private static final long SCAN_PERIOD = 10000;

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;
    private Runnable scanStopRunnable;
    private boolean scanning;

    private SharedPreferences prefs;
    private ProgressBar progress;
    private Button button;
    private ListView list;
    private DevicesAdapter adapter;

    private int textColorPrimary;
    private int textColorSecondary;
    private int textColorTertiary;

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,
                             byte[] scanRecord) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (adapter.add(device)) {
                        adapter.notifyDataSetChanged();
                    }
                }
            });
        }
    };

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ContextThemeWrapper context = new ContextThemeWrapper(getActivity(), R.style.DialogTheme);
        LayoutInflater inflater = getActivity().getLayoutInflater().cloneInContext(context);
        View view = inflater.inflate(R.layout.heart_rate_dialog, null);

        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        progress = (ProgressBar) view.findViewById(R.id.progress);
        button = (Button) view.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothAdapter.isEnabled()) {
                    scanLeDevice(true);
                } else {
                    requestEnableBluetooth();
                }
            }
        });

        updateProgressAndButton();

        list = (ListView) view.findViewById(R.id.devices_list);
        list.setAdapter(adapter);

        textColorPrimary = ContextCompat.getColor(context, R.color.colorTextPrimary);
        textColorSecondary = ContextCompat.getColor(context, R.color.colorTextSecondary);
        textColorTertiary = ContextCompat.getColor(context, R.color.colorTextHint);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
        builder.setTitle(R.string.ble_title);
        builder.setView(view);
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.setPositiveButton(R.string.action_ok, this);
        return MaterialUtils.fixTitle(context, builder.create(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(getContext().getMainLooper());
        adapter = new DevicesAdapter();

        SensorsRecorder recorder = RecordingService.getRecorder(getActivity());
        SortedMap<Integer, BleRecorder> bleRecorders = recorder.getBleRecorders();
        for (Map.Entry<Integer, BleRecorder> r : bleRecorders.entrySet()) {
            adapter.add(r.getValue());
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth adapter not enabled");
            requestEnableBluetooth();
        } else {
            scanLeDevice(true);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            updateDevices();
        }
    }

    @Override
    public void onDestroy() {
        scanLeDevice(false);
        super.onDestroy();
    }

    private void requestEnableBluetooth() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, BLUETOOTH_ENABLE_REQUEST_CODE);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable && !scanning) {
            scanStopRunnable = new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    updateProgressAndButton();
                    bluetoothAdapter.stopLeScan(leScanCallback);
                    scanStopRunnable = null;
                }
            };

            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(scanStopRunnable, SCAN_PERIOD);

            scanning = true;
            bluetoothAdapter.startLeScan(leScanCallback);
        } else {
            scanning = false;
            bluetoothAdapter.stopLeScan(leScanCallback);
            if (scanStopRunnable != null) {
                handler.removeCallbacks(scanStopRunnable);
            }
        }

        updateProgressAndButton();
    }

    public void updateProgressAndButton() {
        if (progress != null) {
            progress.setVisibility(scanning ? View.VISIBLE : View.INVISIBLE);
        }

        if (button != null) {
            boolean enabled = bluetoothAdapter.isEnabled();
            button.setText(enabled ? R.string.ble_rescan : R.string.ble_enable);
            button.setVisibility(scanning ? View.INVISIBLE : View.VISIBLE);
        }
    }

    public void onBluetoothAdapterState(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                scanLeDevice(false);
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                // Ignore for now
                break;
            case BluetoothAdapter.STATE_ON:
                scanLeDevice(true);
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
                // Ignore for now
                break;
        }
    }

    private void updateDevices() {
        List<Integer> previousIds = new ArrayList<>();
        Set<String> previous = prefs.getStringSet(SensorsRecorder.PREF_BLE_DEVICES, null);
        if (previous != null) {
            for (String address : previous) {
                previousIds.add(prefs.getInt(SensorsRecorder.PREF_BLE_ID_ + address, 0));
            }
        }

        SharedPreferences.Editor editor = prefs.edit();

        int lastId = -1;
        int lastIndex = 0;
        Set<String> devices = new HashSet<>();
        for (int i = 0; i < adapter.getCount(); ++i) {
            Device device = (Device) adapter.getItem(i);
            if (device.isEnabled()) {
                String address = device.getAddress();
                devices.add(address);
                if (previous == null || !previous.contains(address)) {
                    for (++lastId; lastIndex < previousIds.size(); ++lastIndex) {
                        if (previousIds.get(lastIndex) > lastId) {
                            break;
                        } else if (previousIds.get(lastIndex) == lastId) {
                            ++lastId;
                        }
                    }
                }
                editor.putInt(SensorsRecorder.PREF_BLE_ID_ + address, lastId);
                editor.putString(SensorsRecorder.PREF_BLE_NAME_ + address, device.getName());
            }
        }

        if (previous != null) {
            for (String address : previous) {
                if (!devices.contains(address)) {
                    editor.remove(SensorsRecorder.PREF_BLE_ID_ + address);
                    editor.remove(SensorsRecorder.PREF_BLE_NAME_ + address);
                }
            }
        }

        editor.putStringSet(SensorsRecorder.PREF_BLE_DEVICES, devices);
        editor.apply();
    }

    private class Device {
        String name;
        String address;
        boolean enabled;
        private boolean present;

        Device(BleRecorder recorder) {
            name = recorder.getName();
            address = recorder.getAddress();
            enabled = true;
            present = false;
        }

        Device(BluetoothDevice device) {
            name = device.getName();
            address = device.getAddress();
            enabled = false;
            present = true;
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPresent() {
            return present;
        }

        public boolean setPresent(boolean present) {
            if (this.present != present) {
                this.present = present;
                return true;
            }
            return false;
        }
    }

    private class DevicesAdapter extends BaseAdapter {

        private ArrayList<Device> devices;
        private Map<String, Long> deviceId;
        private Map<String, Device> deviceMap;
        private DeviceComparator deviceComparator;

        public DevicesAdapter() {
            devices = new ArrayList<>();
            deviceId = new HashMap<>();
            deviceMap = new HashMap<>();
            deviceComparator = new DeviceComparator();
        }

        public boolean add(BluetoothDevice device) {
            if (!deviceId.containsKey(device.getAddress())) {
                Device d = new Device(device);
                devices.add(d);
                deviceId.put(device.getAddress(), (long) devices.size());
                deviceMap.put(device.getAddress(), d);
                return true;
            } else {
                return deviceMap.get(device.getAddress()).setPresent(true);
            }
        }

        public boolean add(BleRecorder recorder) {
            if (!deviceId.containsKey(recorder.getAddress())) {
                Device d = new Device(recorder);
                devices.add(d);
                deviceId.put(recorder.getAddress(), (long) devices.size());
                deviceMap.put(recorder.getAddress(), d);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void notifyDataSetChanged() {
            Collections.sort(devices, deviceComparator);
            super.notifyDataSetChanged();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public long getItemId(int position) {
            return deviceId.get(devices.get(position).getAddress());
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
        }

        public Device getDevice(int position) {
            return devices.get(position);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            DeviceViewHolder holder;

            if (view == null) {
                LayoutInflater inflater = getActivity().getLayoutInflater();
                view = inflater.inflate(R.layout.ble_device_layout, parent, false);
                holder = new DeviceViewHolder(view);
                view.setTag(holder);
            } else {
                holder = (DeviceViewHolder) view.getTag();
            }

            holder.setDevice(getDevice(position));

            return view;
        }
    }

    private class DeviceViewHolder implements CompoundButton.OnCheckedChangeListener {
        TextView nameText;
        TextView addressText;
        SwitchCompat enabledSwitch;

        Device device;

        DeviceViewHolder(View view) {
            nameText = (TextView) view.findViewById(R.id.name_text);
            addressText = (TextView) view.findViewById(R.id.address_text);
            enabledSwitch = (SwitchCompat) view.findViewById(R.id.enabled_switch);
            enabledSwitch.setOnCheckedChangeListener(this);
        }

        public void setDevice(Device device) {
            this.device = device;

            addressText.setText(device.getAddress());

            String name = device.getName();
            if (!TextUtils.isEmpty(name)) {
                nameText.setText(name);
            } else {
                nameText.setText(R.string.ble_unknown_device);
            }

            enabledSwitch.setChecked(device.isEnabled());

            if (device.isPresent()) {
                nameText.setTextColor(textColorPrimary);
                addressText.setTextColor(textColorSecondary);
            } else {
                addressText.setTextColor(textColorTertiary);
                nameText.setTextColor(textColorTertiary);
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            device.setEnabled(isChecked);
        }
    }

    private static class DeviceComparator implements Comparator<Device> {

        @Override
        public int compare(Device lhs, Device rhs) {
            int c = 0;

            boolean lhsEmpty = TextUtils.isEmpty(lhs.getName());
            boolean rhsEmpty = TextUtils.isEmpty(lhs.getName());
            if (lhsEmpty || rhsEmpty) {
                c = (rhsEmpty ? 1 : 0) - (lhsEmpty ? 1 : 0);
            }

            if (c == 0) {
                c = lhs.getAddress().compareTo(rhs.getAddress());
            }

            return c;
        }
    }
}