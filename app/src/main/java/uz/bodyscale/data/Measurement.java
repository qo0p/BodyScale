package uz.bodyscale.data;

import org.json.JSONException;
import org.json.JSONObject;

/** One body-composition reading, either from the scale or from the calculator. */
public class Measurement {

    public long time = System.currentTimeMillis();
    public boolean fromScale;

    public float weightKg;
    public float fatPct;
    public float waterPct;
    public float musclePct;
    public float visceral;
    public float boneKg;
    public int bmr;
    public float impedance;

    // derived
    public float bmi;
    public float fatKg;
    public float leanKg;
    public float muscleKg;
    public float waterKg;
    public float proteinPct;
    public float proteinKg;
    public float bonePct;
    public float subcutaneousPct;
    public int bodyAge;
    public float idealWeight;
    public float weightControl;
    public float fatControl;
    public float muscleControl;
    public float obesityPct;
    public int score;
    public float bcmKg;          // body cell mass
    public int heightCm;
    public int age;
    public int sex;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("t", time);
        o.put("scale", fromScale);
        o.put("w", weightKg); o.put("fat", fatPct); o.put("water", waterPct);
        o.put("muscle", musclePct); o.put("vis", visceral); o.put("bone", boneKg);
        o.put("bmr", bmr); o.put("imp", impedance);
        o.put("bmi", bmi); o.put("fatKg", fatKg); o.put("leanKg", leanKg);
        o.put("muscleKg", muscleKg); o.put("waterKg", waterKg);
        o.put("proteinPct", proteinPct); o.put("proteinKg", proteinKg);
        o.put("bonePct", bonePct); o.put("subQ", subcutaneousPct);
        o.put("bodyAge", bodyAge); o.put("ideal", idealWeight);
        o.put("wCtrl", weightControl); o.put("fCtrl", fatControl);
        o.put("mCtrl", muscleControl); o.put("obesity", obesityPct);
        o.put("score", score); o.put("bcm", bcmKg);
        o.put("h", heightCm); o.put("age", age); o.put("sex", sex);
        return o;
    }

    public static Measurement fromJson(JSONObject o) {
        Measurement m = new Measurement();
        m.time = o.optLong("t");
        m.fromScale = o.optBoolean("scale");
        m.weightKg = (float) o.optDouble("w");
        m.fatPct = (float) o.optDouble("fat");
        m.waterPct = (float) o.optDouble("water");
        m.musclePct = (float) o.optDouble("muscle");
        m.visceral = (float) o.optDouble("vis");
        m.boneKg = (float) o.optDouble("bone");
        m.bmr = o.optInt("bmr");
        m.impedance = (float) o.optDouble("imp");
        m.bmi = (float) o.optDouble("bmi");
        m.fatKg = (float) o.optDouble("fatKg");
        m.leanKg = (float) o.optDouble("leanKg");
        m.muscleKg = (float) o.optDouble("muscleKg");
        m.waterKg = (float) o.optDouble("waterKg");
        m.proteinPct = (float) o.optDouble("proteinPct");
        m.proteinKg = (float) o.optDouble("proteinKg");
        m.bonePct = (float) o.optDouble("bonePct");
        m.subcutaneousPct = (float) o.optDouble("subQ");
        m.bodyAge = o.optInt("bodyAge");
        m.idealWeight = (float) o.optDouble("ideal");
        m.weightControl = (float) o.optDouble("wCtrl");
        m.fatControl = (float) o.optDouble("fCtrl");
        m.muscleControl = (float) o.optDouble("mCtrl");
        m.obesityPct = (float) o.optDouble("obesity");
        m.score = o.optInt("score");
        m.bcmKg = (float) o.optDouble("bcm");
        m.heightCm = o.optInt("h");
        m.age = o.optInt("age");
        m.sex = o.optInt("sex");
        return m;
    }
}
