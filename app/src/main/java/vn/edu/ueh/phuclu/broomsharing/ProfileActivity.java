package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityProfileBinding;
import vn.edu.ueh.phuclu.broomsharing.ui.host.AddBuildingActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostApplyActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostDashboardActivity;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnMenuCreateSpace.setOnClickListener(v -> {
            startActivity(new Intent(this, CreateSpaceActivity.class));
        });

        binding.btnMenuMySpaces.setOnClickListener(v -> {
            startActivity(new Intent(this, HostDashboardActivity.class));
        });

        binding.btnMenuAddBuilding.setOnClickListener(v -> {
            startActivity(new Intent(this, AddBuildingActivity.class));
        });

        binding.btnMenuHostApply.setOnClickListener(v -> {
            startActivity(new Intent(this, HostApplyActivity.class));
        });
    }
}
