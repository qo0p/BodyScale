package uz.bodyscale.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import uz.bodyscale.R;
import uz.bodyscale.algo.Metric;
import uz.bodyscale.data.Measurement;

/** Minimal line chart with axes, grid, area fill and value labels. */
public class ChartView extends View {

    private final List<Float> values = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    private Metric metric = Metric.WEIGHT;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();
    private final RectF plot = new RectF();

    private static final int ACCENT = 0xFF2DD4BF;

    public ChartView(Context c) {
        super(c);
        init();
    }

    public ChartView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        line.setColor(ACCENT);
        line.setStrokeWidth(2.4f * d);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);

        area.setStyle(Paint.Style.FILL);

        dot.setColor(ACCENT);
        dot.setStyle(Paint.Style.FILL);

        label.setColor(0xFF8B95A5);
        label.setTextSize(10f * d);

        axis.setColor(0xFF232B37);
        axis.setStrokeWidth(1f * d);
    }

    public void setData(Metric metric, List<Measurement> history) {
        this.metric = metric;
        values.clear();
        times.clear();
        // history is newest first; draw oldest -> newest
        for (int i = history.size() - 1; i >= 0; i--) {
            Measurement m = history.get(i);
            values.add(metric.value(m));
            times.add(m.time);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float left = 40f * d;
        float right = getWidth() - 14f * d;
        float top = 16f * d;
        float bottom = getHeight() - 26f * d;
        plot.set(left, top, right, bottom);

        if (values.isEmpty()) {
            label.setTextSize(13f * d);
            label.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getContext().getString(R.string.chart_empty), getWidth() / 2f, getHeight() / 2f, label);
            return;
        }

        float min = values.get(0), max = values.get(0);
        for (float v : values) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (max - min < 0.0001f) {
            min -= 1f;
            max += 1f;
        } else {
            float pad = (max - min) * 0.15f;
            min -= pad;
            max += pad;
        }
        if (min < 0 && values.get(0) >= 0 && metric != Metric.BODY_AGE) min = 0;

        // horizontal grid + y labels
        label.setTextSize(10f * d);
        label.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 3; i++) {
            float y = bottom - (bottom - top) * i / 3f;
            canvas.drawLine(left, y, right, y, axis);
            float v = min + (max - min) * i / 3f;
            canvas.drawText(metric.format(v), left - 6f * d, y + 3.5f * d, label);
        }

        int n = values.size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            float x = n == 1 ? (left + right) / 2f : left + (right - left) * i / (float) (n - 1);
            float y = bottom - (values.get(i) - min) / (max - min) * (bottom - top);
            xs[i] = x;
            ys[i] = y;
        }

        path.reset();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            float midX = (xs[i - 1] + xs[i]) / 2f;
            path.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i]);
        }

        if (n > 1) {
            fillPath.reset();
            fillPath.addPath(path);
            fillPath.lineTo(xs[n - 1], bottom);
            fillPath.lineTo(xs[0], bottom);
            fillPath.close();
            area.setShader(new LinearGradient(0, top, 0, bottom,
                    Ui.alpha(ACCENT, 70), Ui.alpha(ACCENT, 4), Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, area);
        }

        canvas.drawPath(path, line);

        for (int i = 0; i < n; i++) {
            float r = (i == n - 1 ? 4f : 2.6f) * d;
            canvas.drawCircle(xs[i], ys[i], r, dot);
            if (i == n - 1) {
                canvas.drawCircle(xs[i], ys[i], r + 2f * d, hollow());
            }
        }

        // last value bubble
        label.setTextSize(12f * d);
        label.setColor(ACCENT);
        label.setTextAlign(Paint.Align.RIGHT);
        String unit = metric.unit(getContext());
        String last = metric.format(values.get(n - 1)) + (unit.trim().isEmpty() ? "" : " " + unit);
        float tx = Math.max(left + 30f * d, xs[n - 1] - 4f * d);
        float ty = Math.max(top + 12f * d, ys[n - 1] - 10f * d);
        canvas.drawText(last, tx, ty, label);
        label.setColor(0xFF8B95A5);

        // x labels: first and last date
        label.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(shortDate(times.get(0)), left, getHeight() - 8f * d, label);
        if (n > 1) {
            label.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(shortDate(times.get(n - 1)), right, getHeight() - 8f * d, label);
        }
    }

    private Paint hollow() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f * getResources().getDisplayMetrics().density);
        p.setColor(Ui.alpha(ACCENT, 90));
        return p;
    }

    private String shortDate(long millis) {
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(millis));
    }
}
