package vn.edu.ueh.phuclu.broomsharing.ui.profile;

import android.net.Uri;
import android.view.View;
import android.widget.*;

import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.*;
import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.repository.RemoteImageClient;
import vn.edu.ueh.phuclu.broomsharing.utils.OnlineImages;

/*
   Dùng chung bộ chọn ảnh đại diện cho Guest và Host/Admin.
   Đăng ký picker khi Activity được tạo; upload/lưu dữ liệu thuộc ViewModel, không thuộc View.
*/
public final class AvatarEditor {
    private final AppCompatActivity activity;
    private final ActivityResultLauncher<String[]> picker;
    private ProfilePhotoViewModel model;

    public AvatarEditor(AppCompatActivity activity) {
        this.activity = activity;
        picker =
                activity.registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(),
                        uri -> {
                            if (uri != null && model != null) model.change(uri);
                        });
    }

    public static void urlDialog(
            AppCompatActivity activity, java.util.function.Consumer<Uri> selected) {
        EditText input = new EditText(activity);
        input.setHint(R.string.media_url_hint);
        input.setSingleLine(true);
        input.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.media_url_title)
                        .setView(input)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Dùng ảnh", null)
                        .create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            try {
                                                Uri uri =
                                                        Uri.parse(
                                                                RemoteImageClient.imageUrl(
                                                                                input.getText()
                                                                                        .toString())
                                                                        .toString());
                                                selected.accept(uri);
                                                dialog.dismiss();
                                            } catch (IllegalArgumentException e) {
                                                input.setError(e.getMessage());
                                            }
                                        }));
        dialog.show();
    }

    public void bind(UserProfile profile) {
        model = new ViewModelProvider(activity).get(ProfilePhotoViewModel.class);
        TextView initial = activity.findViewById(R.id.avatarInitial);
        initial.setText(
                profile.name == null || profile.name.isEmpty()
                        ? "B"
                        : profile.name.substring(0, 1));
        ImageView photo = activity.findViewById(R.id.avatarPhoto);
        View gallery = activity.findViewById(R.id.btnAvatarGallery),
                url = activity.findViewById(R.id.btnAvatarUrl),
                remove = activity.findViewById(R.id.btnAvatarRemove);
        gallery.setOnClickListener(
                v -> picker.launch(new String[] {"image/jpeg", "image/png", "image/webp"}));
        url.setOnClickListener(v -> urlDialog(activity, model::change));
        remove.setOnClickListener(
                v ->
                        new AlertDialog.Builder(activity)
                                .setMessage("Gỡ ảnh đại diện hiện tại?")
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton("Gỡ ảnh", (d, w) -> model.change(null))
                                .show());
        model.bind(profile.uid, profile.photoUrl);
        model.photo.observe(
                activity,
                state -> {
                    if (state == null) return;
                    gallery.setEnabled(!state.loading);
                    url.setEnabled(!state.loading);
                    remove.setEnabled(!state.loading && !model.current().isEmpty());
                    activity.findViewById(R.id.avatarProgress)
                            .setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    ((TextView) activity.findViewById(R.id.avatarFeedback))
                            .setText(state.error == null ? "" : state.error);
                    if (state.data != null) {
                        photo.setVisibility(state.data.isEmpty() ? View.GONE : View.VISIBLE);
                        if (state.data.isEmpty()) {
                            OnlineImages.get(activity).cancelRequest(photo);
                            photo.setImageDrawable(null);
                        } else OnlineImages.show(photo, Uri.parse(state.data));
                    }
                });
    }
}
