package vn.edu.ueh.phuclu.broomsharing.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.*;

/*
   Lưu bản chụp dữ liệu đã đọc thành công để xem lại khi mất mạng.
   UID tách dữ liệu từng tài khoản; SQLite không thay thế Firestore hay cấp quyền ghi.
   Các model Serializable được lưu vào BLOB, kèm thời điểm nhận từ server.
*/
public final class OfflineDatabase extends SQLiteOpenHelper {
    public static final String NAME = "broom_offline.db";
    private static final int MAX_BYTES = 750_000;

    public OfflineDatabase(Context context, String name) {
        super(context.getApplicationContext(), name, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE snapshots (uid TEXT NOT NULL, kind TEXT NOT NULL, item_id TEXT NOT"
                    + " NULL, payload BLOB NOT NULL, saved_at INTEGER NOT NULL, PRIMARY"
                    + " KEY(uid,kind,item_id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Bản lưu có thể tải lại; không chuyển dữ liệu cũ thành trạng thái nghiệp vụ mới.
        db.execSQL("DROP TABLE IF EXISTS snapshots");
        onCreate(db);
    }

    // Kết quả đọc kèm thời điểm lưu để giao diện phân biệt bản xem cũ với dữ liệu online.
    public static final class Saved<T> {
        public final T data;
        public final long savedAt;

        public Saved(T data, long savedAt) {
            this.data = data;
            this.savedAt = savedAt;
        }
    }

    // ContentValues và tham số ? giữ dữ liệu tách khỏi câu lệnh SQL.
    public void put(String uid, String kind, String id, Serializable data, long savedAt)
            throws IOException {
        if (uid == null || uid.isEmpty() || data == null) return;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(data);
        }
        if (bytes.size() > MAX_BYTES) throw new IOException("Bản lưu vượt giới hạn.");
        ContentValues values = new ContentValues();
        values.put("uid", uid);
        values.put("kind", kind);
        values.put("item_id", id);
        values.put("payload", bytes.toByteArray());
        values.put("saved_at", savedAt);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (db.insertWithOnConflict("snapshots", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    < 0) throw new IOException("Chưa lưu được dữ liệu ngoại tuyến.");
            db.execSQL(
                    "DELETE FROM snapshots WHERE uid=? AND kind!='profile' AND rowid NOT IN (SELECT"
                        + " rowid FROM snapshots WHERE uid=? AND kind!='profile' ORDER BY saved_at"
                        + " DESC,rowid DESC LIMIT 400)",
                    new Object[] {uid, uid});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // Đóng Cursor; bỏ bản lưu hỏng hoặc khác phiên bản model thay vì làm app văng.
    public <T> Saved<T> read(String uid, String kind, String id, Class<T> type) {
        if (uid == null || uid.isEmpty()) return null;
        try (Cursor row =
                getReadableDatabase()
                        .query(
                                "snapshots",
                                new String[] {"payload", "saved_at"},
                                "uid=? AND kind=? AND item_id=?",
                                new String[] {uid, kind, id},
                                null,
                                null,
                                null)) {
            if (!row.moveToFirst()) return null;
            try (ObjectInputStream input =
                    new ObjectInputStream(new ByteArrayInputStream(row.getBlob(0)))) {
                return new Saved<>(type.cast(input.readObject()), row.getLong(1));
            } catch (IOException | ClassNotFoundException | ClassCastException invalid) {
                remove(uid, kind, id);
                return null;
            }
        }
    }

    // Chỉ xóa một bản lưu trong đúng phạm vi UID, không thay đổi dữ liệu Firestore.
    public void remove(String uid, String kind, String id) {
        getWritableDatabase()
                .delete(
                        "snapshots",
                        "uid=? AND kind=? AND item_id=?",
                        new String[] {uid, kind, id});
    }

    // Dọn dữ liệu local của tài khoản khi đăng xuất hoặc phiên không còn hợp lệ.
    public void clear(String uid) {
        if (uid != null) getWritableDatabase().delete("snapshots", "uid=?", new String[] {uid});
    }
}
