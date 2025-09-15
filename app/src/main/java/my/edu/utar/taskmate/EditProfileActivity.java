package my.edu.utar.taskmate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class EditProfileActivity extends BaseActivity {

    private EditText etName, etBio;
    private Button btnSave;

    private FirebaseFirestore db;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_edit_profile;
    }

    @Override
    protected int getBottomNavMenuId() {
        return 0; // no bottom nav highlight
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseFirestore.getInstance();

        etName = findViewById(R.id.etName);
        etBio = findViewById(R.id.etBio);
        btnSave = findViewById(R.id.btnSave);

        // Load current values passed from ProfileActivity
        String currentName = getIntent().getStringExtra("name");
        String currentBio = getIntent().getStringExtra("bio");
        etName.setText(currentName != null ? currentName : "");
        etBio.setText(currentBio != null ? currentBio : "");

        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String bio = etBio.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Name required");
            etName.requestFocus();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Update Firestore
        db.collection("users").document(uid)
                .update("name", name, "bio", bio)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EditProfileActivity.this, "Profile updated!", Toast.LENGTH_SHORT).show();

                    // Return updated values to ProfileActivity
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("updatedName", name);
                    resultIntent.putExtra("updatedBio", bio);
                    setResult(RESULT_OK, resultIntent);

                    // Close activity and return
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(EditProfileActivity.this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
