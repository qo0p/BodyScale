package uz.bodyscale.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import uz.bodyscale.R;
import uz.bodyscale.data.HistoryStore;
import uz.bodyscale.data.Measurement;

public class ResultActivity extends Activity {

    public static final String EXTRA = "measurement";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Ui.applyInsets(this);

        findViewById(R.id.back).setOnClickListener(v -> finish());

        Measurement m = null;
        String raw = getIntent().getStringExtra(EXTRA);
        if (raw != null) {
            try {
                m = Measurement.fromJson(new JSONObject(raw));
            } catch (Exception ignored) {
            }
        }
        if (m == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.weight)).setText(Ui.f1(m.weightKg));
        ((TextView) findViewById(R.id.subtitle)).setText(getString(R.string.result_subtitle,
                getString(m.fromScale ? R.string.result_measured : R.string.result_calculated),
                Ui.date(m.time),
                getString(m.sex == 1 ? R.string.male : R.string.female),
                getResources().getQuantityString(R.plurals.age_years, m.age, m.age),
                m.heightCm + " " + getString(R.string.unit_cm)));

        LinearLayout chips = findViewById(R.id.chips);
        ResultRenderer.chips(this, chips, m);

        LinearLayout metrics = findViewById(R.id.metrics);
        ResultRenderer.rows(metrics, m);

        final Measurement current = m;
        findViewById(R.id.btnDelete).setOnClickListener(v ->
                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.result_delete_title)
                        .setMessage(getString(R.string.result_delete_message, Ui.date(current.time), Ui.f1(current.weightKg)))
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setPositiveButton(R.string.dialog_delete, (d, w) -> {
                            HistoryStore.remove(this, current.time);
                            finish();
                        })
                        .show());
    }
}
