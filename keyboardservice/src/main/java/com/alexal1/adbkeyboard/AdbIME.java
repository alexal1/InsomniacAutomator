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

import java.io.UnsupportedEncodingException;

public class AdbIME extends InputMethodService {

    private static final String TAG = "AdbIME";
    private static final String IME_MESSAGE_B64 = "ADB_INPUT_B64";
    private static final String IME_CLEAR_TEXT = "ADB_CLEAR_TEXT";
    private static final String EXTRA_MESSAGE = "msg";
    private static final String EXTRA_DELAY = "delay";
    private static final int DEFAULT_DELAY = 200;

    private BroadcastReceiver mReceiver = null;
    private View mInputView = null;

    @Override
    public View onCreateInputView() {
        mInputView = getLayoutInflater().inflate(R.layout.view, null);
        final InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        Button switchButton = mInputView.findViewById(R.id.switchButton);
        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                inputMethodManager.showInputMethodPicker();
            }
        });

        if (mReceiver == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(IME_MESSAGE_B64);
            filter.addAction(IME_CLEAR_TEXT);
            mReceiver = new AdbReceiver();
            registerReceiver(mReceiver, filter);
        }

        return mInputView;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        int keyboardHeight = Math.round(Math.min(getScreenHeight() / 3f, dp(200)));
        ViewGroup.LayoutParams lp = mInputView.getLayoutParams();
        lp.height = keyboardHeight;
        mInputView.setLayoutParams(lp);
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

    public void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
    }

    class AdbReceiver extends BroadcastReceiver {

        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private String text;
        private int delay;

        private void typeText(InputConnection inputConnection, String text) {
            this.text = text;
            typeChar(inputConnection, 0);
        }

        private void typeChar(final InputConnection inputConnection, final int typedSymbols) {
            char c = text.charAt(typedSymbols);
            inputConnection.commitText(String.valueOf(c), 1);
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (typedSymbols < text.length() - 1) {
                        typeChar(inputConnection, typedSymbols + 1);
                    }
                }
            }, delay);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(IME_MESSAGE_B64)) {
                delay = intent.getIntExtra(EXTRA_DELAY, DEFAULT_DELAY);
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
