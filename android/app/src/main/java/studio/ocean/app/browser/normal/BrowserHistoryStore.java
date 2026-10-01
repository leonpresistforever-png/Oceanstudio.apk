package studio.ocean.app.browser.normal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * SQLite-backed persistent history store for the Normal Browser.
 * HARD RULE: Never called from Private Browser (PDF 4 §10.2).
 * Supports time-grouped queries (Today, Yesterday, Older), domain deletion, and search.
 */
public final class BrowserHistoryStore {

    private static final String DB_NAME = "ocean_browser_history.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_HISTORY = "history";

    private static final String COL_ID = "id";
    private static final String COL_URL = "url";
    private static final String COL_TITLE = "title";
    private static final String COL_DOMAIN = "domain";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_VISIT_COUNT = "visit_count";

    public static final class HistoryItem {
        public final long id;
        public final String url;
        public final String title;
        public final String domain;
        public final long timestamp;
        public final int visitCount;

        public HistoryItem(long id, String url, String title, String domain, long timestamp, int visitCount) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.domain = domain;
            this.timestamp = timestamp;
            this.visitCount = visitCount;
        }
    }

    public static final class HistoryGroup {
        public final String title; // "Today", "Yesterday", "Older"
        public final List<HistoryItem> items;

        public HistoryGroup(String title, List<HistoryItem> items) {
            this.title = title;
            this.items = items;
        }
    }

    private static final class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_HISTORY + " ("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_URL + " TEXT NOT NULL, "
                    + COL_TITLE + " TEXT, "
                    + COL_DOMAIN + " TEXT, "
                    + COL_TIMESTAMP + " INTEGER NOT NULL, "
                    + COL_VISIT_COUNT + " INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE INDEX idx_history_timestamp ON " + TABLE_HISTORY + " (" + COL_TIMESTAMP + " DESC)");
            db.execSQL("CREATE INDEX idx_history_domain ON " + TABLE_HISTORY + " (" + COL_DOMAIN + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
            onCreate(db);
        }
    }

    private final DatabaseHelper dbHelper;

    public BrowserHistoryStore(@NonNull Context context) {
        this.dbHelper = new DatabaseHelper(context.getApplicationContext());
    }

    /**
     * Records a visited URL in Normal Browser history.
     * Extracts host and increments visit count if visited recently.
     */
    public synchronized void recordVisit(@NonNull String url, @Nullable String title) {
        if (url.trim().isEmpty() || url.startsWith("about:") || url.startsWith("data:")) {
            return;
        }
        String domain = "";
        try {
            Uri parsed = Uri.parse(url);
            if (parsed.getHost() != null) {
                domain = parsed.getHost().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
        }

        long now = System.currentTimeMillis();
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Check if same URL was visited in the last 15 minutes to avoid flood
        Cursor cursor = db.query(TABLE_HISTORY,
                new String[]{COL_ID, COL_VISIT_COUNT},
                COL_URL + " = ? AND " + COL_TIMESTAMP + " > ?",
                new String[]{url, String.valueOf(now - 15 * 60 * 1000)},
                null, null, COL_TIMESTAMP + " DESC", "1");

        try {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                int count = cursor.getInt(1);
                ContentValues values = new ContentValues();
                values.put(COL_TIMESTAMP, now);
                values.put(COL_VISIT_COUNT, count + 1);
                if (title != null && !title.isEmpty()) {
                    values.put(COL_TITLE, title);
                }
                db.update(TABLE_HISTORY, values, COL_ID + " = ?", new String[]{String.valueOf(id)});
                return;
            }
        } finally {
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put(COL_URL, url);
        values.put(COL_TITLE, title != null ? title : domain);
        values.put(COL_DOMAIN, domain);
        values.put(COL_TIMESTAMP, now);
        values.put(COL_VISIT_COUNT, 1);
        db.insert(TABLE_HISTORY, null, values);
    }

    /**
     * Retrieves history grouped into Today, Yesterday, and Older.
     */
    @NonNull
    public synchronized List<HistoryGroup> getHistoryGrouped() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();

        cal.add(Calendar.DAY_OF_YEAR, -1);
        long yesterdayStart = cal.getTimeInMillis();

        List<HistoryItem> todayItems = new ArrayList<>();
        List<HistoryItem> yesterdayItems = new ArrayList<>();
        List<HistoryItem> olderItems = new ArrayList<>();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(TABLE_HISTORY,
                new String[]{COL_ID, COL_URL, COL_TITLE, COL_DOMAIN, COL_TIMESTAMP, COL_VISIT_COUNT},
                null, null, null, null, COL_TIMESTAMP + " DESC", "500");

        try {
            while (cursor.moveToNext()) {
                HistoryItem item = new HistoryItem(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.getInt(5)
                );
                if (item.timestamp >= todayStart) {
                    todayItems.add(item);
                } else if (item.timestamp >= yesterdayStart) {
                    yesterdayItems.add(item);
                } else {
                    olderItems.add(item);
                }
            }
        } finally {
            cursor.close();
        }

        List<HistoryGroup> groups = new ArrayList<>();
        if (!todayItems.isEmpty()) groups.add(new HistoryGroup("Today", todayItems));
        if (!yesterdayItems.isEmpty()) groups.add(new HistoryGroup("Yesterday", yesterdayItems));
        if (!olderItems.isEmpty()) groups.add(new HistoryGroup("Older", olderItems));
        return groups;
    }

    /**
     * Searches history by title, URL, or domain.
     */
    @NonNull
    public synchronized List<HistoryItem> searchHistory(@NonNull String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String pattern = "%" + trimmed + "%";
        Cursor cursor = db.query(TABLE_HISTORY,
                new String[]{COL_ID, COL_URL, COL_TITLE, COL_DOMAIN, COL_TIMESTAMP, COL_VISIT_COUNT},
                COL_URL + " LIKE ? OR " + COL_TITLE + " LIKE ? OR " + COL_DOMAIN + " LIKE ?",
                new String[]{pattern, pattern, pattern},
                null, null, COL_TIMESTAMP + " DESC", "100");

        List<HistoryItem> results = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                results.add(new HistoryItem(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.getInt(5)
                ));
            }
        } finally {
            cursor.close();
        }
        return results;
    }

    /**
     * Deletes all history records for a given domain/host.
     */
    public synchronized int clearDomain(@NonNull String domain) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(TABLE_HISTORY, COL_DOMAIN + " = ?", new String[]{domain.toLowerCase(Locale.ROOT)});
    }

    /**
     * Deletes history records within a timestamp range.
     */
    public synchronized int clearTimeRange(long startMillis, long endMillis) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(TABLE_HISTORY, COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " <= ?",
                new String[]{String.valueOf(startMillis), String.valueOf(endMillis)});
    }

    /**
     * Clears all history.
     */
    public synchronized void clearAll() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
    }
}
