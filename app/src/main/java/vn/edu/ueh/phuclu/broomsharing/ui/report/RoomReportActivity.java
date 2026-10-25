package vn.edu.ueh.phuclu.broomsharing.ui.report;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityRoomReportBinding;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.repository.ReportRepository;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.ui.room.SpaceDetailActivity;

/**
 * Màn hình báo cáo phòng: nhận nội dung và kiểm tra thông tin trước khi gửi. Chuyển yêu cầu sang
 * ReportViewModel và hiển thị kết quả xử lý.
 */
public class RoomReportActivity extends SessionActivity {
    @Override
    protected int pageLayout() {
        return R.layout.activity_room_report;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        ActivityRoomReportBinding b =
                ActivityRoomReportBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        b.reportToolbar.setNavigationOnClickListener(v -> finish());
        b.reportReason.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        ReportRepository.REASONS));
        ReportViewModel model = new ViewModelProvider(this).get(ReportViewModel.class);
        b.reportDescription.setText(model.draft);
        b.reportReason.setSelection(model.selectedReason);
        b.reportDescription.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        model.draft = s.toString();
                    }

                    public void afterTextChanged(Editable e) {}
                });
        b.reportReason.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        model.selectedReason = pos;
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });
        b.reportSubmit.setOnClickListener(
                v -> {
                    String note =
                            b.reportDescription.getText() == null
                                    ? ""
                                    : b.reportDescription.getText().toString().trim();
                    if (note.length() < 20) {
                        b.reportDescriptionLayout.setError("Mô tả cần ít nhất 20 ký tự.");
                        return;
                    }
                    b.reportDescriptionLayout.setError(null);
                    model.submit(
                            getIntent().getStringExtra(SpaceDetailActivity.ROOM_ID),
                            getIntent().getStringExtra("bookingId"),
                            b.reportReason.getSelectedItem().toString(),
                            note);
                });
        model.result.observe(
                this,
                s -> {
                    boolean done = s.data != null;
                    b.reportProgress.setVisibility(s.loading ? View.VISIBLE : View.GONE);
                    b.reportSubmit.setEnabled(!s.loading && !done);
                    b.reportReason.setEnabled(!s.loading && !done);
                    b.reportDescription.setEnabled(!s.loading && !done);
                    b.reportStatus.setText(
                            s.loading
                                    ? "Đang gửi, chờ xác nhận từ hệ thống…"
                                    : s.error != null
                                            ? s.error
                                            : done
                                                    ? "Đã gửi báo cáo. Mã: "
                                                            + s.data
                                                            + "\n"
                                                            + "Admin sẽ kiểm tra và xử lý; đây chưa"
                                                            + " phải kết luận vi phạm."
                                                    : "");
                });
    }
}
