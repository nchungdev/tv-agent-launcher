package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.util.Base64;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class AgentRenderInstrumentation extends Instrumentation {
    public volatile Activity activeHome;

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (activity.getClass().getName().equals(
                "com.gathin.tvhome.feature.main.ui.MainActivity")) {
            activeHome = activity;
        }
    }

    private boolean mediaScenario;
    private boolean riskScenario;
    private boolean dockScenario;
    private boolean weatherScenario;
    private boolean hubScenario;
    private static final String FIXTURE = "## Kiểm tra hiển thị\n\n"
            + "| Ổ đĩa | Tổng | Còn trống |\n|---|---|---|\n"
            + "| NVMe | 233 GB | **72 GB** |\n| MainPool | 7,2 TB | **1,3 TB** |\n\n"
            + "<div class=grid><article class=card><h3>NAS</h3>Intel N150 · RAM 16 GB"
            + "</article><article class=card><h3>Xem video</h3>Ưu tiên dịch vụ media"
            + "</article></div>\n\n[Qwen · thông tin model](https://ollama.com/library/qwen3)\n\n";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mediaScenario = arguments != null && "media".equals(arguments.getString("scenario"));
        riskScenario = arguments != null && "risk".equals(arguments.getString("scenario"));
        dockScenario = arguments != null && "dock".equals(arguments.getString("scenario"));
        weatherScenario = arguments != null && "weather".equals(arguments.getString("scenario"));
        hubScenario = arguments != null && "hub".equals(arguments.getString("scenario"));
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (mediaScenario) {
                result.putString("result", new MediaRenderChecks(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (riskScenario) {
                result.putString("result", new RiskRegressionChecks(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (weatherScenario || hubScenario) {
                result.putString("result", new WeatherRenderChecks(this).run(hubScenario));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (dockScenario) {
                result.putString("result", new DockRenderChecks(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            Activity activity = startActivitySync(new Intent()
                    .setClassName(getTargetContext(), "com.gathin.tvhome.AgentActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(1800);
            String fixture = FIXTURE + image();
            runOnMainSync(() -> showFixture(activity, fixture));
            Thread.sleep(1800);
            CountDownLatch checked = new CountDownLatch(1);
            String[] status = new String[1];
            runOnMainSync(() -> answer(activity).evaluateJavascript(
                    "document.querySelectorAll('table').length===1"
                    + "&&document.querySelectorAll('.card').length===2"
                    + "&&document.querySelectorAll('.source').length===1"
                    + "&&document.querySelector('img').naturalWidth===240", value -> {
                        status[0] = value;
                        checked.countDown();
                    }));
            if (!checked.await(5, TimeUnit.SECONDS) || !"true".equals(status[0])) {
                throw new IllegalStateException("Rich answer DOM failed: " + status[0]);
            }
            saveScreen("agent-rich-qa.png");
            runOnMainSync(() -> answer(activity).evaluateJavascript(
                    "window.scrollTo(0,document.body.scrollHeight)", null));
            Thread.sleep(500);
            saveScreen("agent-rich-qa-bottom.png");
            result.putString("result", "table, cards, link preview and image passed");
            finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            result.putString("failure", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void saveScreen(String name) throws Exception {
        File image = new File(getTargetContext().getExternalFilesDir(null), name);
        try (FileOutputStream output = new FileOutputStream(image)) {
            getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }

    private void showFixture(Activity activity, String fixture) {
        try {
            Field recognizerField = activity.getClass().getDeclaredField("recognizer");
            recognizerField.setAccessible(true);
            SpeechRecognizer recognizer = (SpeechRecognizer) recognizerField.get(activity);
            Method state = activity.getClass().getDeclaredMethod("setState", int.class);
            state.setAccessible(true);
            state.invoke(activity, 0);
            if (recognizer != null) recognizer.cancel();

            WebView view = answer(activity);
            Method setText = view.getClass().getDeclaredMethod("setText", String.class);
            setText.setAccessible(true);
            setText.invoke(view, fixture);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private WebView answer(Activity activity) {
        try {
            Field field = activity.getClass().getDeclaredField("answer");
            field.setAccessible(true);
            return (WebView) field.get(activity);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private String image() {
        Bitmap bitmap = Bitmap.createBitmap(240, 72, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(52, 48, 71));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(22);
        canvas.drawText("Image preview", 24, 44, paint);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        bitmap.recycle();
        return "![Ảnh kiểm thử](data:image/png;base64,"
                + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP) + ")";
    }
}
