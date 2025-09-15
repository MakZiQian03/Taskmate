package my.edu.utar.taskmate;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtName, edtEmail, edtPassword, edtConfirm;
    private Button btnRegister;
    private ProgressBar progress;
    private TextView tvGoLogin;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        edtName     = findViewById(R.id.edtName);
        edtEmail    = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirm  = findViewById(R.id.edtConfirm);
        btnRegister = findViewById(R.id.btnRegister);
        progress    = findViewById(R.id.progress);
        tvGoLogin   = findViewById(R.id.tvGoLogin);

        btnRegister.setOnClickListener(v -> attemptRegister());
        tvGoLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        String name  = text(edtName);
        String email = text(edtEmail);
        String pass  = text(edtPassword);
        String conf  = text(edtConfirm);

        if (TextUtils.isEmpty(name))  { edtName.setError("Enter name"); edtName.requestFocus(); return; }
        if (TextUtils.isEmpty(email)) { edtEmail.setError("Enter email"); edtEmail.requestFocus(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Invalid email"); edtEmail.requestFocus(); return;
        }
        if (TextUtils.isEmpty(pass))  { edtPassword.setError("Enter password"); edtPassword.requestFocus(); return; }
        if (pass.length() < 6)       { edtPassword.setError("Min 6 characters"); edtPassword.requestFocus(); return; }
        if (!pass.equals(conf))      { edtConfirm.setError("Passwords do not match"); edtConfirm.requestFocus(); return; }

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        Toast.makeText(this, "Registered but user is null", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Set display name (optional)
                    user.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build());

                    // Send verification email
                    user.sendEmailVerification()
                            .addOnCompleteListener(task -> {
                                setLoading(false);
                                if (task.isSuccessful()) {
                                    Toast.makeText(this,
                                            "Verification email sent to " + email + ". Please verify, then login.",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this,
                                            "Failed to send verification email: " +
                                                    (task.getException() != null ? task.getException().getMessage() : ""),
                                            Toast.LENGTH_LONG).show();
                                }

                                // Save profile info to Firestore (non-blocking)
                                Map<String, Object> profile = new HashMap<>();
                                profile.put("uid", user.getUid());
                                profile.put("name", name);
                                profile.put("email", email);
                                profile.put("createdAt", System.currentTimeMillis());
                                db.collection("users").document(user.getUid())
                                        .set(profile)
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this, "Profile save failed: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show());

                                // Go back to login page
                                Intent intent = new Intent(this, LoginActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Register failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
        edtName.setEnabled(!loading);
        edtEmail.setEnabled(!loading);
        edtPassword.setEnabled(!loading);
        edtConfirm.setEnabled(!loading);
    }

    private String text(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
