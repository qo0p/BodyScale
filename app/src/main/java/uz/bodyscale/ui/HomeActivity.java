package uz.bodyscale.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import uz.bodyscale.R;
import uz.bodyscale.algo.Profile;
import uz.bodyscale.data.HistoryStore;
import uz.bodyscale.data.Measurement;

public class HomeActivity extends Activity {

    private LinearLayout historyList;
    private TextView historyEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Ui.applyInsets(this);

        historyList = findViewById(R.id.historyList);
        historyEmpty = findViewById(R.id.historyEmpty);

        findViewById(R.id.btnScan).setOnClickListener(v ->
                startActivity(new Intent(this, ScanActivity.class)));
        findViewById(R.id.btnCalc).setOnClickListener(v ->
                startActivity(new Intent(this, CalculatorActivity.class)));
        findViewById(R.id.btnChart).setOnClickListener(v ->
                startActivity(new Intent(this, ChartActivity.class)));
        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderLatest();
        renderHistory();
    }

    private void renderLatest() {
        Measurement m = HistoryStore.latest(this);
        TextView weight = findViewById(R.id.homeWeight);
        TextView date = findViewById(R.id.homeDate);
        LinearLayout chips = findViewById(R.id.homeChips);
        TextView empty = findViewById(R.id.homeEmpty);

        if (m == null) {
            weight.setText("—");
            date.setText("");
            chips.removeAllViews();
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);
        weight.setText(Ui.f1(m.weightKg));
        date.setText(Ui.date(m.time));
        ResultRenderer.chips(this, chips, m);
    }

    private void renderHistory() {
        historyList.removeAllViews();
        List<Measurement> all = HistoryStore.all(this);
        historyEmpty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inf = LayoutInflater.from(this);
        int shown = 0;
        for (Measurement m : all) {
            if (shown >= 12) break;
            shown++;
            View row = inf.inflate(R.layout.item_history, historyList, false);
            TextView w = row.findViewById(R.id.rowWeight);
            TextView meta = row.findViewById(R.id.rowMeta);
            TextView fat = row.findViewById(R.id.rowFat);

            w.setText(Ui.f1(m.weightKg) + " " + getString(R.string.unit_kg));
            meta.setText(Ui.date(m.time) + getString(m.fromScale ? R.string.history_scale : R.string.history_calc));
            fat.setText(getString(R.string.history_row_fat, Ui.f1(m.fatPct)));
            row.setOnClickListener(v -> open(m));
            row.setOnLongClickListener(v -> {
                confirmDelete(m);
                return true;
            });
            historyList.addView(row);
        }
    }

    private void confirmDelete(Measurement m) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.result_delete_title)
                .setMessage(getString(R.string.result_delete_message, Ui.date(m.time), Ui.f1(m.weightKg)))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_delete, (d, w) -> {
                    HistoryStore.remove(this, m.time);
                    renderLatest();
                    renderHistory();
                })
                .show();
    }

    private void open(Measurement m) {
        Intent i = new Intent(this, ResultActivity.class);
        try {
            i.putExtra(ResultActivity.EXTRA, m.toJson().toString());
        } catch (Exception ignored) {
        }
        startActivity(i);
    }
}
