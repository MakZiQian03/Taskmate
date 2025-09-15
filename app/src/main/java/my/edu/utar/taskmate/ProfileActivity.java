package my.edu.utar.taskmate;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;

public class ProfileActivity extends BaseActivity {

    private static final int PICK_IMAGE = 100;
    private static final int TAKE_PHOTO = 101;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;

    private ImageView imgAvatar;
    private TextView tvName, tvBio, tvRating;
    private Button btnEdit, btnHistory, btnChangeAvatar;
    private Switch switchDarkMode;
    private RecyclerView recyclerHistory;
    private LinearLayout layoutHistory;

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private Uri imageUri;

    private ArrayList<String> historyList;
    private HistoryAdapter adapter;

    private String currentName = "";
    private String currentBio = "";

    // ✅ Modern Activity Result API launcher
    private ActivityResultLauncher<Intent> editProfileLauncher;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_profile;
    }

    @Override
    protected int getBottomNavMenuId() {
        return R.id.nav_profile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Bind views
        imgAvatar = findViewById(R.id.imgAvatar);
        tvName = findViewById(R.id.tvName);
        tvBio = findViewById(R.id.tvBio);
        tvRating = findViewById(R.id.tvRating);
        btnEdit = findViewById(R.id.btnEdit);
        btnHistory = findViewById(R.id.btnHistory);
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar);
        switchDarkMode = findViewById(R.id.switchDarkMode);
        recyclerHistory = findViewById(R.id.recyclerHistory);
        layoutHistory = findViewById(R.id.layoutHistory);

        // RecyclerView setup
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        historyList = new ArrayList<>();
        adapter = new HistoryAdapter(historyList);
        recyclerHistory.setAdapter(adapter);

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Load profile & rating
        loadProfile(uid);
        loadRating(uid);

        // Load history
        btnHistory.setOnClickListener(v -> loadHistory(uid));

        // Dark mode toggle
        boolean dark = prefs.getBoolean("darkMode", false);
        switchDarkMode.setChecked(dark);
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("darkMode", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
            recreate(); // Apply theme immediately
        });

        // ✅ Setup the modern activity result launcher
        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        currentName = data.getStringExtra("updatedName");
                        currentBio = data.getStringExtra("updatedBio");
                        tvName.setText(currentName);
                        tvBio.setText(currentBio);
                    }
                }
        );

        // Edit profile
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            intent.putExtra("name", currentName);
            intent.putExtra("bio", currentBio);
            editProfileLauncher.launch(intent);
        });

        // Change avatar
        btnChangeAvatar.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                showImagePickerDialog(uid);
            }
        });
    }

    private void loadProfile(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentName = doc.getString("name");
                        currentBio = doc.getString("bio");
                        tvName.setText(currentName != null ? currentName : "No name");
                        tvBio.setText(currentBio != null ? currentBio : "No bio");

                        String avatarUrl = doc.getString("avatarUrl");
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(this).load(avatarUrl).into(imgAvatar);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show());
    }

    private void loadRating(String uid) {
        db.collection("users").document(uid).collection("ratings").get()
                .addOnSuccessListener(qs -> {
                    double total = 0;
                    for (DocumentSnapshot d : qs) {
                        Double stars = d.getDouble("stars");
                        if (stars != null) total += stars;
                    }
                    double avg = qs.size() > 0 ? total / qs.size() : 0;
                    tvRating.setText("⭐ " + String.format("%.1f", avg) + " (" + qs.size() + " reviews)");
                });
    }

    private void loadHistory(String uid) {
        db.collection("tasks")
                .whereEqualTo("acceptedBy", uid)
                .get()
                .addOnSuccessListener((QuerySnapshot qs) -> {
                    historyList.clear();
                    for (DocumentSnapshot doc : qs) {
                        String title = doc.getString("title");
                        String status = doc.getString("status");
                        historyList.add((title != null ? title : "Untitled") +
                                " - " + (status != null ? status : "Unknown"));
                    }
                    adapter.notifyDataSetChanged();

                    if (!historyList.isEmpty()) {
                        layoutHistory.setVisibility(LinearLayout.VISIBLE);
                    } else {
                        layoutHistory.setVisibility(LinearLayout.GONE);
                        Toast.makeText(this, "No history available", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show());
    }

    private boolean checkAndRequestPermissions() {
        boolean cameraGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean storageGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!cameraGranted) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return false;
        }

        if (!storageGranted) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            return false;
        }

        return true;
    }

    private void showImagePickerDialog(String uid) {
        String[] options = {"Take Photo", "Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Change Profile Picture")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else openGallery();
                })
                .show();
    }

    private void openGallery() {
        Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(gallery, PICK_IMAGE);
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(cameraIntent, TAKE_PHOTO);
    }

    private void uploadToFirebase(String uid, Uri uri) {
        StorageReference fileRef = storage.getReference().child("avatars").child(uid + ".jpg");
        fileRef.putFile(uri)
                .addOnSuccessListener(taskSnapshot ->
                        fileRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            db.collection("users").document(uid)
                                    .update("avatarUrl", downloadUri.toString());
                            Toast.makeText(ProfileActivity.this, "Profile picture updated!", Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e ->
                        Toast.makeText(ProfileActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply dark mode to ensure consistency after navigating back
        boolean dark = prefs.getBoolean("darkMode", false);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
}
