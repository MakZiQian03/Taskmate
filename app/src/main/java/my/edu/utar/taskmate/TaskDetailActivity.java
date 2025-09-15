package my.edu.utar.taskmate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class TaskDetailActivity extends BaseActivity {

    private TextView tvTitle, tvDescription, tvPayment, tvLocation, tvTime;
    private Button btnAccept, btnComplete;

    private FirebaseFirestore db;
    private DocumentReference taskRef;
    private String taskId;
    private String currentUserId;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_task_detail;
    }

    @Override
    protected int getBottomNavMenuId() {
        return R.id.nav_home;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bind views
        tvTitle = findViewById(R.id.tvTitle);
        tvDescription = findViewById(R.id.tvDescription);
        tvPayment = findViewById(R.id.tvPayment);
        tvLocation = findViewById(R.id.tvLocation);
        tvTime = findViewById(R.id.tvTime);
        btnAccept = findViewById(R.id.btnAccept);
        btnComplete = findViewById(R.id.btnComplete);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        taskId = getIntent().getStringExtra("taskId");

        if (taskId == null || taskId.isEmpty()) {
            Toast.makeText(this, "No task ID provided.", Toast.LENGTH_SHORT).show();
            return;
        }

        taskRef = db.collection("tasks").document(taskId);

        loadTaskDetails();

        btnAccept.setOnClickListener(v -> acceptTask());
        btnComplete.setOnClickListener(v -> completeTask());
    }

    private void loadTaskDetails() {
        taskRef.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                tvTitle.setText(doc.getString("title"));
                tvDescription.setText(doc.getString("description"));
                Object payment = doc.get("payment");
                tvPayment.setText(payment != null ? "RM " + payment : "-");
                tvLocation.setText(String.valueOf(doc.get("location")));
                Object ts = doc.get("timestamp");
                tvTime.setText(ts != null ? String.valueOf(ts) : "-");

                // Show/hide buttons based on user role
                String posterId = doc.getString("posterId");
                if (posterId != null && posterId.equals(currentUserId)) {
                    btnComplete.setVisibility(Button.VISIBLE);
                    btnAccept.setVisibility(Button.GONE);
                } else {
                    btnAccept.setVisibility(Button.VISIBLE);
                    btnComplete.setVisibility(Button.GONE);
                }
            }
        }).addOnFailureListener(e -> Toast.makeText(this, "Failed to load task", Toast.LENGTH_SHORT).show());
    }

    private void acceptTask() {
        if (currentUserId == null) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
            return;
        }

        taskRef.update("acceptedBy", currentUserId, "status", "Ongoing")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Task accepted!", Toast.LENGTH_SHORT).show();

                    // ✅ Return to previous page (MyTasks or Home)
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void completeTask() {
        Intent intent = new Intent(this, RatingsActivity.class);
        intent.putExtra("taskId", taskId);
        startActivity(intent);
        finish(); // Close TaskDetailActivity
    }
}
