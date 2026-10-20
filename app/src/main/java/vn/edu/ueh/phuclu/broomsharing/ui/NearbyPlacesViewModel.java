package vn.edu.ueh.phuclu.broomsharing.ui;
import androidx.lifecycle.*;
import java.util.List;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
public class NearbyPlacesViewModel extends ViewModel {
    private final NearbyPlacesRepository repository=new NearbyPlacesRepository();
    public final MutableLiveData<UiState<List<NearbyPlace>>> places=new MutableLiveData<>();private boolean closed;
    public boolean fallbackOpened;
    public void load(MapPoint center){if(places.getValue()!=null&&places.getValue().loading)return;fallbackOpened=false;places.setValue(UiState.loading());repository.nearby(center,new ResultCallback<List<NearbyPlace>>(){public void onSuccess(List<NearbyPlace> p){if(!closed)places.setValue(UiState.success(p));}public void onError(Exception e){if(!closed)places.setValue(UiState.error(e));}});}
    public void ensure(MapPoint center){if(places.getValue()==null)load(center);}
    @Override protected void onCleared(){closed=true;repository.close();}
}
