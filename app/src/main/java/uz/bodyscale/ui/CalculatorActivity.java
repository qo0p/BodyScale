package uz.bodyscale.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import uz.bodyscale.R;
import uz.bodyscale.algo.BodyMath;
import uz.bodyscale.algo.Profile;
import uz.bodyscale.data.HistoryStore;
import uz.bodyscale.data.Measurement;

/** Offline calculator: the same equations, no scale required. */
public class CalculatorActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);
        Ui.applyInsets(this);

        Profile p = Profile.load(this);
        EditText w = findViewById(R.id.inWeight);
        EditText h = findViewById(R.id.inHeight);
        EditText age = findViewById(R.id.inAge);
        EditText imp = findViewById(R.id.inImpedance);
        RadioButton male = findViewById(R.id.sexMale);
        RadioButton female = findViewById(R.id.sexFemale);
        LinearLayout metrics = findViewById(R.id.metrics);

        h.setText(String.valueOf(p.heightCm));
        age.setText(String.valueOf(p.age));
        if (p.sex == 1) male.setChecked(true); else female.setChecked(true);

        findViewById(R.id.back).setOnClickListener(v -> finish());

        findViewById(R.id.btnCompute).setOnClickListener(v -> {
            float weight = parse(w.getText().toString(), 0f);
            int height = (int) parse(h.getText().toString(), 0f);
            int years = (int) parse(age.getText().toString(), 0f);
            float impedance = parse(imp.getText().toString(), 0f);

            if (weight < 10f || height < 80 || years < 5) {
                Toast.makeText(this, R.string.calc_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            Profile cp = new Profile();
            cp.sex = male.isChecked() ? 1 : 0;
            cp.age = years;
            cp.heightCm = height;

            Measurement m = new Measurement();
            m.time = System.currentTimeMillis();
            m.fromScale = false;
            m.weightKg = weight;
            m.impedance = impedance;
            BodyMath.build(m, cp);

            metrics.setVisibility(android.view.View.VISIBLE);
            ResultRenderer.rows(metrics, m);
            HistoryStore.add(this, m);
            Toast.makeText(this, R.string.calc_saved, Toast.LENGTH_SHORT).show();
        });
    }

    private static float parse(String s, float def) {
        if (TextUtils.isEmpty(s)) return def;
        try {
            return Float.parseFloat(s.replace(',', '.'));
        } catch (Exception e) {
            return def;
        }
    }
}
