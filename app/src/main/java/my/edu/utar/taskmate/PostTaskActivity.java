package my.edu.utar.taskmate;

import android.app.Activity;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class PostTaskActivity extends BaseActivity {

    // Use TextInputEditText consistently (matches your XML + TextInputLayout)
    private TextInputEditText etTitle, etDesc, etPayment;
    private TextView tvLocation, tvProgressLabel;
    private LinearProgressIndicator postProgress;
    private MaterialButton btnPost;     // Use MaterialButton if XML uses <com.google.android.material.button.MaterialButton>
    private MaterialButton btnPickLocation;

    private Double selectedLat, selectedLng;
    private String selectedAddress;
    private FirebaseFirestore db;

    private boolean locationSelected = false;
    private int currentProgress = 0;

    private final ActivityResultLauncher<Intent> mapLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null) {
                                selectedLat = data.getDoubleExtra("lat", 0.0);
                                selectedLng = data.getDoubleExtra("lng", 0.0);

                                selectedAddress = getAddressFromLatLng(selectedLat, selectedLng);
                                if (selectedAddress != null && !selectedAddress.isEmpty()) {
                                    tvLocation.setText(selectedAddress);
                                } else {
                                    tvLocation.setText("Lat: " + selectedLat + ", Lng: " + selectedLng);
                                }
                                // Mark location as selected and refresh progress
                                locationSelected = true;
                                updateProgress();
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // IMPORTANT: BaseActivity likely calls setContentView(getLayoutResourceId()).
        // Since you override getLayoutResourceId() below, do NOT call setContentView() here again.

        db = FirebaseFirestore.getInstance();

        // Bind views to FIELDS (no shadowing!)
        etTitle = findViewById(R.id.etTitle);
        etDesc = findViewById(R.id.etDescription);
        etPayment = findViewById(R.id.etPayment);
        tvLocation = findViewById(R.id.tvLocation);
        tvProgressLabel = findViewById(R.id.tvProgressLabel);
        postProgress = findViewById(R.id.postProgress);  // Use LinearProgressIndicator if XML uses <com.google.android.material.progressindicator.LinearProgressIndicator>
        btnPickLocation = findViewById(R.id.btnPickLocation);
        btnPost = findViewById(R.id.btnPost);

        // Text watchers update progress as the user types
        TextWatcher watcher = new SimpleTextWatcher(this::updateProgress);
        etTitle.addTextChangedListener(watcher);
        etDesc.addTextChangedListener(watcher);
        etPayment.addTextChangedListener(watcher);

        btnPickLocation.setOnClickListener(v -> {
            Intent intent = new Intent(PostTaskActivity.this, MapPickerActivity.class);
            mapLauncher.launch(intent);
        });

        btnPost.setOnClickListener(v -> postTask());

        // Initial state
        updateProgress();
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_post_task;
    }

    @Override
    protected int getBottomNavMenuId() {
        return R.id.nav_post;
    }

    private void updateProgress() {
        if (postProgress == null || tvProgressLabel == null || btnPost == null) return;

        int target = 0;
        if (!isEmpty(etTitle))       target += 25; // Title
        if (!isEmpty(etDesc))        target += 25; // Description
        if (isValidMoney(etPayment)) target += 25; // Payment valid (>0)
        if (locationSelected)        target += 25; // Location

        animateProgressTo(target);

        String step = (target < 25) ? "Title"
                : (target < 50) ? "Description"
                : (target < 75) ? "Payment"
                : "Location";
        tvProgressLabel.setText(target + "% complete • " + step);

        boolean ready = (target == 100);
        btnPost.setEnabled(ready);
        btnPost.setAlpha(ready ? 1f : 0.6f);
    }

    private void animateProgressTo(int targetPercent) {
        targetPercent = Math.max(0, Math.min(100, targetPercent));
        if (targetPercent == currentProgress) return;
        postProgress.setProgressCompat(targetPercent, /*animated=*/true);
        currentProgress = targetPercent;
    }

    private boolean isEmpty(TextInputEditText editText) {
        CharSequence cs = editText.getText();
        return cs == null || cs.toString().trim().isEmpty();
    }

    private boolean isValidMoney(TextInputEditText editText) {
        CharSequence cs = editText.getText();
        if (cs == null) return false;
        try {
            double v = Double.parseDouble(cs.toString().trim());
            return v > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void postTask() {
        String title = safe(etTitle);
        String desc = safe(etDesc);
        String payStr = safe(etPayment);

        if (title.isEmpty() || desc.isEmpty() || payStr.isEmpty()
                || selectedLat == null || selectedLng == null) {
            Toast.makeText(this, "Fill all fields and pick location", Toast.LENGTH_SHORT).show();
            return;
        }

        double payment;
        try {
            payment = Double.parseDouble(payStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid payment amount", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        Map<String, Object> task = new HashMap<>();
        task.put("title", title);
        task.put("description", desc);
        task.put("payment", payment);
        task.put("posterId", uid);
        task.put("status", "open");
        task.put("lat", selectedLat);
        task.put("lng", selectedLng);
        task.put("locationName", selectedAddress != null ? selectedAddress : "");
        task.put("createdAt", System.currentTimeMillis());
        task.put("updatedAt", System.currentTimeMillis());

        db.collection("tasks")
                .add(task)
                .addOnSuccessListener(doc -> {
                    Toast.makeText(this, "Task posted!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, MyTasksActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private String safe(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    // Simple watcher helper
    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChange;
        SimpleTextWatcher(Runnable onChange) { this.onChange = onChange; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onChange.run(); }
    }

    // Geocoding
    private String getAddressFromLatLng(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
