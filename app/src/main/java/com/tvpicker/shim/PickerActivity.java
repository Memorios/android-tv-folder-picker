package com.tvpicker.shim;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Minimal folder browser that answers ACTION_OPEN_DOCUMENT_TREE.
 * Returns a tree URI served by FsProvider with persistable read/write grants.
 */
public class PickerActivity extends Activity {

    private static final int KIND_SELECT = 0;
    private static final int KIND_UP = 1;
    private static final int KIND_DIR = 2;
    private static final int KIND_VOLUME = 3;
    private static final int KIND_INFO = 4;

    private static class Entry {
        final String label;
        final File file;
        final int kind;

        Entry(String label, File file, int kind) {
            this.label = label;
            this.file = file;
            this.kind = kind;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private TextView header;
    private ListView list;
    private ArrayAdapter<Entry> adapter;
    private File current; // null means the storage list

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 32);

        header = new TextView(this);
        header.setTextSize(20);
        header.setPadding(0, 0, 0, 24);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        list = new ListView(this);
        list.setSelector(new ColorDrawable(0x664285F4));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        adapter = new ArrayAdapter<Entry>(this, android.R.layout.simple_list_item_1,
                new ArrayList<Entry>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextSize(22);
                tv.setPadding(24, 20, 24, 20);
                return tv;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onEntryClicked(adapter.getItem(position));
            }
        });

        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(this,
                    "All files access not granted. Run: adb shell appops set "
                            + getPackageName() + " MANAGE_EXTERNAL_STORAGE allow",
                    Toast.LENGTH_LONG).show();
        }

        showDir(null);
    }

    private void onEntryClicked(Entry e) {
        if (e == null) return;
        switch (e.kind) {
            case KIND_SELECT:
                finishWith(e.file);
                break;
            case KIND_UP:
                goUp();
                break;
            case KIND_DIR:
            case KIND_VOLUME:
                showDir(e.file);
                break;
            default:
                break;
        }
    }

    private void showDir(File dir) {
        current = dir;
        List<Entry> items = new ArrayList<>();

        if (dir == null) {
            header.setText("Choose a storage location");
            for (StorageVolume v : FsProvider.mountedVolumes(this)) {
                items.add(new Entry(v.getDescription(this) + "  (" + v.getDirectory().getPath() + ")",
                        v.getDirectory(), KIND_VOLUME));
            }
        } else {
            header.setText(dir.getAbsolutePath());
            items.add(new Entry("[ SELECT THIS FOLDER ]", dir, KIND_SELECT));
            items.add(new Entry("[ .. ]  Up", dir, KIND_UP));

            File[] all = dir.listFiles();
            if (all == null) {
                items.add(new Entry("(this folder can't be read)", dir, KIND_INFO));
            } else {
                List<File> dirs = new ArrayList<>();
                for (File f : all) {
                    if (f.isDirectory()) dirs.add(f);
                }
                sortByName(dirs);
                for (File d : dirs) {
                    items.add(new Entry(d.getName() + "/", d, KIND_DIR));
                }
                if (dirs.isEmpty()) {
                    items.add(new Entry("(no subfolders)", dir, KIND_INFO));
                }
            }
        }

        adapter.clear();
        adapter.addAll(items);
        adapter.notifyDataSetChanged();
        list.setSelection(0);
        list.requestFocus();
    }

    private static void sortByName(List<File> files) {
        File[] arr = files.toArray(new File[0]);
        Arrays.sort(arr, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().toLowerCase(Locale.ROOT).compareTo(b.getName().toLowerCase(Locale.ROOT));
            }
        });
        files.clear();
        files.addAll(Arrays.asList(arr));
    }

    private boolean isVolumeRoot(File f) {
        for (StorageVolume v : FsProvider.mountedVolumes(this)) {
            if (v.getDirectory().getAbsolutePath().equals(f.getAbsolutePath())) return true;
        }
        return false;
    }

    private void goUp() {
        if (current == null) return;
        File parent = current.getParentFile();
        if (parent == null || isVolumeRoot(current)) {
            showDir(null);
        } else {
            showDir(parent);
        }
    }

    private void finishWith(File dir) {
        Uri uri = DocumentsContract.buildTreeDocumentUri(FsProvider.AUTHORITY, dir.getAbsolutePath());
        Intent result = new Intent();
        result.setData(uri);
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (current == null) {
            setResult(RESULT_CANCELED);
            finish();
        } else {
            goUp();
        }
    }
}
