package uz.bodyscale.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import uz.bodyscale.R;

import java.util.UUID;

/**
 * Minimal BLE client for Chipsea-based scales, with fallbacks for the standard
 * Bluetooth SIG Weight Scale (0x181D) and Body Composition (0x181B) services.
 */
@SuppressLint("MissingPermission")
public class BleScaleClient {

    public interface Listener {
        void onScanResult(BluetoothDevice device, int rssi, String name, ScaleAdvertisement adv);
        void onState(String state);
        void onWeight(float kg);
        void onReading(ChipseaProtocol.Reading reading);
        void onRawFrame(String hex);
        void onError(String message);
    }

    private static final UUID SVC_CHIPSEA = uuid16(0xFFF0);
    private static final UUID CH_CHIPSEA_TX = uuid16(0xFFF2);
    private static final UUID CH_CHIPSEA_RX = uuid16(0xFFF1);
    private static final UUID SVC_BODY_COMP = uuid16(0x181B);
    private static final UUID CH_BODY_COMP = uuid16(0x2A9C);
    private static final UUID SVC_WEIGHT = uuid16(0x181D);
    private static final UUID CH_WEIGHT = uuid16(0x2A9D);
    private static final UUID CCCD = uuid16(0x2902);

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ChipseaProtocol protocol = new ChipseaProtocol();

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private BluetoothGattCharacteristic notifyChar;
    private boolean chipsea;
    private boolean scanning;

    private int roleId = 1;
    private int sex = 1;
    private int age = 30;
    private int height = 175;
    private float weight = 70f;

    private boolean compositionSent;
    private BluetoothDevice device;
    private int attempt;

    public BleScaleClient(Context ctx, Listener listener) {
        this.context = ctx.getApplicationContext();
        this.listener = listener;
    }

    private void emitState(String s) { handler.post(() -> listener.onState(s)); }
    private void emitWeight(float kg) { handler.post(() -> listener.onWeight(kg)); }
    private void emitReading(ChipseaProtocol.Reading r) { handler.post(() -> listener.onReading(r)); }
    private void emitError(String s) { handler.post(() -> listener.onError(s)); }
    private void emitRaw(String hex) { handler.post(() -> listener.onRawFrame(hex)); }

    public void setProfile(int roleId, int sex, int age, int heightCm, float weightKg) {
        this.roleId = roleId;
        this.sex = sex;
        this.age = age;
        this.height = heightCm;
        this.weight = weightKg;
    }

    public void startScan() {
        if (scanning) return;
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm == null) {
            emitError(context.getString(R.string.scan_bluetooth_unavailable));
            return;
        }
        BluetoothAdapter adapter = bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            emitError(context.getString(R.string.scan_bluetooth_off));
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            emitError(context.getString(R.string.scan_scanner_unavailable));
            return;
        }
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(null, settings, scanCallback);
        scanning = true;
        emitState(context.getString(R.string.scan_scanning));
    }

    public void stopScan() {
        if (scanner != null && scanning) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {
            }
            scanning = false;
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice d = result.getDevice();
            String name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
            byte[] raw = result.getScanRecord() != null ? result.getScanRecord().getBytes() : null;
            android.util.Log.i("BodyScale", "adv " + d.getAddress() + " rssi=" + result.getRssi()
                    + " connectable=" + result.isConnectable() + " name=" + name
                    + " data=" + ChipseaProtocol.hex(raw));
            ScaleAdvertisement adv = ScaleAdvertisement.parse(raw, d.getAddress());
            if (adv != null) adv.connectable = result.isConnectable();
            final ScaleAdvertisement parsed = adv;
            final String label = name;
            final int rssi = result.getRssi();
            handler.post(() -> listener.onScanResult(d, rssi, label, parsed));
        }

        @Override
        public void onScanFailed(int errorCode) {
            emitError(context.getString(R.string.scan_failed, errorCode));
        }
    };

    public void connect(BluetoothDevice device) {
        this.device = device;
        this.attempt = 0;
        connectNow();
    }

    private void connectNow() {
        stopScan();
        attempt++;
        emitState(context.getString(attempt > 1 ? R.string.scan_reconnecting : R.string.scan_connecting));
        handler.postDelayed(() -> {
            if (gatt != null) {
                try { gatt.close(); } catch (Exception ignored) {}
                gatt = null;
            }
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        }, attempt > 1 ? 900 : 300);
    }

    public void close() {
        stopScan();
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) {
            }
            gatt = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                emitState(context.getString(R.string.scan_connected));
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (!compositionSent && attempt < 3) {
                    emitState(context.getString(R.string.scan_retrying));
                    handler.postDelayed(() -> connectNow(), 1200);
                } else {
                    emitState(context.getString(R.string.scan_disconnected));
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService cs = g.getService(SVC_CHIPSEA);
            if (cs != null && cs.getCharacteristic(CH_CHIPSEA_TX) != null) {
                chipsea = true;
                writeChar = cs.getCharacteristic(CH_CHIPSEA_RX);
                notifyChar = cs.getCharacteristic(CH_CHIPSEA_TX);
            } else {
                chipsea = false;
                BluetoothGattService bc = g.getService(SVC_BODY_COMP);
                BluetoothGattService ws = g.getService(SVC_WEIGHT);
                if (bc != null) {
                    writeChar = null;
                    notifyChar = bc.getCharacteristic(CH_BODY_COMP);
                } else if (ws != null) {
                    writeChar = null;
                    notifyChar = ws.getCharacteristic(CH_WEIGHT);
                }
            }
            if (notifyChar == null) {
                emitError(context.getString(R.string.scan_no_service));
                return;
            }
            enableNotifications(g, notifyChar);
            emitState(context.getString(R.string.scan_waiting));

            handler.postDelayed(() -> {
                if (chipsea) sendProfile();
                else emitState(context.getString(R.string.scan_step_on));
            }, 700);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            byte[] data = c.getValue();
            if (data == null) return;
            emitRaw(ChipseaProtocol.hex(data));
            if (!chipsea) {
                parseStandard(data, c.getUuid());
                return;
            }
            ChipseaProtocol.Reading r = protocol.parse(data);
            if (r == null) return;
            if (r.weightKg > 0) emitWeight(r.weightKg);
            if (r.complete) {
                compositionSent = true;
                emitReading(r);
            }
        }
    };

    private void sendProfile() {
        if (gatt == null || writeChar == null) return;
        byte[] p = ChipseaProtocol.roleInfo(roleId, sex, age, height, weight);
        write(writeChar, p);
        handler.postDelayed(() -> write(writeChar, ChipseaProtocol.roleInfo(roleId, sex, age, height, weight)), 250);
        handler.postDelayed(() -> {
            if (!compositionSent) emitState(context.getString(R.string.scan_step_on));
        }, 900);
    }

    private void write(BluetoothGattCharacteristic c, byte[] value) {
        if (gatt == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else {
                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                c.setValue(value);
                gatt.writeCharacteristic(c);
            }
        } catch (Exception e) {
            emitError(context.getString(R.string.scan_write_failed, e.getMessage()));
        }
    }

    private void enableNotifications(BluetoothGatt g, BluetoothGattCharacteristic c) {
        try {
            g.setCharacteristicNotification(c, true);
            BluetoothGattDescriptor d = c.getDescriptor(CCCD);
            if (d != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(d);
                }
            }
        } catch (Exception e) {
            emitError(context.getString(R.string.scan_subscribe_failed, e.getMessage()));
        }
    }

    /* ------------------------------------------------------------ */
    /* Standard BLE services (0x181B / 0x181D)                      */
    /* ------------------------------------------------------------ */

    private void parseStandard(byte[] d, UUID uuid) {
        if (d.length < 3) return;
        if (CH_WEIGHT.equals(uuid)) {
            // Weight Measurement: flags(1) + weight(2, little endian)
            int flags = d[0] & 0xFF;
            boolean si = (flags & 0x01) != 0;
            int raw = (d[1] & 0xFF) | ((d[2] & 0xFF) << 8);
            float kg = si ? raw * 0.005f : raw * 0.01f * 0.4535924f;
            if (kg > 0) listener.onWeight(kg);
            return;
        }
        // Body Composition Measurement (0x2A9C), little endian fields.
        int flags = (d[0] & 0xFF) | ((d[1] & 0xFF) << 8);
        int i = 2;
        boolean si = (flags & 0x0001) != 0;
        if ((flags & 0x0002) != 0) i += 7;
        if ((flags & 0x0004) != 0) i += 1;
        if (i + 2 > d.length) return;

        ChipseaProtocol.Reading r = new ChipseaProtocol.Reading();
        int bodyFat = u16le(d, i); i += 2;
        r.fatPct = bodyFat * 0.1f;
        if ((flags & 0x0008) != 0) {
            i += 2; // basal metabolism
        }
        if ((flags & 0x0010) != 0) {
            r.musclePct = u16le(d, i) * 0.1f;
            i += 2;
        }
        if ((flags & 0x0020) != 0) i += 2; // muscle mass
        if ((flags & 0x0040) != 0) i += 2; // fat free mass
        if ((flags & 0x0080) != 0) i += 2; // soft lean mass
        if ((flags & 0x0100) != 0) i += 2; // body water mass
        if ((flags & 0x0200) != 0) { // impedance
            float imp = u16le(d, i) * 0.1f;
            i += 2;
            if (imp > 0) r.weightKg = 0;
        }
        if ((flags & 0x0400) != 0 && i + 2 <= d.length) {
            int raw = u16le(d, i);
            r.weightKg = si ? raw * 0.005f : raw * 0.01f * 0.4535924f;
            i += 2;
        }
        if (r.weightKg > 0) emitWeight(r.weightKg);
        if (r.fatPct > 0 && r.weightKg > 0) {
            r.complete = true;
            emitReading(r);
        }
    }

    private static int u16le(byte[] d, int p) {
        if (p + 1 >= d.length) return 0;
        return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8);
    }

    private static UUID uuid16(int v) {
        return UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", v));
    }
}
