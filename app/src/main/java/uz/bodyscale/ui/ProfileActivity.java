package uz.bodyscale.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import uz.bodyscale.R;
import uz.bodyscale.algo.Profile;

public class ProfileActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Ui.applyInsets(this);

        Profile p = Profile.load(this);
        EditText h = findViewById(R.id.pHeight);
        EditText age = findViewById(R.id.pAge);
        EditText target = findViewById(R.id.pTarget);
        RadioButton male = findViewById(R.id.pMale);
        RadioButton female = findViewById(R.id.pFemale);

        h.setText(String.valueOf(p.heightCm));
        age.setText(String.valueOf(p.age));
        if (p.targetWeight > 0) target.setText(Ui.f1(p.targetWeight));
        if (p.sex == 1) male.setChecked(true); else female.setChecked(true);

        findViewById(R.id.back).setOnClickListener(v -> finish());

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            int height = (int) num(h.getText().toString(), 0);
            int years = (int) num(age.getText().toString(), 0);
            if (height < 80 || height > 250 || years < 5 || years > 120) {
                Toast.makeText(this, R.string.profile_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            p.heightCm = height;
            p.age = years;
            p.sex = male.isChecked() ? 1 : 0;
            p.targetWeight = num(target.getText().toString(), 0);
            p.save(this);
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
            finish();
        });

        findViewById(R.id.btnForget).setOnClickListener(v -> {
            p.deviceMac = "";
            p.deviceName = "";
            p.save(this);
            Toast.makeText(this, R.string.profile_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private static float num(String s, float def) {
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return def;
        }
    }
}
