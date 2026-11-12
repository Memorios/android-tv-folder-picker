package com.tvpicker.shim;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;

/** Shows whether all-files access is granted and how to grant it. */
public class MainActivity extends Activity {

    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tv = new TextView(this);
        tv.setTextSize(22);
        tv.setPadding(64, 48, 64, 48);
        setContentView(tv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ok = Environment.isExternalStorageManager();
        tv.setText("TV Folder Picker\n\n"
                + "All files access: " + (ok ? "GRANTED" : "NOT GRANTED") + "\n\n"
                + (ok ? "" : "To grant it, run this on your PC:\n"
                + "adb shell appops set " + getPackageName()
                + " MANAGE_EXTERNAL_STORAGE allow\n\n")
                + "This app answers folder-picker requests (OPEN_DOCUMENT_TREE) "
                + "from other apps. It has no other screen.");
    }
}
