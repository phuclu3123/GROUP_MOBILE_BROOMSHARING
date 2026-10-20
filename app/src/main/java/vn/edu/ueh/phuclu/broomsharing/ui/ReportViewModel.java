package vn.edu.ueh.phuclu.broomsharing.ui;
import androidx.lifecycle.*;
import java.util.UUID;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
public class ReportViewModel extends ViewModel {
    public final MutableLiveData<UiState<String>> result=new MutableLiveData<>();
    public String draft="";public int selectedReason;
    private final String id=UUID.randomUUID().toString();private final ReportRepository repository=new ReportRepository();
    public void submit(String room,String reason,String note){if(result.getValue()!=null&&(result.getValue().loading||result.getValue().data!=null))return;result.setValue(UiState.loading());repository.submit(id,room,reason,note,new ResultCallback<String>(){public void onSuccess(String id){result.setValue(UiState.success(id));}public void onError(Exception e){result.setValue(UiState.error(e));}});}
}
