package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import vn.edu.ueh.phuclu.broomsharing.adapter.ReviewAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityAdminDashboardBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.admin.*;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.UiForms;

public class AdminDashboardActivity extends SessionActivity {
    private final String[] sections={"HOST","ROOM","HOST_STATUS","ROOM_STATUS","VERIFICATION","AUDIT","REPORT"};
    private AdminViewModel model;
    private ActivityAdminDashboardBinding b;
    private String restoredSection;
    private final ActivityResultLauncher<Intent> evidence = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result->{
        if(result.getResultCode()==RESULT_OK && result.getData()!=null && model!=null)
            model.viewedEvidence.add(result.getData().getStringExtra("ID")+":"+result.getData().getStringExtra("PATH"));
    });
    @Override protected int pageLayout(){return R.layout.activity_admin_dashboard;}
    @Override protected boolean requiresAdmin(){return true;}
    @Override protected void onCreate(Bundle state){if(state!=null)restoredSection=state.getString("section");super.onCreate(state);}
    @Override protected void onProfileReady(UserProfile profile){
        b=ActivityAdminDashboardBinding.bind(((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0));
        model=new ViewModelProvider(this).get(AdminViewModel.class);
        if(restoredSection!=null)model.selected=restoredSection;
        b.adminSignOut.setOnClickListener(v->signOut());
        b.btnRefresh.setOnClickListener(v->model.load(model.selected));
        b.reviewList.setLayoutManager(new LinearLayoutManager(this));
        int selected=0;for(int i=0;i<sections.length;i++)if(sections[i].equals(model.selected))selected=i;
        b.adminSections.setSelection(selected);
        b.adminSections.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View v,int index,long id){
                if(!sections[index].equals(model.selected)||model.items.getValue()==null)model.load(sections[index]);
            }
        });
        model.items.observe(this,state->{
            if(state==null)return;
            b.progressBar.setVisibility(state.loading?View.VISIBLE:View.GONE);
            b.btnRefresh.setEnabled(!state.loading);
            if(state.loading||state.error!=null)b.reviewList.setAdapter(null);
            b.tvFeedback.setText(state.loading?getString(R.string.admin_loading):state.error==null?"":state.error);
            if(state.data!=null){
                b.tvFeedback.setText(state.data.isEmpty()?getString(R.string.admin_empty):"Số mục: "+state.data.size()
                    +("AUDIT".equals(model.selected)?" • 100 thao tác mới nhất":" • Tải lại để cập nhật"));
                b.reviewList.setAdapter(new ReviewAdapter(state.data,this::select,this::viewEvidence));
            }
        });
        model.operation.observe(this,state->{
            if(state==null)return;
            UiForms.enabled(b.getRoot(),!state.loading);
            b.progressBar.setVisibility(state.loading?View.VISIBLE:View.GONE);
            if(state.error!=null)b.tvFeedback.setText(state.error);
            if(state.data!=null){model.operation.setValue(null);model.load(model.selected);}
        });
        if(model.items.getValue()==null)model.load(model.selected);
    }
    private void viewEvidence(ReviewItem item){
        if(item.evidenceRefs.isEmpty()){b.tvFeedback.setText("Hồ sơ chưa có tệp bằng chứng. Có thể từ chối kèm lý do.");return;}
        String[] labels=new String[item.evidenceRefs.size()];
        for(int i=0;i<labels.length;i++){String path=item.evidenceRefs.get(i);labels[i]=(i+1)+". "+path.substring(path.lastIndexOf('/')+1);}
        new AlertDialog.Builder(this).setTitle(R.string.admin_evidence).setItems(labels,(d,index)->
            evidence.launch(new Intent(this,EvidencePreviewActivity.class).putExtra("ID",item.id).putExtra("PATH",item.evidenceRefs.get(index))))
            .setNegativeButton(android.R.string.cancel,null).show();
    }
    private void select(ReviewItem item,boolean approve){
        if("VERIFICATION".equals(item.kind)&&approve&&!model.evidenceViewed(item)){
            b.tvFeedback.setText(R.string.admin_evidence_required);viewEvidence(item);return;
        }
        boolean reasonRequired=!approve||item.kind.endsWith("_STATUS")||"VERIFICATION".equals(item.kind);
        EditText input=new EditText(this);input.setHint(R.string.admin_reason_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(R.string.admin_confirm).setMessage(item.title)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.admin_submit,null);
        if(reasonRequired)builder.setView(input);
        AlertDialog dialog=builder.create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String reason=input.getText().toString().trim();
            if(reasonRequired&&(reason.isEmpty()||reason.length()>1000)){input.setError(getString(R.string.admin_reason_error));return;}
            model.review(item,approve,reason);dialog.dismiss();
        }));dialog.show();
    }
    @Override protected void onSaveInstanceState(Bundle state){if(model!=null)state.putString("section",model.selected);super.onSaveInstanceState(state);}
}