package app.pachli.interfaces;

import androidx.annotation.NonNull;

public interface PermissionRequester {
    void onRequestPermissionsResult(@NonNull String[] permissions, @NonNull int[] grantResults);
}
