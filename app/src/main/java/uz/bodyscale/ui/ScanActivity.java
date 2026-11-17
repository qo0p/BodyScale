package uz.bodyscale.ui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uz.bodyscale.R;
import uz.bodyscale.algo.BodyMath;
import uz.bodyscale.algo.Profile;
import uz.bodyscale.ble.BleScaleClient;
import uz.bodyscale.ble.ChipseaProtocol;
import uz.bodyscale.ble.ScaleAdvertisement;
import uz.bodyscale.data.HistoryStore;
import uz.bodyscale.data.Measurement;

/** Scans for BLE scales and turns the first complete reading into a result. */
public class ScanActivity extends Activity implements BleScaleClient.Listener {

    private static final int REQ_PERM = 7;

    private final List<Device> devices = new ArrayList<>();
    private final Map<String, Device> byMac = new HashMap<>();
    private DeviceAdapter adapter;
    private BleScaleClient client;
    private Profile profile;
    private TextView status;
    private TextView liveWeight;
    private ListView list;
    private boolean connecting;
    private float pendingWeight;
    private float pendingImpedance;
    private long stableSince;
    private long lastSaved;

    private static class Device {
        BluetoothDevice device;
        String name;
        int rssi;
        ScaleAdvertisement adv;

        Device(BluetoothDevice d, String n, int r) {
            device = d;
            name = n;
            rssi = r;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        Ui.applyInsets(this);

        profile = Profile.load(this);
        status = findViewById(R.id.status);
        liveWeight = findViewById(R.id.liveWeight);
        list = findViewById(R.id.deviceList);
        adapter = new DeviceAdapter();
        list.setAdapter(adapter);
        findViewById(R.id.back).setOnClickListener(v -> finish());

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= devices.size()) return;
            Device d = devices.get(position);
            if (d.adv != null && !d.adv.connectable) {
                android.widget.Toast.makeText(this,
                        getString(R.string.scan_broadcast_only),
                        android.widget.Toast.LENGTH_LONG).show();
            } else {
                connectTo(d);
            }
        });


        client = new BleScaleClient(this, this);
        client.setProfile(1, profile.sex, profile.age, profile.heightCm, 70f);

        if (hasPermissions()) startScan();
        else requestPermissions(neededPermissions(), REQ_PERM);
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private String[] neededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            if (hasPermissions()) startScan();
            else status.setText(R.string.scan_permission_required);
        }
    }

    private void startScan() {
        status.setText(R.string.scan_scanning);
        client.startScan();
    }

    private void connectTo(Device d) {
        if (connecting) return;
        connecting = true;
        list.setVisibility(View.GONE);
        liveWeight.setText("");
        status.setText(getString(R.string.scan_connecting_to, d.name));
        profile.deviceMac = d.device.getAddress();
        profile.deviceName = d.name;
        profile.save(this);
        client.connect(d.device);
    }

    @Override
    public void onScanResult(BluetoothDevice device, int rssi, String name, ScaleAdvertisement adv) {
        if (connecting) return;
        String mac = device.getAddress();
        boolean saved = !profile.deviceMac.isEmpty() && profile.deviceMac.equalsIgnoreCase(mac);
        if (adv == null && !saved) return;

        Device existing = byMac.get(mac);
        if (existing != null) {
            existing.rssi = rssi;
            if (adv != null) existing.adv = adv;
            if (name != null && !name.isEmpty()) existing.name = name;
        } else {
            String label = adv != null ? getString(R.string.scan_scale_name) : ((name == null || name.isEmpty()) ? getString(R.string.scan_unknown) : name);
            Device d = new Device(device, label, rssi);
            d.adv = adv;
            byMac.put(mac, d);
            devices.add(d);
        }
        java.util.Collections.sort(devices, (a, b) -> Integer.compare(b.rssi, a.rssi));
        adapter.notifyDataSetChanged();

        if (adv != null && adv.complete && adv.weightKg > 20f) {
            long now = System.currentTimeMillis();
            if (pendingWeight == 0f || Math.abs(adv.weightKg - pendingWeight) > 0.15f) {
                pendingWeight = adv.weightKg;
                pendingImpedance = adv.impedance;
                stableSince = now;
            } else if (now - stableSince >= 1200 && now - lastSaved > 4000) {
                lastSaved = now;
                finishFromBroadcast(adv.weightKg, adv.impedance);
            }
        }

        // These scales advertise non-connectable frames; a tap is required to try GATT.
    }

    private void finishFromBroadcast(float weight, float impedance) {
        Measurement m = new Measurement();
        m.fromScale = true;
        m.time = System.currentTimeMillis();
        m.weightKg = weight;
        m.impedance = impedance;
        if (impedance <= 0) {
            status.setText(R.string.scan_no_impedance);
            return;
        }
        BodyMath.build(m, profile);
        HistoryStore.add(this, m);
        try {
            Intent i = new Intent(this, ResultActivity.class);
            i.putExtra(ResultActivity.EXTRA, m.toJson().toString());
            startActivity(i);
            finish();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onState(String s) {
        status.setText(s);
        if (getString(R.string.scan_disconnected).equals(s) && !isFinishing()) {
            connecting = false;
            list.setVisibility(View.VISIBLE);
            client.startScan();
        }
    }

    @Override
    public void onWeight(float kg) {
        liveWeight.setText(Ui.f1(kg) + " " + getString(R.string.unit_kg));
        status.setText(R.string.scan_measuring);
    }

    @Override
    public void onReading(ChipseaProtocol.Reading r) {
        Measurement m = new Measurement();
        m.fromScale = true;
        m.time = System.currentTimeMillis();
        m.weightKg = r.weightKg;
        m.fatPct = r.fatPct;
        m.waterPct = r.waterPct;
        m.musclePct = r.musclePct;
        m.visceral = r.visceral;
        m.boneKg = r.boneKg;
        m.bmr = r.bmr;
        BodyMath.build(m, profile);

        HistoryStore.add(this, m);
        try {
            Intent i = new Intent(this, ResultActivity.class);
            i.putExtra(ResultActivity.EXTRA, m.toJson().toString());
            startActivity(i);
            finish();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onRawFrame(String hex) {
        TextView hint = findViewById(R.id.liveHint);
        hint.setText(hex.length() > 90 ? hex.substring(0, 90) + "…" : hex);
    }

    @Override
    public void onError(String message) {
        status.setText(message);
        connecting = false;
        list.setVisibility(View.VISIBLE);
        client.startScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null) client.close();
    }

    private class DeviceAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.item_device, parent, false);
            }
            Device d = devices.get(position);
            TextView name = v.findViewById(R.id.deviceName);
            TextView mac = v.findViewById(R.id.deviceMac);
            TextView rssi = v.findViewById(R.id.deviceRssi);

            boolean isSaved = !profile.deviceMac.isEmpty() && profile.deviceMac.equalsIgnoreCase(d.device.getAddress());
            name.setText(d.name + (isSaved ? getString(R.string.scan_saved) : ""));
            String sub = d.device.getAddress();
            if (d.adv != null) {
                sub += "  ·  " + getString(R.string.scan_frame, d.adv.version);
                if (d.adv.weightKg > 0) sub += ", " + Ui.f1(d.adv.weightKg) + " " + getString(R.string.unit_kg);
                if (d.adv.complete) sub += ", " + getString(R.string.scan_ready);
                if (d.adv.encrypted) sub += ", " + getString(R.string.scan_encrypted);
            }
            mac.setText(sub);
            rssi.setText(d.rssi + " dBm");
            return v;
        }
    }
}
