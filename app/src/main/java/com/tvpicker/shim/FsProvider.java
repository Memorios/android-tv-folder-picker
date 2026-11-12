package com.tvpicker.shim;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exposes the device's storage volumes as a document tree. Document IDs are
 * absolute file paths. Every path is checked to be inside a mounted volume.
 */
public class FsProvider extends DocumentsProvider {

    public static final String AUTHORITY = "com.tvpicker.shim.documents";

    private static final String[] DEFAULT_ROOT_PROJECTION = {
            Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_MIME_TYPES
    };

    private static final String[] DEFAULT_DOC_PROJECTION = {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Mounted storage volumes that have a usable directory. */
    static List<StorageVolume> mountedVolumes(Context c) {
        List<StorageVolume> out = new ArrayList<>();
        StorageManager sm = c.getSystemService(StorageManager.class);
        if (sm == null) return out;
        for (StorageVolume v : sm.getStorageVolumes()) {
            if (v.getDirectory() != null) out.add(v);
        }
        return out;
    }

    // ---------------------------------------------------------------- roots

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        Context ctx = getContext();
        for (StorageVolume v : mountedVolumes(ctx)) {
            File dir = v.getDirectory();
            MatrixCursor.RowBuilder r = c.newRow();
            r.add(Root.COLUMN_ROOT_ID, dir.getAbsolutePath());
            r.add(Root.COLUMN_DOCUMENT_ID, dir.getAbsolutePath());
            r.add(Root.COLUMN_TITLE, v.getDescription(ctx));
            r.add(Root.COLUMN_FLAGS,
                    Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD | Root.FLAG_LOCAL_ONLY);
            r.add(Root.COLUMN_ICON, android.R.drawable.ic_menu_gallery);
        }
        return c;
    }

    // ------------------------------------------------------------ documents

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(resolve(projection));
        File f = fileFor(documentId);
        if (!f.exists()) throw new FileNotFoundException("Missing: " + documentId);
        includeFile(c, f);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(resolve(projection));
        File dir = fileFor(parentDocumentId);
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File k : kids) includeFile(c, k);
        }
        c.setNotificationUri(getContext().getContentResolver(),
                DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId));
        return c;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        File f = fileFor(documentId);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        try {
            String p = new File(parentDocumentId).getCanonicalPath();
            String d = new File(documentId).getCanonicalPath();
            if (d.equals(p)) return true;
            return d.startsWith(p.endsWith("/") ? p : p + "/");
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File parent = fileFor(parentDocumentId);
        if (!parent.isDirectory()) throw new FileNotFoundException("Parent is not a directory");

        String name = sanitize(displayName);
        if (!Document.MIME_TYPE_DIR.equals(mimeType) && name.indexOf('.') < 0) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (ext != null) name = name + "." + ext;
        }
        File target = uniqueChild(parent, name);
        try {
            if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                if (!target.mkdir()) throw new FileNotFoundException("Could not create folder");
            } else {
                if (!target.createNewFile()) throw new FileNotFoundException("Could not create file");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Create failed: " + e.getMessage());
        }
        notifyChildren(parentDocumentId);
        return target.getAbsolutePath();
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File f = fileFor(documentId);
        File parent = f.getParentFile();
        if (!deleteRecursive(f)) throw new FileNotFoundException("Delete failed: " + documentId);
        if (parent != null) notifyChildren(parent.getAbsolutePath());
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File f = fileFor(documentId);
        File parent = f.getParentFile();
        if (parent == null) throw new FileNotFoundException("Cannot rename root");
        File target = new File(parent, sanitize(displayName));
        if (target.getAbsolutePath().equals(f.getAbsolutePath())) return null;
        if (target.exists()) throw new FileNotFoundException("Target already exists");
        if (!f.renameTo(target)) throw new FileNotFoundException("Rename failed");
        notifyChildren(parent.getAbsolutePath());
        return target.getAbsolutePath();
    }

    // -------------------------------------------------------------- helpers

    private static String[] resolve(String[] projection) {
        return projection != null ? projection : DEFAULT_DOC_PROJECTION;
    }

    /** Maps a document ID to a File, refusing anything outside mounted volumes. */
    private File fileFor(String documentId) throws FileNotFoundException {
        File f = new File(documentId);
        try {
            String canon = f.getCanonicalPath();
            for (StorageVolume v : mountedVolumes(getContext())) {
                String root = v.getDirectory().getCanonicalPath();
                if (canon.equals(root) || canon.startsWith(root + "/")) return f;
            }
        } catch (IOException e) {
            // fall through
        }
        throw new FileNotFoundException("Not allowed: " + documentId);
    }

    private void includeFile(MatrixCursor c, File f) {
        int flags = 0;
        String mime;
        if (f.isDirectory()) {
            mime = Document.MIME_TYPE_DIR;
            if (f.canWrite()) {
                flags |= Document.FLAG_DIR_SUPPORTS_CREATE
                        | Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_RENAME;
            }
        } else {
            mime = mimeOf(f);
            if (f.canWrite()) {
                flags |= Document.FLAG_SUPPORTS_WRITE
                        | Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_RENAME;
            }
        }
        MatrixCursor.RowBuilder r = c.newRow();
        r.add(Document.COLUMN_DOCUMENT_ID, f.getAbsolutePath());
        r.add(Document.COLUMN_DISPLAY_NAME, displayNameOf(f));
        r.add(Document.COLUMN_SIZE, f.length());
        r.add(Document.COLUMN_MIME_TYPE, mime);
        r.add(Document.COLUMN_LAST_MODIFIED, f.lastModified());
        r.add(Document.COLUMN_FLAGS, flags);
    }

    /** Volume roots get the volume's friendly name instead of e.g. "0". */
    private String displayNameOf(File f) {
        for (StorageVolume v : mountedVolumes(getContext())) {
            if (v.getDirectory().getAbsolutePath().equals(f.getAbsolutePath())) {
                return v.getDescription(getContext());
            }
        }
        return f.getName();
    }

    private static String mimeOf(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    private static String sanitize(String name) {
        String n = name == null ? "untitled" : name.replace('/', '_').trim();
        return n.isEmpty() ? "untitled" : n;
    }

    private static File uniqueChild(File parent, String name) {
        File f = new File(parent, name);
        if (!f.exists()) return f;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            f = new File(parent, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
        return f;
    }

    private static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (!deleteRecursive(k)) return false;
                }
            }
        }
        return f.delete();
    }

    private void notifyChildren(String parentDocumentId) {
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId), null);
    }
}
