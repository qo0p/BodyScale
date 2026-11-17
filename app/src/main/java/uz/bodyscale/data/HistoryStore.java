package uz.bodyscale.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Local measurement history; nothing leaves the device. */
public final class HistoryStore {

    private static final String PREFS = "history";
    private static final String KEY = "items";
    private static final int MAX = 100;

    private HistoryStore() {}

    public static void add(Context ctx, Measurement m) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr;
        try {
            arr = new JSONArray(sp.getString(KEY, "[]"));
        } catch (Exception e) {
            arr = new JSONArray();
        }
        JSONArray out = new JSONArray();
        try {
            out.put(m.toJson());
        } catch (Exception ignored) {
        }
        for (int i = 0; i < arr.length() && i < MAX - 1; i++) {
            out.put(arr.opt(i));
        }
        sp.edit().putString(KEY, out.toString()).apply();
    }

    public static List<Measurement> all(Context ctx) {
        List<Measurement> list = new ArrayList<>();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) list.add(Measurement.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static Measurement latest(Context ctx) {
        List<Measurement> all = all(ctx);
        return all.isEmpty() ? null : all.get(0);
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    /** Removes the reading with the given timestamp. */
    public static void remove(Context ctx, long time) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray out = new JSONArray();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (o.optLong("t") == time) continue;
                out.put(o);
            }
        } catch (Exception ignored) {
        }
        sp.edit().putString(KEY, out.toString()).apply();
    }
}
