package uz.bodyscale.ui;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import uz.bodyscale.R;
import uz.bodyscale.algo.BodyMath;
import uz.bodyscale.data.Measurement;

/** Renders a measurement into rows; shared by the result and calculator screens. */
public final class ResultRenderer {

    private static final int GREEN = 0xFF34D399;
    private static final int AMBER = 0xFFF59E0B;
    private static final int RED = 0xFFEF4444;

    private ResultRenderer() {}

    public static void chips(Context c, LinearLayout box, Measurement m) {
        box.removeAllViews();
        box.addView(Ui.chip(c, c.getString(R.string.chips_fat, Ui.f1(m.fatPct)), true));
        box.addView(Ui.chip(c, c.getString(R.string.chips_muscle, Ui.f1(m.musclePct)), false));
        box.addView(Ui.chip(c, c.getString(R.string.chips_water, Ui.f1(m.waterPct)), false));
        box.addView(Ui.chip(c, c.getString(R.string.chips_score, m.score), false));
    }

    public static void rows(LinearLayout box, Measurement m) {
        box.removeAllViews();
        Context c = box.getContext();

        section(box, c.getString(R.string.section_body));
        row(box, c.getString(R.string.row_bmi), Ui.f1(m.bmi), BodyMath.bmiLabel(m.bmi));
        row(box, c.getString(R.string.row_body_fat), pct(c, m.fatPct), BodyMath.fatLabel(m.fatPct, m.sex, m.age));
        row(box, c.getString(R.string.row_fat_mass), kg(c, m.fatKg), 0);
        row(box, c.getString(R.string.row_lean_mass), kg(c, m.leanKg), 0);
        row(box, c.getString(R.string.row_muscle), pct(c, m.musclePct), BodyMath.muscleLabel(m.musclePct));
        row(box, c.getString(R.string.row_muscle_mass), kg(c, m.muscleKg), 0);
        row(box, c.getString(R.string.row_body_water), pct(c, m.waterPct), BodyMath.waterLabel(m.waterPct));
        row(box, c.getString(R.string.row_water_mass), kg(c, m.waterKg), 0);
        row(box, c.getString(R.string.row_visceral), Ui.f1(m.visceral), BodyMath.visceralLabel(m.visceral));
        row(box, c.getString(R.string.row_bone), kg(c, m.boneKg), 0);
        row(box, c.getString(R.string.row_protein), pct(c, m.proteinPct) + " / " + kg(c, m.proteinKg), 0);
        row(box, c.getString(R.string.row_subcutaneous), pct(c, m.subcutaneousPct), 0);
        row(box, c.getString(R.string.row_bcm), kg(c, m.bcmKg), 0);

        section(box, c.getString(R.string.section_metabolism));
        row(box, c.getString(R.string.row_bmr), m.bmr + " " + c.getString(R.string.unit_kcal), 0);
        row(box, c.getString(R.string.row_body_age), String.valueOf(m.bodyAge), ageBadge(m.bodyAge, m.age));
        row(box, c.getString(R.string.row_score), c.getString(R.string.row_score_value, m.score),
                m.score >= 80 ? R.string.badge_good : (m.score >= 65 ? R.string.badge_normal : R.string.badge_low));

        section(box, c.getString(R.string.section_targets));
        row(box, c.getString(R.string.row_ideal), kg(c, m.idealWeight), 0);
        row(box, c.getString(R.string.row_weight_control), signed(c, m.weightControl), controlBadge(m.weightControl, true));
        row(box, c.getString(R.string.row_fat_control), signed(c, m.fatControl), controlBadge(m.fatControl, false));
        row(box, c.getString(R.string.row_muscle_control), signed(c, m.muscleControl), controlBadge(-m.muscleControl, true));
        row(box, c.getString(R.string.row_obesity), signed(c, m.obesityPct) + " " + c.getString(R.string.unit_percent), 0);
        if (m.impedance > 0) {
            row(box, c.getString(R.string.row_impedance),
                    Ui.f1(m.impedance) + " " + c.getString(R.string.unit_ohm), 0);
        }
    }

    /* ------------------------------------------------------------ */

    private static void section(LinearLayout box, String title) {
        TextView t = Ui.text(box.getContext(), title, 11, 0xFF2DD4BF);
        t.setLetterSpacing(0.12f);
        int ph = Ui.dp(box.getContext(), 14), pt = Ui.dp(box.getContext(), 14);
        t.setPadding(ph, pt, ph, Ui.dp(box.getContext(), 4));
        box.addView(t);
    }

    private static void row(LinearLayout box, String label, String value, int badgeRes) {
        String badge = badgeRes == 0 ? null : box.getContext().getString(badgeRes);
        Ui.row(box, label, value, badge, badgeRes == 0 ? 0 : badgeColor(badgeRes));
    }

    private static String kg(Context c, float v) {
        return Ui.f1(v) + " " + c.getString(R.string.unit_kg);
    }

    private static String pct(Context c, float v) {
        return Ui.f1(v) + " " + c.getString(R.string.unit_percent);
    }

    private static String signed(Context c, float v) {
        return (v > 0 ? "+" : "") + Ui.f1(v);
    }

    private static int ageBadge(int bodyAge, int realAge) {
        int d = bodyAge - realAge;
        if (d <= -3) return R.string.badge_younger;
        if (d >= 3) return R.string.badge_older;
        return R.string.badge_normal;
    }

    private static int controlBadge(float v, boolean up) {
        if (Math.abs(v) < 0.5f) return R.string.badge_normal;
        if (up) return v > 0 ? R.string.badge_low : R.string.badge_high;
        return v > 0 ? R.string.badge_high : R.string.badge_low;
    }

    public static int badgeColor(int resId) {
        if (resId == R.string.badge_normal || resId == R.string.badge_good) return GREEN;
        if (resId == R.string.badge_low || resId == R.string.badge_high
                || resId == R.string.badge_overweight || resId == R.string.badge_underweight
                || resId == R.string.badge_younger || resId == R.string.badge_older) return AMBER;
        if (resId == R.string.badge_very_high || resId == R.string.badge_obese) return RED;
        return 0xFF8B95A5;
    }
}
