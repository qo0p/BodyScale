package uz.bodyscale.algo;

import uz.bodyscale.R;

import uz.bodyscale.data.Measurement;

/**
 * Body-composition math.
 *
 * <p>The single-impedance equations are the linear BIA fits used by Chipsea-based
 * scales (decompiled from CsAlgoBuilder of com.chipsea.btcontrol.en 3.x):
 * each parameter is a linear combination of height (cm), weight (kg), age (years)
 * and bio-impedance Z (ohm), with separate male/female coefficients, followed by
 * the same clamping windows the original app applies.
 *
 * <p>When the scale itself already sends body fat / water / muscle / BMR /
 * visceral / bone, those measured values win and only the derived masses are
 * computed here.
 */
public final class BodyMath {

    private BodyMath() {}

    /** Fills every derived field from the raw inputs. */
    public static Measurement build(Measurement m, Profile p) {
        m.heightCm = p.heightCm;
        m.age = p.age;
        m.sex = p.sex;
        float h = p.heightCm;
        float w = m.weightKg;
        if (w <= 0) return m;

        m.bmi = round1(w / (p.heightM() * p.heightM()));

        boolean haveScaleValues = m.fatPct > 0;

        if (m.fatPct <= 0) {
            m.fatPct = bodyFat(h, w, p.age, p.sex, m.impedance);
        }
        if (m.waterPct <= 0) {
            m.waterPct = totalWater(h, w, p.age, p.sex, m.impedance);
        }
        if (m.musclePct <= 0) {
            // Soft lean mass from the linear fit, scaled to skeletal muscle.
            float slm = softLeanMass(h, w, p.age, p.sex, m.impedance);
            m.musclePct = round1(clamp(slm * 0.55f / w * 100f, 10f, 65f));
        }
        if (m.visceral <= 0) {
            m.visceral = visceralFat(h, w, p.age, p.sex, m.impedance);
        }
        if (m.bmr <= 0) {
            m.bmr = Math.round(basalMetabolism(h, w, p.age, p.sex, m.impedance));
        }
        if (m.boneKg <= 0) {
            float bone = w - (w * m.fatPct / 100f) - (w * m.musclePct / 100f);
            m.boneKg = round1(clamp(bone, 1f, 4f));
        }

        m.fatKg = round1(w * m.fatPct / 100f);
        m.leanKg = round1(w - m.fatKg);
        m.muscleKg = round1(w * m.musclePct / 100f);
        m.waterKg = round1(w * m.waterPct / 100f);
        m.bonePct = round1(m.boneKg / w * 100f);

        // Protein is ~19% of fat-free mass on the Chipsea reference model.
        m.proteinKg = round1(m.leanKg * 0.19f);
        m.proteinPct = round1(m.proteinKg / w * 100f);

        // Subcutaneous fat is the fraction of fat stored under the skin
        // (visceral fat is the remainder).
        m.subcutaneousPct = round1(Math.max(0f, m.fatPct - m.visceral * 0.35f));

        m.bcmKg = round1(m.leanKg * 0.55f);

        m.idealWeight = round1(p.sex == 1 ? (h - 80f) * 0.7f : (h - 70f) * 0.6f);
        m.weightControl = round1(m.idealWeight - w);
        m.obesityPct = round1((w - m.idealWeight) / m.idealWeight * 100f);
        m.fatControl = round1(m.fatKg - (w * normalFatHigh(p.sex, p.age) / 100f));
        m.muscleControl = round1((w * 0.45f) - m.muscleKg);

        m.bodyAge = bodyAge(h, w, p.age, p.sex, m.impedance, haveScaleValues ? m : null);
        m.score = score(h, w, p.sex, p.age, m.fatPct, m.muscleKg, 0f);
        return m;
    }

    /* ---------------------------------------------------------------- */
    /* Single-impedance BIA equations (Chipsea linear fits)             */
    /* ---------------------------------------------------------------- */

    public static float bodyFat(float h, float w, int age, int sex, float z) {
        float base;
        if (sex == 1) {
            base = -0.3315f * h + 0.6216f * w + 0.0183f * age + 0.0085f * z + 22.554f;
        } else {
            base = -0.3332f * h + 0.7509f * w + 0.0196f * age + 0.0072f * z + 22.7193f;
        }
        return round1(clamp(base / w * 100f, 5f, 45f));
    }

    public static float softLeanMass(float h, float w, int age, int sex, float z) {
        float slm;
        if (sex == 1) {
            slm = 0.2867f * h + 0.3894f * w - 0.0408f * age - 0.01235f * z - 15.7665f;
        } else {
            slm = 0.3186f * h + 0.1934f * w - 0.0206f * age - 0.0132f * z - 16.4556f;
        }
        return round1(clamp(slm, 20f, 70f));
    }

    public static float totalWater(float h, float w, int age, int sex, float z) {
        if (age <= 17) return 0f;
        float v;
        if (sex == 1) {
            v = (0.0939f * h + 0.3758f * w - 0.0032f * age - 0.006925f * z + 0.097f);
        } else {
            v = (0.0877f * h + 0.2973f * w + 0.0128f * age - 0.00603f * z + 0.5175f);
        }
        return round1(clamp(v / w * 100f, 20f, 85f));
    }

    public static float visceralFat(float h, float w, int age, int sex, float z) {
        if (age <= 17) return 0f;
        float v;
        if (sex == 1) {
            v = -0.2675f * h + 0.42f * w + 0.1462f * age + 0.0123f * z + 13.9871f;
        } else {
            v = -0.1651f * h + 0.2628f * w + 0.0649f * age + 0.0024f * z + 12.3445f;
        }
        // The original rounds to the nearest half level.
        v = Math.round(v * 2f) / 2f;
        return clamp(v, 1f, 59f);
    }

    public static float basalMetabolism(float h, float w, int age, int sex, float z) {
        if (sex == 1) {
            return 7.5037f * h + 13.1523f * w - 4.3376f * age - 0.3486f * z - 311.7751f;
        }
        return 7.5432f * h + 9.9474f * w - 3.4382f * age - 0.309f * z - 288.2821f;
    }

    /** Body age; with scale data the fat percentage is the dominant factor. */
    public static int bodyAge(float h, float w, int age, int sex, float z, Measurement measured) {
        if (age <= 17) return 0;
        float v;
        if (measured != null && measured.fatPct > 0) {
            // Solve the fat-age relation: older bodies carry more fat for the
            // same height/weight, so body age tracks the fat deviation.
            float normal = normalFatHigh(sex, age) - 5f;
            v = age + (measured.fatPct - normal) * 0.9f;
            // Weight deviation also ages the body.
            float ideal = sex == 1 ? (h - 80f) * 0.7f : (h - 70f) * 0.6f;
            v += (w - ideal) * 0.35f;
        } else if (sex == 1) {
            v = -0.7471f * h + 0.9161f * w + 0.4184f * age + 0.0517f * z + 54.2267f;
        } else {
            v = -1.1165f * h + 1.5784f * w + 0.4615f * age + 0.0415f * z + 83.2548f;
        }
        int r = Math.round(v);
        r = Math.min(r, age + 10);
        r = Math.max(r, age - 10);
        return (int) clamp(r, 18, 80);
    }

    /* ---------------------------------------------------------------- */
    /* Score / classification                                           */
    /* ---------------------------------------------------------------- */

    public static int score(float h, float w, int sex, int age, float bfp, float muscleKg, float visceral) {
        float bmi = w / ((h / 100f) * (h / 100f));
        float bmiScore = bmi * bmi * -5.686f + bmi * 241.7f - 2470f;
        bmiScore = clamp(bmiScore, 55f, 96f);

        float adjAge = age;
        float idealBmr;
        if (sex == 1) {
            idealBmr = adjAge * adjAge * adjAge * adjAge * 1.085e-4f
                    - adjAge * adjAge * adjAge * 0.003181f
                    - adjAge * adjAge * 0.2952f + adjAge * 10.85f + 0.4248f;
            idealBmr = Math.max(idealBmr, 55f);
        } else {
            idealBmr = adjAge * adjAge * adjAge * adjAge * 2.469e-4f
                    - adjAge * adjAge * adjAge * 0.02788f
                    + adjAge * adjAge * 0.9597f - adjAge * 10.02f + 80.42f;
            idealBmr = Math.max(idealBmr, 55f);
        }
        float muscleTarget = (w * 0.45f + 90f) - muscleKg;
        muscleTarget = Math.min(muscleTarget, 100f);

        float visScore;
        if (visceral >= 15f) {
            visScore = -50f;
        } else {
            float x2 = visceral * visceral, x3 = x2 * visceral, x4 = x3 * visceral;
            visScore = x4 * visceral * 0.007212f - x4 * 0.2825f + x3 * 3.912f
                    - x2 * 22.27f + 30.38f * visceral + 89.35f;
        }
        visScore = Math.max(visScore, -50f);
        float wv = visceral == 0f ? 0.48f : 0.08f;

        int s = (int) (0.48f * bmiScore + 0.4f * idealBmr + 0.1f * muscleTarget + wv * visScore);
        return (int) clamp(s, 45, 100);
    }

    public static int bmiLabel(float bmi) {
        if (bmi < 18.5f) return R.string.badge_underweight;
        if (bmi < 24f) return R.string.badge_normal;
        if (bmi < 28f) return R.string.badge_overweight;
        return R.string.badge_obese;
    }

    public static int fatLabel(float pct, int sex, int age) {
        float low = normalFatLow(sex, age);
        float high = normalFatHigh(sex, age);
        if (pct < low) return R.string.badge_low;
        if (pct <= high) return R.string.badge_normal;
        if (pct <= high + 5f) return R.string.badge_high;
        return R.string.badge_very_high;
    }

    public static int waterLabel(float pct) {
        if (pct < 50f) return R.string.badge_low;
        if (pct <= 65f) return R.string.badge_normal;
        return R.string.badge_high;
    }

    public static int visceralLabel(float v) {
        if (v < 10f) return R.string.badge_normal;
        if (v < 15f) return R.string.badge_high;
        return R.string.badge_very_high;
    }

    public static int muscleLabel(float pct) {
        if (pct < 35f) return R.string.badge_low;
        if (pct < 45f) return R.string.badge_normal;
        return R.string.badge_high;
    }

    /** Upper bound of the normal body-fat band for sex/age. */
    public static float normalFatHigh(int sex, int age) {
        if (sex == 1) {
            if (age < 40) return 20f;
            if (age < 60) return 23f;
            return 25f;
        }
        if (age < 40) return 30f;
        if (age < 60) return 33f;
        return 35f;
    }

    public static float normalFatLow(int sex, int age) {
        return sex == 1 ? 11f : 21f;
    }

    /* ---------------------------------------------------------------- */

    public static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }
}
