package vn.edu.ueh.phuclu.broomsharing;
import android.view.View;
import android.widget.ArrayAdapter;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityRoomReportBinding;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.repository.ReportRepository;
import vn.edu.ueh.phuclu.broomsharing.ui.ReportViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
public class RoomReportActivity extends SessionActivity {
    @Override protected int pageLayout(){return R.layout.activity_room_report;}
    @Override protected void onProfileReady(UserProfile profile){
        ActivityRoomReportBinding b=ActivityRoomReportBinding.bind(findViewById(R.id.workspaceRoot));ManagementNavigation.applyInsets(this);b.reportToolbar.setNavigationOnClickListener(v->finish());
        b.reportReason.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ReportRepository.REASONS));ReportViewModel model=new ViewModelProvider(this).get(ReportViewModel.class);
        b.reportDescription.setText(model.draft);b.reportReason.setSelection(model.selectedReason);
        b.reportDescription.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){model.draft=s.toString();}public void afterTextChanged(android.text.Editable e){}});
        b.reportReason.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){model.selectedReason=pos;}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        b.reportSubmit.setOnClickListener(v->{String note=b.reportDescription.getText()==null?"":b.reportDescription.getText().toString().trim();if(note.length()<20){b.reportDescriptionLayout.setError("Mô tả cần ít nhất 20 ký tự.");return;}b.reportDescriptionLayout.setError(null);model.submit(getIntent().getStringExtra(SpaceDetailActivity.ROOM_ID),b.reportReason.getSelectedItem().toString(),note);});
        model.result.observe(this,s->{boolean done=s.data!=null;b.reportProgress.setVisibility(s.loading?View.VISIBLE:View.GONE);b.reportSubmit.setEnabled(!s.loading&&!done);b.reportReason.setEnabled(!s.loading&&!done);b.reportDescription.setEnabled(!s.loading&&!done);b.reportStatus.setText(s.loading?"Đang gửi, chờ xác nhận từ hệ thống…":s.error!=null?s.error:done?"Đã gửi báo cáo. Mã: "+s.data+"\nAdmin sẽ kiểm tra và xử lý; đây chưa phải kết luận vi phạm.":"");});
    }
}
