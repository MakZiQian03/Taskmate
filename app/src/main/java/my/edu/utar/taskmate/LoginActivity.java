package my.edu.utar.taskmate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText edtEmail, edtPassword;
    private Button btnLogin;
    private CheckBox cbRemember;
    private FirebaseAuth auth;

    // SharedPreferences keys (Remember me)
    private static final String PREFS       = "taskmate_prefs";
    private static final String KEY_REMEMBER = "remember";
    private static final String KEY_EMAIL    = "remember_email";
    private static final String KEY_PASSWORD = "remember_password";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // EdgeToEdge template
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Firebase Auth instance
        auth = FirebaseAuth.getInstance();

        // UI components
        edtEmail    = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin    = findViewById(R.id.btnLogin);
        cbRemember  = findViewById(R.id.cbRemember); // make sure this exists in XML

        // Ensure password starts HIDDEN so the eye toggle behaves correctly
        edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edtPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
        edtPassword.setSelection(edtPassword.getText().length());

        // Restore "Remember me" state (if checkbox is present)
        if (cbRemember != null) {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean remember = sp.getBoolean(KEY_REMEMBER, false);
            cbRemember.setChecked(remember);
            if (remember) {
                String savedEmail = sp.getString(KEY_EMAIL, "");
                if (!savedEmail.isEmpty()) edtEmail.setText(savedEmail);
                String savedPw = sp.getString(KEY_PASSWORD, "");
                if (!savedPw.isEmpty()) edtPassword.setText(savedPw);

            }
        }

        // Login button click
        btnLogin.setOnClickListener(v -> loginUser());

        // Register
        TextView tvGoRegister = findViewById(R.id.tvGoRegister);
        tvGoRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // Forgot Password
        TextView tvForgot = findViewById(R.id.tvForgotPassword);
        tvForgot.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
    }

    private void loginUser() {
        String email = edtEmail.getText().toString().trim();
        String pass  = edtPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Enter email");
            return;
        }
        if (TextUtils.isEmpty(pass)) {
            edtPassword.setError("Enter password");
            return;
        }

        auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user == null) {
                            Toast.makeText(this, "Login succeeded but user is null", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Save or clear "Remember me" preference on successful auth
                        if (cbRemember != null) {
                            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                            if (cbRemember.isChecked()) {
                                sp.edit()
                                        .putBoolean(KEY_REMEMBER, true)
                                        .putString(KEY_EMAIL, email)
                                        .putString(KEY_PASSWORD, pass)
                                        .apply();
                            } else {
                                sp.edit()
                                        .putBoolean(KEY_REMEMBER, false)
                                        .remove(KEY_EMAIL)
                                        .remove(KEY_PASSWORD)
                                        .apply();
                            }
                        }

                        if (user.isEmailVerified()) {
                            // Verified → go to Home
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // Not verified → dialog (resend link)
                            showEmailNotVerifiedDialog(user);
                        }
                    } else {
                        // Failure
                        Toast.makeText(LoginActivity.this,
                                "Login failed: " + (task.getException() != null ? task.getException().getMessage() : ""),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showEmailNotVerifiedDialog(FirebaseUser user) {
        new AlertDialog.Builder(this)
                .setTitle("Email not verified")
                .setMessage("Please verify your email before logging in.")
                .setPositiveButton("Resend verification", (dialog, which) -> {
                    user.sendEmailVerification()
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(this,
                                            "Verification email resent to " + user.getEmail(),
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this,
                                            "Resend failed: " +
                                                    (task.getException() != null ? task.getException().getMessage() : ""),
                                            Toast.LENGTH_LONG).show();
                                }
                                FirebaseAuth.getInstance().signOut();
                            });
                })
                .setNegativeButton("OK", (dialog, which) -> FirebaseAuth.getInstance().signOut())
                .setCancelable(false)
                .show();
    }
}
