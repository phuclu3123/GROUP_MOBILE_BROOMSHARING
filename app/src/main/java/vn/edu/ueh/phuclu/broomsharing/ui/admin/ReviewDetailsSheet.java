package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.SheetAdminDetailsBinding;
import vn.edu.ueh.phuclu.broomsharing.model.ReviewItem;

import java.util.function.Consumer;

// Tách từng dòng thông tin thành thẻ dễ đọc, có vùng cuộn và thao tác luôn nằm trong màn hình.
public final class ReviewDetailsSheet {
    private ReviewDetailsSheet() {}

    // Chỉ trình bày dữ liệu hồ sơ; thao tác mở tệp được chuyển về Activity đang giữ quyền phiên.
    public static void show(Context context, ReviewItem item, Consumer<ReviewItem> evidence) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        SheetAdminDetailsBinding b = SheetAdminDetailsBinding.inflate(LayoutInflater.from(context));
        b.detailTitle.setText(item.title);
        b.detailKind.setText(kind(item.kind));
        for (String line : item.details.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
            row.setBackgroundResource(R.drawable.bg_card);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(context, 10);
            b.detailRows.addView(row, params);
            int colon = line.indexOf(':');
            if (colon > 0 && colon < 32 && !line.startsWith("http")) {
                TextView label =
                        text(context, line.substring(0, colon), 13, R.color.text_secondary);
                label.setTypeface(null, Typeface.BOLD);
                row.addView(label);
                String value = line.substring(colon + 1).trim();
                row.addView(
                        text(
                                context,
                                value.isEmpty() ? "Chưa cung cấp" : value,
                                16,
                                R.color.text_primary));
            } else row.addView(text(context, line, 16, R.color.text_primary));
        }
        if (!item.evidenceRefs.isEmpty()) {
            b.detailEvidence.setVisibility(View.VISIBLE);
            b.detailEvidence.setText("Xem minh chứng (" + item.evidenceRefs.size() + ")");
            b.detailEvidence.setOnClickListener(
                    v -> {
                        dialog.dismiss();
                        evidence.accept(item);
                    });
        }
        b.detailClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(b.getRoot());
        dialog.setOnShowListener(
                ignored -> {
                    View sheet =
                            dialog.findViewById(
                                    com.google.android.material.R.id.design_bottom_sheet);
                    if (sheet == null) return;
                    int height =
                            (int) (context.getResources().getDisplayMetrics().heightPixels * .88f);
                    sheet.getLayoutParams().height = height;
                    // Giới hạn cả nội dung để vùng cuộn không đẩy thanh thao tác ra ngoài sheet.
                    b.getRoot().getLayoutParams().height = height;
                    b.getRoot().requestLayout();
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                    behavior.setSkipCollapsed(true);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    sheet.requestLayout();
                });
        dialog.show();
    }

    private static TextView text(Context context, String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(context, color));
        view.setTextIsSelectable(true);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private static String kind(String value) {
        switch (value) {
            case "HOST":
                return "HỒ SƠ ĐĂNG KÝ HOST";
            case "VERIFICATION":
                return "XÁC MINH KHÔNG GIAN";
            case "ROOM":
            case "ROOM_STATUS":
                return "THÔNG TIN KHÔNG GIAN";
            case "HOST_STATUS":
                return "THÔNG TIN HOST";
            case "AUDIT":
                return "NHẬT KÝ NGHIỆP VỤ";
            default:
                return "CHI TIẾT BÁO CÁO";
        }
    }
}
