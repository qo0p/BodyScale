package uz.bodyscale.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import uz.bodyscale.R;
import uz.bodyscale.algo.Metric;
import uz.bodyscale.data.HistoryStore;
import uz.bodyscale.data.Measurement;

/** Plots one selected parameter over the locally stored readings. */
public class ChartActivity extends Activity {

    private final Metric[] metrics = Metric.values();
    private Metric selected = Metric.WEIGHT;

    private LinearLayout chips;
    private LinearLayout stats;
    private ChartView chart;
    private TextView hint;
    private List<Measurement> history;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);
        Ui.applyInsets(this);

        chips = findViewById(R.id.chips);
        stats = findViewById(R.id.stats);
        chart = findViewById(R.id.chart);
        hint = findViewById(R.id.hint);
        history = HistoryStore.all(this);

        findViewById(R.id.back).setOnClickListener(v -> finish());

        buildChips();
        refresh();
    }

    private void buildChips() {
        chips.removeAllViews();
        for (Metric m : metrics) {
            TextView chip = Ui.chip(this, m.label(this), m == selected);
            chip.setOnClickListener(v -> {
                selected = m;
                buildChips();
                refresh();
            });
            chips.addView(chip);
        }
    }

    private void refresh() {
        chart.setData(selected, history);
        stats.removeAllViews();

        if (history.isEmpty()) {
            hint.setText(R.string.chart_hint_empty);
            addStat(getString(R.string.stats_readings), "0");
            return;
        }

        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE, sum = 0f;
        for (Measurement m : history) {
            float v = selected.value(m);
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        float first = selected.value(history.get(history.size() - 1));
        float last = selected.value(history.get(0));
        float change = last - first;

        addStat(getString(R.string.stats_min), selected.format(min));
        addStat(getString(R.string.stats_avg), selected.format(sum / history.size()));
        addStat(getString(R.string.stats_max), selected.format(max));
        addStat(getString(R.string.stats_change), (change > 0 ? "+" : "") + selected.format(change));

        int n = history.size();
        String unit = selected.unit(this);
        String unitPart = unit.trim().isEmpty() ? "" : " (" + unit + ")";
        hint.setText(getResources().getQuantityString(R.plurals.readings_count, n, n)
                + " · " + selected.label(this) + unitPart + " · " + getString(R.string.chart_range_hint));
    }

    private void addStat(String title, String value) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(lp);

        TextView t = Ui.text(this, title, 11, 0xFF8B95A5);
        t.setGravity(Gravity.CENTER);
        TextView v = Ui.text(this, value, 16, 0xFFE8EDF4);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        col.addView(t);
        col.addView(v);
        col.setOnClickListener(null);
        stats.addView(col);
    }
}
