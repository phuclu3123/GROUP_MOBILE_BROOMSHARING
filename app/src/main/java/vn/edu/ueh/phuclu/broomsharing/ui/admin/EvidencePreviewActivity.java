package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityEvidencePreviewBinding;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import java.io.*;

/** Views protected bytes without creating a public download URL or sharing a private document. */
public class EvidencePreviewActivity extends SessionActivity {
    private ActivityEvidencePreviewBinding b;
    private PdfRenderer pdf;
    private File temporary;
    private int page;
    private Bitmap bitmap;
    @Override protected int pageLayout() { return R.layout.activity_evidence_preview; }
    @Override protected boolean requiresAdmin() { return true; }
    @Override protected void onCreate(Bundle state) { if (state != null) page=state.getInt("page"); super.onCreate(state); }
    @Override protected void onProfileReady(UserProfile profile) {
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        b=ActivityEvidencePreviewBinding.bind(((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0));
        AdminEvidenceViewModel model=new ViewModelProvider(this).get(AdminEvidenceViewModel.class);
        b.close.setOnClickListener(v->finish());
        b.previous.setOnClickListener(v->{if(pdf!=null&&page>0){page--;renderPage();}});
        b.next.setOnClickListener(v->{if(pdf!=null&&page+1<pdf.getPageCount()){page++;renderPage();}});
        b.retry.setOnClickListener(v->model.load(getIntent().getStringExtra("ID"),getIntent().getStringExtra("PATH")));
        model.evidence.observe(this,state->{
            if(state==null)return;
            b.retry.setEnabled(!state.loading);
            b.feedback.setText(state.loading?getString(R.string.admin_loading):state.error==null?"":state.error);
            if(state.data!=null) show(state.data);
        });
        if(model.evidence.getValue()==null)model.load(getIntent().getStringExtra("ID"),getIntent().getStringExtra("PATH"));
    }
    private void show(byte[] data) {
        closePdf(); b.preview.setImageDrawable(null);
        if(bitmap!=null){bitmap.recycle();bitmap=null;}
        try {
            boolean isPdf=data.length>=4 && data[0]=='%' && data[1]=='P' && data[2]=='D' && data[3]=='F';
            if(isPdf){
                temporary=File.createTempFile("verification-", ".pdf",getCacheDir());
                try(FileOutputStream out=new FileOutputStream(temporary)){out.write(data);}
                pdf=new PdfRenderer(ParcelFileDescriptor.open(temporary,ParcelFileDescriptor.MODE_READ_ONLY));
                page=Math.min(page,Math.max(0,pdf.getPageCount()-1));renderPage();
            }else{
                BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data,0,data.length,size);
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(size.outWidth/options.inSampleSize>1800||size.outHeight/options.inSampleSize>2400)options.inSampleSize*=2;
                bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);
                if(bitmap==null)throw new IOException("Không đọc được ảnh bằng chứng.");
                b.preview.setImageBitmap(bitmap);b.previous.setVisibility(View.GONE);b.next.setVisibility(View.GONE);markViewed();
            }
        }catch(Exception e){b.feedback.setText(e.getMessage());}
    }
    private void renderPage(){
        try(PdfRenderer.Page p=pdf.openPage(page)){
            float scale=Math.min(1280f/p.getWidth(),1800f/p.getHeight());
            Bitmap next=Bitmap.createBitmap(Math.max(1,(int)(p.getWidth()*scale)),Math.max(1,(int)(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);
            next.eraseColor(Color.WHITE);p.render(next,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            b.preview.setImageBitmap(next);if(bitmap!=null)bitmap.recycle();bitmap=next;
            b.previous.setVisibility(View.VISIBLE);b.next.setVisibility(View.VISIBLE);
            b.previous.setEnabled(page>0);b.next.setEnabled(page+1<pdf.getPageCount());
            b.feedback.setText("Trang "+(page+1)+"/"+pdf.getPageCount());markViewed();
        }catch(Exception e){b.feedback.setText(e.getMessage());}
    }
    private void markViewed(){setResult(RESULT_OK,new Intent().putExtra("ID",getIntent().getStringExtra("ID")).putExtra("PATH",getIntent().getStringExtra("PATH")));}
    private void closePdf(){if(pdf!=null){pdf.close();pdf=null;}if(temporary!=null){temporary.delete();temporary=null;}}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("page",page);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){closePdf();if(bitmap!=null){bitmap.recycle();bitmap=null;}super.onDestroy();}
}
