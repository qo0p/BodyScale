package uz.bodyscale.algo;

import android.content.Context;
import android.content.SharedPreferences;

/** User profile used by the scale and by the local algorithm. */
public class Profile {

    private static final String PREFS = "profile";

    public int sex = 1;            // 1 = male, 0 = female
    public int age = 30;
    public int heightCm = 175;
    public float targetWeight = 0f;
    public String deviceMac = "";
    public String deviceName = "";

    public static Profile load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Profile p = new Profile();
        p.sex = sp.getInt("sex", 1);
        p.age = sp.getInt("age", 30);
        p.heightCm = sp.getInt("height", 175);
        p.targetWeight = sp.getFloat("target", 0f);
        p.deviceMac = sp.getString("mac", "");
        p.deviceName = sp.getString("name", "");
        return p;
    }

    public void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("sex", sex)
                .putInt("age", age)
                .putInt("height", heightCm)
                .putFloat("target", targetWeight)
                .putString("mac", deviceMac)
                .putString("name", deviceName)
                .apply();
    }

    /** Height in metres. */
    public float heightM() {
        return heightCm / 100f;
    }
}
