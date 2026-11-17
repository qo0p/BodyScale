package uz.bodyscale.ble;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chipsea scale protocol.
 *
 * <p>Frames start with 0xCA. Two frame families matter for measuring:
 * <ul>
 *   <li>0x10 - weight plus the body composition the scale computed itself</li>
 *   <li>0x11 - weight only (sub type 0/1), multi-packet composition (0x12/0x13)
 *       and the role-match answer (0x14)</li>
 * </ul>
 *
 * <p>Field offsets and scaling were taken from the decompiled
 * {@code chipseaStraightFrame} / {@code syncChipseaInstruction} classes.
 */
public final class ChipseaProtocol {

    public static final int FRAME_HEAD = 0xCA;
    public static final int TYPE_COMPOSITION = 0x10;
    public static final int TYPE_STREAM = 0x11;

    public ChipseaProtocol() {}

    public static final class Reading {
        public float weightKg;
        public float fatPct;
        public float waterPct;
        public float musclePct;
        public float visceral;
        public float boneKg;
        public int bmr;
        public boolean complete;
        public int roleId;
    }

    /** Accumulates multi-packet body composition frames. */
    private final Map<Integer, byte[]> packets = new HashMap<>();
    private int packetsExpected;

    public void reset() {
        packets.clear();
        packetsExpected = 0;
    }

    public Reading parse(byte[] d) {
        if (d == null || d.length < 5) return null;
        if ((d[0] & 0xFF) != FRAME_HEAD) return null;
        int type = d[1] & 0xFF;

        if (type == TYPE_COMPOSITION) {
            return parseComposition(d);
        }
        if (type == TYPE_STREAM) {
            return parseStream(d);
        }
        return null;
    }

    private Reading parseComposition(byte[] d) {
        if (d.length < 18) return null;
        Reading r = new Reading();
        int prop = d[4] & 0xFF;
        r.weightKg = weightKg(d[5], d[6], prop);

        // Bit 0 of the property byte marks a frame that carries composition.
        if ((prop & 1) == 0) return r;

        r.fatPct = pct(d[7], d[8]);
        r.waterPct = pct(d[9], d[10]);
        r.musclePct = pct(d[11], d[12]);
        r.bmr = u16(d[13], d[14]);
        r.visceral = round1(u16(d[15], d[16]) / 10f);
        r.boneKg = round1((d[17] & 0xFF) / 10f);
        r.complete = true;
        return r;
    }

    private Reading parseStream(byte[] d) {
        int sub = d[3] & 0xFF;
        switch (sub) {
            case 0:
            case 1: {
                if (d.length < 12) return null;
                Reading r = new Reading();
                int prop = d[11] & 0xFF;
                r.weightKg = weightKg(d[5], d[6], prop);
                return r;
            }
            case 18:
            case 19: {
                byte[] payload = assemble(d);
                if (payload == null) return null;
                return parseMulti(payload, sub == 19);
            }
            case 20: {
                // Role match answer: weight + the role ids stored on the scale.
                return null;
            }
            default:
                return null;
        }
    }

    private byte[] assemble(byte[] d) {
        if (d.length < 6) return null;
        int len = (d[2] & 0xFF) - 2;
        if (len <= 0 || d.length < 5 + len) return null;
        int pack = d[4] & 0xFF;
        int index = pack >> 4;
        int total = pack & 0x0F;
        if (total <= 0 || total > 16) return null;
        packetsExpected = total;

        byte[] payload = new byte[len];
        System.arraycopy(d, 5, payload, 0, len);
        packets.put(index, payload);

        if (packets.size() < packetsExpected) return null;
        int size = 0;
        for (int i = 1; i <= packetsExpected; i++) {
            byte[] p = packets.get(i);
            if (p == null) return null;
            size += p.length;
        }
        byte[] all = new byte[size];
        int off = 0;
        for (int i = 1; i <= packetsExpected; i++) {
            byte[] p = packets.get(i);
            System.arraycopy(p, 0, all, off, p.length);
            off += p.length;
        }
        reset();
        return all;
    }

    private Reading parseMulti(byte[] b, boolean history) {
        if (b.length < 23) return null;
        int prop = b[4] & 0xFF;
        Reading r = new Reading();
        r.roleId = (int) u32(b[6], b[7], b[8], b[9]);
        r.weightKg = weightKg(b[10], b[11], prop);
        r.fatPct = pct(b[12], b[13]);
        r.waterPct = pct(b[14], b[15]);
        r.musclePct = pct(b[16], b[17]);
        r.bmr = u16(b[18], b[19]);
        r.visceral = round1(u16(b[20], b[21]) / 10f);
        r.boneKg = round1((b[22] & 0xFF) / 10f);
        r.complete = !history;
        return r;
    }

    /* ------------------------------------------------------------ */
    /* Outgoing instructions                                        */
    /* ------------------------------------------------------------ */

    /** Role/profile sync, the same packet the original app writes on connect. */
    public static byte[] roleInfo(int roleId, int sex, int age, int heightCm, float weightKg) {
        byte[] b = new byte[20];
        b[0] = (byte) 0xCA;
        b[1] = TYPE_STREAM;
        b[2] = 16;
        b[3] = 16;
        b[4] = 0x11; // one packet of one
        putInt(b, (int) (System.currentTimeMillis() / 1000), 5);
        b[9] = 0;
        b[10] = 0;
        putInt(b, roleId, 11);
        b[15] = (byte) ((sex == 1 ? 0x80 : 0) | (age & 0x7F));
        b[16] = (byte) (heightCm & 0xFF);
        putShort(b, (short) Math.round(weightKg * 10f), 17);
        b[19] = xor(b, 1, 18);
        return b;
    }

    public static byte[] selectUser(int roleId) {
        byte[] b = new byte[9];
        b[0] = (byte) 0xCA;
        b[1] = TYPE_STREAM;
        b[2] = 5;
        b[3] = 21;
        putInt(b, roleId, 4);
        b[8] = xor(b, 1, 7);
        return b;
    }

    public static byte[] requestHistory() {
        byte[] b = new byte[20];
        b[0] = (byte) 0xCA;
        b[1] = TYPE_STREAM;
        b[2] = 2;
        b[3] = 17;
        b[4] = 1;
        b[5] = xor(b, 1, 4);
        return b;
    }

    /* ------------------------------------------------------------ */
    /* Helpers                                                      */
    /* ------------------------------------------------------------ */

    /**
     * Weight is a big-endian 16-bit value in the unit/resolution encoded in the
     * scale-property byte, converted to kilograms.
     */
    public static float weightKg(int b0, int b1, int prop) {
        int raw = ((b0 & 0xFF) << 8) | (b1 & 0xFF);
        // Resolution bits (2..1): 01 = whole units, 10 = 0.01, else 0.1.
        int digit = (prop >> 1) & 0x03;
        float value;
        if (digit == 1) value = raw;
        else if (digit == 2) value = raw / 100f;
        else value = raw / 10f;

        int unit = (prop >> 3) & 0x03;
        if (unit == 1) value *= 0.5f;           // jin
        else if (unit == 2) value *= 0.4535924f; // lb
        else if (unit == 3) value *= 6.35029f;   // stone
        return round2(value);
    }

    /** Percentages arrive in 0.1 % units. */
    private static float pct(int b0, int b1) {
        float v = u16(b0, b1) / 10f;
        if (v > 100f) v /= 10f; // some firmwares already send plain percent
        return round1(v);
    }

    private static int u16(int b0, int b1) {
        return ((b0 & 0xFF) << 8) | (b1 & 0xFF);
    }

    private static long u32(int b0, int b1, int b2, int b3) {
        return ((long) (b0 & 0xFF) << 24) | ((b1 & 0xFF) << 16) | ((b2 & 0xFF) << 8) | (b3 & 0xFF);
    }

    private static void putInt(byte[] b, int v, int off) {
        b[off] = (byte) (v >> 24);
        b[off + 1] = (byte) (v >> 16);
        b[off + 2] = (byte) (v >> 8);
        b[off + 3] = (byte) v;
    }

    private static void putShort(byte[] b, short v, int off) {
        b[off] = (byte) (v >> 8);
        b[off + 1] = (byte) v;
    }

    private static byte xor(byte[] b, int from, int to) {
        byte v = b[from];
        for (int i = from + 1; i <= to; i++) v ^= b[i];
        return v;
    }

    private static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    private static float round2(float v) {
        return Math.round(v * 100f) / 100f;
    }

    /** Formats a frame for the debug log. */
    public static String hex(byte[] d) {
        if (d == null) return "";
        StringBuilder sb = new StringBuilder(d.length * 3);
        for (byte b : d) sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }

    public static List<String> describe(byte[] d) {
        List<String> out = new ArrayList<>();
        if (d == null || d.length < 2) return out;
        out.add("type=0x" + Integer.toHexString(d[1] & 0xFF));
        if ((d[1] & 0xFF) == TYPE_STREAM) out.add("sub=" + (d[3] & 0xFF));
        return out;
    }
}
