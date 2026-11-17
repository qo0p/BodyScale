package uz.bodyscale.algo;

import android.content.Context;

import java.util.Locale;

import uz.bodyscale.R;
import uz.bodyscale.data.Measurement;

/** Parameters that can be plotted. */
public enum Metric {
    WEIGHT(R.string.metric_weight, R.string.unit_kg),
    FAT(R.string.metric_fat, R.string.unit_percent),
    FAT_KG(R.string.metric_fat_kg, R.string.unit_kg),
    MUSCLE(R.string.metric_muscle, R.string.unit_percent),
    MUSCLE_KG(R.string.metric_muscle_kg, R.string.unit_kg),
    LEAN(R.string.metric_lean, R.string.unit_kg),
    WATER(R.string.metric_water, R.string.unit_percent),
    VISCERAL(R.string.metric_visceral, R.string.unit_none),
    BMI(R.string.metric_bmi, R.string.unit_none),
    BMR(R.string.metric_bmr, R.string.unit_kcal),
    BODY_AGE(R.string.metric_body_age, R.string.unit_years),
    SCORE(R.string.metric_score, R.string.unit_none),
    PROTEIN(R.string.metric_protein, R.string.unit_percent);

    public final int labelRes;
    public final int unitRes;

    Metric(int labelRes, int unitRes) {
        this.labelRes = labelRes;
        this.unitRes = unitRes;
    }

    public String label(Context c) {
        return c.getString(labelRes);
    }

    public String unit(Context c) {
        return c.getString(unitRes);
    }

    public float value(Measurement m) {
        switch (this) {
            case WEIGHT: return m.weightKg;
            case FAT: return m.fatPct;
            case FAT_KG: return m.fatKg;
            case MUSCLE: return m.musclePct;
            case MUSCLE_KG: return m.muscleKg;
            case LEAN: return m.leanKg;
            case WATER: return m.waterPct;
            case VISCERAL: return m.visceral;
            case BMI: return m.bmi;
            case BMR: return m.bmr;
            case BODY_AGE: return m.bodyAge;
            case SCORE: return m.score;
            case PROTEIN: return m.proteinPct;
            default: return 0f;
        }
    }

    public String format(float v) {
        if (this == BMR || this == BODY_AGE || this == SCORE) {
            return String.valueOf(Math.round(v));
        }
        return String.format(Locale.getDefault(), "%.1f", v);
    }
}
