package my.edu.utar.taskmate;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.widget.Toast;

public class MainActivity extends BaseActivity implements TaskAdapter.OnTaskClickListener {

    private RecyclerView recyclerView;
    private EditText etSearch;
    private final List<MyTasksActivity.Task> fullTaskList = new ArrayList<>(); // source of truth
    private final List<MyTasksActivity.Task> taskList = new ArrayList<>();     // shown in adapter
    private TaskAdapter taskAdapter;
    private TextView tvUsername;
    private ImageView imgAvatarTopBar;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    // tiny TextWatcher adapter (so you only override what you need)
    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void afterTextChanged(android.text.Editable s) { }
    }



    private ListenerRegistration taskListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tvUsername = findViewById(R.id.tvUsername);
        imgAvatarTopBar = findViewById(R.id.imgAvatar);

// Get current user UID
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

// Load welcome info
        loadWelcomeInfo(uid);


        // Toolbar
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);

        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut();
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });



        imgAvatarTopBar = findViewById(R.id.imgAvatar);
        if (imgAvatarTopBar != null) {
            imgAvatarTopBar.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        }

        recyclerView = findViewById(R.id.recyclerViewTasks);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        taskAdapter = new TaskAdapter(taskList, this);
        recyclerView.setAdapter(taskAdapter);

        etSearch = findViewById(R.id.etSearch);


// Live filter as the user types
        etSearch.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }
        });

// “Search” action on keyboard: scroll to/open first match
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = v.getText() == null ? "" : v.getText().toString();
                int idx = indexOfFirstMatch(q);
                if (idx >= 0) {
                    recyclerView.scrollToPosition(idx);
                    // Optionally open detail immediately:
                    MyTasksActivity.Task t = taskList.get(idx);
                    Intent i = new Intent(this, TaskDetailActivity.class);
                    i.putExtra("taskId", t.id);
                    startActivity(i);
                } else {
                    Toast.makeText(this, "No results for \"" + q + "\"", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        attachTaskListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (taskListener != null) {
            taskListener.remove();
            taskListener = null;
        }
    }

    private void loadWelcomeInfo(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) {
                            tvUsername.setText("Welcome, " + name + "!");
                        }

                        String avatarUrl = doc.getString("avatarUrl");
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(this).load(avatarUrl).into(imgAvatarTopBar);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load user info", Toast.LENGTH_SHORT).show()
                );
    }


    private void attachTaskListener() {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        taskListener = db.collection("tasks")
                .whereEqualTo("status", "open")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    fullTaskList.clear();                              // ← change
                    for (DocumentSnapshot doc : snapshots) {
                        MyTasksActivity.Task t = new MyTasksActivity.Task();
                        t.id = doc.getId();
                        t.title = doc.getString("title");
                        t.description = doc.getString("description");
                        t.posterId = doc.getString("posterId");
                        t.acceptedBy = doc.getString("acceptedBy");
                        t.status = doc.getString("status");
                        Double pay = doc.getDouble("payment");
                        t.payment = pay != null ? pay : 0;
                        t.locationName = doc.getString("locationName");

                        if (!currentUid.equals(t.posterId)) {
                            fullTaskList.add(t);                       // ← change
                        }
                    }

                    // Re-apply whatever is currently typed in the search box
                    String q = etSearch != null && etSearch.getText() != null
                            ? etSearch.getText().toString() : "";
                    applyFilter(q);                                    // ← add
                });
    }


    private void applyFilter(@NonNull String query) {
        String q = query.trim().toLowerCase();

        taskList.clear();
        if (q.isEmpty()) {
            // If no search text, show everything
            taskList.addAll(fullTaskList);
        } else {
            // Otherwise, add only matching tasks
            for (MyTasksActivity.Task t : fullTaskList) {
                String title = t.title == null ? "" : t.title.toLowerCase();
                String desc  = t.description == null ? "" : t.description.toLowerCase();
                String loc   = t.locationName == null ? "" : t.locationName.toLowerCase();

                if (title.contains(q) || desc.contains(q) || loc.contains(q)) {
                    taskList.add(t);
                }
            }
        }

        taskAdapter.notifyDataSetChanged();
    }

    private int indexOfFirstMatch(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return -1;

        for (int i = 0; i < taskList.size(); i++) {
            MyTasksActivity.Task t = taskList.get(i);
            String title = t.title == null ? "" : t.title.toLowerCase();
            String desc  = t.description == null ? "" : t.description.toLowerCase();
            String loc   = t.locationName == null ? "" : t.locationName.toLowerCase();

            if (title.contains(q) || desc.contains(q) || loc.contains(q)) {
                return i;
            }
        }
        return -1; // no match
    }



    @Override
    public void onTaskClick(@NonNull MyTasksActivity.Task task) {
        Intent i = new Intent(this, TaskDetailActivity.class);
        i.putExtra("taskId", task.id);
        startActivity(i);
    }

    @Override
    public void onAcceptTask(@NonNull MyTasksActivity.Task task) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Map<String, Object> update = new HashMap<>();
        update.put("acceptedBy", currentUid);
        update.put("status", "in_progress");  // matches Firestore rule
        update.put("updatedAt", System.currentTimeMillis());

        db.collection("tasks").document(task.id)
                .update(update)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Task accepted!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to accept task", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_main;
    }

    @Override
    protected int getBottomNavMenuId() {
        return R.id.nav_home;
    }
}