package uz.bodyscale.ble;

/**
 * Decoder for Chipsea / OKOK BLE advertisements.
 *
 * <p>The original app does not list every BLE device: it inspects the
 * manufacturer-specific AD record and only accepts payloads that start with
 * 0xC0, 0xCA or 0xFF (see chipseaBroadcastFrame in com.chipsea.btcontrol.en).
 * Those frames also carry the live weight and, on v2 frames, the impedance, so a
 * scale can be recognised before any GATT connection is made.
 */
public final class ScaleAdvertisement {

    public boolean detected;
    public int version;
    public float weightKg;
    public float impedance;
    public int productId;
    public boolean encrypted;
    public boolean complete;
    public boolean connectable;
    public String mac = "";

    private ScaleAdvertisement() {}

    /** Parses the raw scan record (AD structures) and returns a detected scale or null. */
    public static ScaleAdvertisement parse(byte[] record, String sourceMac) {
        byte[] man = manufacturerData(record);
        if (man == null || man.length < 3) return null;

        int head = man[0] & 0xFF;
        ScaleAdvertisement a = new ScaleAdvertisement();
        a.mac = sourceMac;
        try {
            if (head == 0xC0) {
                a.detected = parseV2(man, a);
            } else if (head == 0xCA) {
                a.detected = true;
                if ((man[1] & 0xFF) == 0x20) parseBroadcast(man, a);
                else parseLegacy(man, a);
            } else if (head == 0xFF) {
                a.detected = true;
                parseCloud(man, a);
            }
        } catch (Exception e) {
            return null;
        }
        return a.detected ? a : null;
    }

    /** 0xC0 frames: live weight + impedance, optionally encrypted. */
    private static boolean parseV2(byte[] b, ScaleAdvertisement a) {
        if (b.length < 9) return false;
        a.version = 2;
        a.encrypted = (b[4] & 0x80) == 0x80;
        a.productId = ((b[6] & 0xFF) << 8) | (b[7] & 0xFF);
        int prop = b[8] & 0xFF;
        if (a.encrypted) {
            // Weight and impedance are XOR-rotated with a per-device key that
            // only the vendor server knows, so nothing usable can be read here.
            return true;
        }
        a.weightKg = ChipseaProtocol.weightKg(b[2], b[3], prop);
        a.impedance = (((b[4] & 0x7F) << 8) | (b[5] & 0xFF)) / 10f;
        // Bit 0 of the property byte marks a finished measurement.
        a.complete = (prop & 0x01) == 1;
        return true;
    }

    /** 0xCA 0x20 frames: weight and impedance. */
    private static void parseBroadcast(byte[] b, ScaleAdvertisement a) {
        if (b.length < 14) return;
        a.version = 2;
        a.productId = ((b[3] & 0xFF) << 24) | ((b[4] & 0xFF) << 16) | ((b[5] & 0xFF) << 8) | (b[6] & 0xFF);
        int prop = b[8] & 0xFF;
        a.weightKg = ChipseaProtocol.weightKg(b[10], b[11], prop);
        a.impedance = (((b[12] & 0xFF) << 8) | (b[13] & 0xFF)) / 10f;
    }

    /** 0xCA legacy frames: weight only. */
    private static void parseLegacy(byte[] b, ScaleAdvertisement a) {
        int ver = b[1] & 0xFF;
        a.version = ver == 0x11 ? 1 : 0;
        int prop;
        int deviceType;
        if (ver == 0x10) {
            deviceType = b[3] & 0xFF;
            prop = b[4] & 0xFF;
        } else {
            prop = b[3] & 0xFF;
            deviceType = b[4] & 0xFF;
        }
        if (deviceType != 0 && b.length >= 11) {
            a.weightKg = ChipseaProtocol.weightKg(b[5], b[6], prop);
            a.productId = ((b[7] & 0xFF) << 24) | ((b[8] & 0xFF) << 16) | ((b[9] & 0xFF) << 8) | (b[10] & 0xFF);
        } else if (ver == 0x10 && b.length >= 10) {
            a.weightKg = (((b[8] & 0xFF) << 8) | (b[9] & 0xFF)) / 10f;
        }
    }

    /** 0xFF cloud frames: weight only. */
    private static void parseCloud(byte[] b, ScaleAdvertisement a) {
        if (b.length < 6) return;
        a.version = 0;
        int prop = b[3] & 0xFF;
        a.weightKg = ChipseaProtocol.weightKg(b[5], b[4], prop);
    }

    /** Walks the AD structures and returns the 0xFF (manufacturer) payload. */
    private static byte[] manufacturerData(byte[] rec) {
        if (rec == null) return null;
        int i = 0;
        while (i < rec.length) {
            int len = rec[i] & 0xFF;
            if (len == 0 || i + len >= rec.length + 1) break;
            int type = rec[i + 1] & 0xFF;
            if (type == 0xFF && len > 1) {
                byte[] out = new byte[len - 1];
                System.arraycopy(rec, i + 2, out, 0, len - 1);
                return out;
            }
            i += len + 1;
        }
        return null;
    }
}
