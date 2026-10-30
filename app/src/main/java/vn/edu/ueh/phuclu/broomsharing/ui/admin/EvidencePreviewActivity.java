package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.view.View;
import android.view.WindowManager;

import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityEvidencePreviewBinding;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;

import java.io.*;

// Xem tệp trong app: path cũ theo Storage Rules, tệp demo mới là URL Cloudinary công khai.
public class EvidencePreviewActivity extends SessionActivity {
    private ActivityEvidencePreviewBinding b;
    private PdfRenderer pdf;
    private File temporary;
    private int page;
    private Bitmap bitmap;

    @Override
    protected int pageLayout() {
        return R.layout.activity_evidence_preview;
    }

    @Override
    protected boolean requiresAdmin() {
        return true;
    }

    @Override
    protected void onCreate(Bundle state) {
        if (state != null) {
            page = state.getInt("page");
            fileIndex = state.getInt("fileIndex");
            java.util.ArrayList<String> restored = state.getStringArrayList("viewed");
            if (restored != null) viewed.addAll(restored);
        }
        super.onCreate(state);
    }

    // Chuyển tệp ngay trong màn xem; chỉ ghi nhận những tệp đã giải mã thành công.
    private AdminEvidenceViewModel model;
    private java.util.ArrayList<String> files = new java.util.ArrayList<>();
    private final java.util.LinkedHashSet<String> viewed = new java.util.LinkedHashSet<>();
    private int fileIndex;

    @Override
    protected void onProfileReady(UserProfile profile) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        b =
                ActivityEvidencePreviewBinding.bind(
                        findViewById(vn.edu.ueh.phuclu.broomsharing.R.id.workspaceRoot));
        vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation.applyInsets(this);
        model = new ViewModelProvider(this).get(AdminEvidenceViewModel.class);
        java.util.ArrayList<String> requested = getIntent().getStringArrayListExtra("FILES");
        if (requested != null)
            for (String file : requested)
                if (file != null && !file.trim().isEmpty() && !files.contains(file))
                    files.add(file);
        if (files.isEmpty() && getIntent().getStringExtra("PATH") != null)
            files.add(getIntent().getStringExtra("PATH"));
        b.documentTitle.setText(
                getIntent().getStringExtra("TITLE") == null
                        ? "Minh chứng hồ sơ"
                        : getIntent().getStringExtra("TITLE"));
        b.close.setOnClickListener(v -> finish());
        b.previous.setOnClickListener(
                v -> {
                    if (pdf != null && page > 0) {
                        page--;
                        renderPage();
                    }
                });
        b.next.setOnClickListener(
                v -> {
                    if (pdf != null && page + 1 < pdf.getPageCount()) {
                        page++;
                        renderPage();
                    }
                });
        b.resetZoom.setOnClickListener(v -> b.preview.resetZoom());
        b.retry.setOnClickListener(
                v -> {
                    if (!files.isEmpty())
                        model.load(getIntent().getStringExtra("ID"), files.get(fileIndex));
                });
        if (files.isEmpty()) {
            error("Hồ sơ chưa có tệp minh chứng.");
            b.retry.setVisibility(View.GONE);
            return;
        }
        fileIndex = Math.max(0, Math.min(fileIndex, files.size() - 1));
        for (int i = 0; i < files.size(); i++) {
            final int index = i;
            com.google.android.material.button.MaterialButton tab =
                    new com.google.android.material.button.MaterialButton(
                            this,
                            null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle);
            tab.setText(
                    "HOST".equals(getIntent().getStringExtra("KIND")) && files.size() == 2
                            ? i == 0 ? "Mặt trước" : "Mặt sau"
                            : "Tệp " + (i + 1));
            tab.setTextSize(14);
            tab.setAllCaps(false);
            android.widget.LinearLayout.LayoutParams params =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
            b.fileTabs.addView(tab, params);
            tab.setOnClickListener(
                    v -> {
                        if (index != fileIndex) {
                            fileIndex = index;
                            page = 0;
                            loadSelected();
                        }
                    });
        }
        model.evidence.observe(
                this,
                state -> {
                    if (state == null || !model.hasFile(files.get(fileIndex))) return;
                    b.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    b.errorPanel.setVisibility(View.GONE);
                    b.retry.setEnabled(!state.loading);
                    if (state.loading) {
                        closePdf();
                        b.preview.setImageDrawable(null);
                        b.pageControls.setVisibility(View.GONE);
                        b.resetZoom.setVisibility(View.GONE);
                        if (bitmap != null) {
                            bitmap.recycle();
                            bitmap = null;
                        }
                    } else if (state.error != null) error(state.error);
                    else if (state.data != null) show(state.data);
                });
        loadSelected();
    }

    // Đổi nhãn tab và chọn tệp; tệp đang có trong ViewModel không cần tải lại.
    private void loadSelected() {
        for (int i = 0; i < b.fileTabs.getChildCount(); i++) {
            View tab = b.fileTabs.getChildAt(i);
            tab.setSelected(i == fileIndex);
            ((android.widget.TextView) tab)
                    .setTypeface(null, i == fileIndex ? Typeface.BOLD : Typeface.NORMAL);
            tab.setAlpha(i == fileIndex ? 1f : .65f);
        }
        String path = files.get(fileIndex);
        b.fileInfo.setText(
                "Tệp "
                        + (fileIndex + 1)
                        + "/"
                        + files.size()
                        + " · "
                        + android.net.Uri.decode(path.substring(path.lastIndexOf('/') + 1)));
        // ViewModel giữ byte qua xoay màn hình, không tải lại cùng tệp đã nhận.
        if (!model.hasFile(path)) model.load(getIntent().getStringExtra("ID"), path);
    }

    // Chỉ hiện nút thử lại khi mở tệp thất bại; không ghi nhận minh chứng đã xem.
    private void error(String message) {
        b.progress.setVisibility(View.GONE);
        b.errorPanel.setVisibility(View.VISIBLE);
        b.feedback.setText(message);
        b.pageControls.setVisibility(View.GONE);
        b.resetZoom.setVisibility(View.GONE);
    }

    // Nhận diện PDF bằng chữ ký byte, còn ảnh được giải mã ở kích thước giới hạn.
    private void show(byte[] data) {
        closePdf();
        b.preview.setImageDrawable(null);
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        try {
            boolean isPdf =
                    data.length >= 4
                            && data[0] == '%'
                            && data[1] == 'P'
                            && data[2] == 'D'
                            && data[3] == 'F';
            if (isPdf) {
                temporary = File.createTempFile("verification-", ".pdf", getCacheDir());
                try (FileOutputStream out = new FileOutputStream(temporary)) {
                    out.write(data);
                }
                pdf =
                        new PdfRenderer(
                                ParcelFileDescriptor.open(
                                        temporary, ParcelFileDescriptor.MODE_READ_ONLY));
                page = Math.min(page, Math.max(0, pdf.getPageCount() - 1));
                renderPage();
            } else {
                BitmapFactory.Options size = new BitmapFactory.Options();
                size.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, size);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (size.outWidth / options.inSampleSize > 1800
                        || size.outHeight / options.inSampleSize > 2400) options.inSampleSize *= 2;
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (bitmap == null) throw new IOException("Không đọc được ảnh bằng chứng.");
                b.preview.setImageBitmap(bitmap);
                b.pageControls.setVisibility(View.GONE);
                b.preview.resetZoom();
                b.resetZoom.setVisibility(View.VISIBLE);
                markViewed();
            }
        } catch (Exception e) {
            error(
                    "Không mở được nội dung tệp. "
                            + (e.getMessage() == null ? "Vui lòng thử lại." : e.getMessage()));
        }
    }

    // Thay bitmap trang trước, cập nhật số trang và giới hạn nút điều hướng.
    private void renderPage() {
        try (PdfRenderer.Page p = pdf.openPage(page)) {
            float scale = Math.min(1280f / p.getWidth(), 1800f / p.getHeight());
            Bitmap next =
                    Bitmap.createBitmap(
                            Math.max(1, (int) (p.getWidth() * scale)),
                            Math.max(1, (int) (p.getHeight() * scale)),
                            Bitmap.Config.ARGB_8888);
            next.eraseColor(Color.WHITE);
            p.render(next, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            b.preview.setImageBitmap(next);
            if (bitmap != null) bitmap.recycle();
            bitmap = next;
            b.previous.setVisibility(View.VISIBLE);
            b.next.setVisibility(View.VISIBLE);
            b.previous.setEnabled(page > 0);
            b.next.setEnabled(page + 1 < pdf.getPageCount());
            b.pageControls.setVisibility(View.VISIBLE);
            b.pageNumber.setText((page + 1) + " / " + pdf.getPageCount());
            b.preview.resetZoom();
            b.resetZoom.setVisibility(View.VISIBLE);
            markViewed();
        } catch (Exception e) {
            error(
                    "Không mở được nội dung tệp. "
                            + (e.getMessage() == null ? "Vui lòng thử lại." : e.getMessage()));
        }
    }

    // Trả toàn bộ tệp đã hiển thị thành công cho màn xét duyệt, kể cả sau khi chuyển tab.
    private void markViewed() {
        if (files.isEmpty()) return;
        viewed.add(files.get(fileIndex));
        setResult(
                RESULT_OK,
                new Intent()
                        .putExtra("ID", getIntent().getStringExtra("ID"))
                        .putExtra("PATH", files.get(fileIndex))
                        .putStringArrayListExtra("VIEWED", new java.util.ArrayList<>(viewed)));
    }

    // Đóng tài nguyên PDF khi thay tệp hoặc hủy màn hình để không giữ descriptor của bản xem cũ.
    private void closePdf() {
        if (pdf != null) {
            pdf.close();
            pdf = null;
        }
        if (temporary != null) {
            temporary.delete();
            temporary = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", page);
        state.putInt("fileIndex", fileIndex);
        state.putStringArrayList("viewed", new java.util.ArrayList<>(viewed));
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        closePdf();
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
        super.onDestroy();
    }
}
