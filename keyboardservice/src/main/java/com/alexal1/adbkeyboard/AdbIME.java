package com.alexal1.adbkeyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class AdbIME extends InputMethodService {

    private static final String TAG = "AdbIME";
    private static final String IME_MESSAGE_B64 = "ADB_INPUT_B64";
    private static final String IME_CLEAR_TEXT = "ADB_CLEAR_TEXT";
    private static final String EXTRA_MESSAGE = "msg";
    private static final String EXTRA_DELAY_MEAN = "delay_mean";
    private static final String EXTRA_DELAY_DEVIATION = "delay_deviation";
    private static final int DEFAULT_DELAY_MEAN = 200;
    private static final int DEFAULT_DELAY_DEVIATION = 100;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger animationDotsCount = new AtomicInteger(0);
    private BroadcastReceiver receiver = null;
    private View inputView = null;
    private TextView typingProgress = null;
    private TextView typingNoProgress = null;
    private Timer animationTimer = null;

    @Override
    public View onCreateInputView() {
        inputView = getLayoutInflater().inflate(R.layout.view, null);
        final InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        Button switchButton = inputView.findViewById(R.id.switchButton);
        typingProgress = inputView.findViewById(R.id.typingProgress);
        typingNoProgress = inputView.findViewById(R.id.typingNoProgress);
        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                inputMethodManager.showInputMethodPicker();
            }
        });

        if (receiver == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(IME_MESSAGE_B64);
            filter.addAction(IME_CLEAR_TEXT);
            receiver = new AdbReceiver();
            registerReceiver(receiver, filter);
        }

        return inputView;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        int keyboardHeight = Math.round(Math.min(Math.max(getScreenHeight() / 3f, dp(200)), dp(400)));
        ViewGroup.LayoutParams lp = inputView.getLayoutParams();
        lp.height = keyboardHeight;
        inputView.setLayoutParams(lp);
    }

    private float getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    private float dp(int count) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                count,
                Resources.getSystem().getDisplayMetrics()
        );
    }

    private void startTypingAnimation() {
        typingProgress.setVisibility(View.VISIBLE);
        typingNoProgress.setVisibility(View.INVISIBLE);
        if (animationTimer != null) {
            animationTimer.cancel();
        }
        animationDotsCount.set(0);
        animationTimer = new Timer();
        animationTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                animationDotsCount.set((animationDotsCount.get() + 1) % 4);
                final StringBuilder builder = new StringBuilder(getString(R.string.typing));
                for (int i = 0; i < animationDotsCount.get(); i++) {
                    builder.append('.');
                }
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        typingProgress.setText(builder.toString());
                    }
                });
            }
        }, 0, 400);
    }

    private void stopTypingAnimation() {
        if (animationTimer != null) {
            animationTimer.cancel();
        }
        animationTimer = null;
        typingProgress.setVisibility(View.INVISIBLE);
        typingNoProgress.setVisibility(View.VISIBLE);
    }

    public void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
    }

    class AdbReceiver extends BroadcastReceiver {

        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private String text;
        private int delayMean;
        private int delayDeviation;

        private void typeText(InputConnection inputConnection, String text) {
            this.text = text;
            typeChar(inputConnection, 0);
            startTypingAnimation();
        }

        private void typeChar(final InputConnection inputConnection, final int typedSymbols) {
            char c = text.charAt(typedSymbols);
            inputConnection.commitText(String.valueOf(c), 1);

            long randomDelay = delayMean - delayDeviation + Math.round(2 * delayDeviation * Math.random());
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (typedSymbols < text.length() - 1) {
                        typeChar(inputConnection, typedSymbols + 1);
                    } else {
                        stopTypingAnimation();
                    }
                }
            }, randomDelay);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(IME_MESSAGE_B64)) {
                delayMean = intent.getIntExtra(EXTRA_DELAY_MEAN, DEFAULT_DELAY_MEAN);
                delayDeviation = intent.getIntExtra(EXTRA_DELAY_DEVIATION, DEFAULT_DELAY_DEVIATION);
                String data = intent.getStringExtra(EXTRA_MESSAGE);

                byte[] b64 = Base64.decode(data, Base64.DEFAULT);
                String msg = null;
                try {
                    msg = new String(b64, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "Cannot type text", e);
                }

                if (msg != null) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        typeText(ic, msg);
                    }
                }
            }

            if (intent.getAction().equals(IME_CLEAR_TEXT)) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    CharSequence curPos = ic.getExtractedText(new ExtractedTextRequest(), 0).text;
                    CharSequence beforePos = ic.getTextBeforeCursor(curPos.length(), 0);
                    CharSequence afterPos = ic.getTextAfterCursor(curPos.length(), 0);
                    ic.deleteSurroundingText(beforePos.length(), afterPos.length());
                }
            }
        }
    }
}
