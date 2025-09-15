package my.edu.utar.taskmate;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.app.NavUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyTasksActivity extends BaseActivity implements TaskAdapter.OnTaskClickListener {

    // Firestore constants
    private static final String COLLECTION_TASKS = "tasks";
    private static final String FIELD_POSTER_ID = "posterId";
    private static final String FIELD_ACCEPTED_BY = "acceptedBy";
    private static final String FIELD_STATUS = "status";
    private static final String STATUS_COMPLETED = "completed";

    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup tabButtons;
    private RecyclerView rvPosted, rvAccepted;
    private TextView textEmptyPosted, textEmptyAccepted;
    private ProgressBar progress;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipePosted, swipeAccepted;

    private final List<Task> postedData = new ArrayList<>();
    private final List<Task> acceptedData = new ArrayList<>();
    private TaskAdapter postedAdapter;
    private TaskAdapter acceptedAdapter;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private ListenerRegistration postedReg;
    private ListenerRegistration acceptedReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // UI references

        tabButtons = findViewById(R.id.tabButtons);
        rvPosted = findViewById(R.id.rvPosted);
        rvAccepted = findViewById(R.id.rvAccepted);
        textEmptyPosted = findViewById(R.id.textEmptyPosted);
        textEmptyAccepted = findViewById(R.id.textEmptyAccepted);
        progress = findViewById(R.id.progress);
        swipePosted = findViewById(R.id.swipePosted);
        swipeAccepted = findViewById(R.id.swipeAccepted);

        // Toolbar setup
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Tabs
        MaterialButtonToggleGroup tabButtons = findViewById(R.id.tabButtons);
        View btnPosted = findViewById(R.id.btnPosted);
        View btnAccepted = findViewById(R.id.btnAccepted);

// Default: show posted tasks
        tabButtons.check(R.id.btnPosted);
        swipePosted.setVisibility(View.VISIBLE);
        swipeAccepted.setVisibility(View.GONE);

        tabButtons.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (checkedId == R.id.btnPosted) {
                swipePosted.setVisibility(View.VISIBLE);
                swipeAccepted.setVisibility(View.GONE);
            } else if (checkedId == R.id.btnAccepted) {
                swipePosted.setVisibility(View.GONE);
                swipeAccepted.setVisibility(View.VISIBLE);
            }
        });



        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateBackToMain();
            }
        });

        // RecyclerViews
        rvPosted.setLayoutManager(new LinearLayoutManager(this));
        rvAccepted.setLayoutManager(new LinearLayoutManager(this));
        postedAdapter = new TaskAdapter(postedData, this);
        acceptedAdapter = new TaskAdapter(acceptedData, this);
        rvPosted.setAdapter(postedAdapter);
        rvAccepted.setAdapter(acceptedAdapter);

        swipePosted.setOnRefreshListener(this::refreshPosted);
        swipeAccepted.setOnRefreshListener(this::refreshAccepted);

    }

    @Override
    protected void onStart() {
        super.onStart();
        attachListeners();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachListeners();
    }

    private void attachListeners() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        showLoading(true);

        // Posted by me (exclude completed tasks)
        Query postedQ = db.collection(COLLECTION_TASKS)
                .whereEqualTo(FIELD_POSTER_ID, uid) // your tasks
                .orderBy("createdAt", Query.Direction.DESCENDING);
        postedReg = postedQ.addSnapshotListener(this::onPostedChanged);


        // Accepted by me
        Query acceptedQ = db.collection(COLLECTION_TASKS)
                .whereEqualTo(FIELD_ACCEPTED_BY, uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING);
        acceptedReg = acceptedQ.addSnapshotListener(this::onAcceptedChanged);
    }

    private void detachListeners() {
        if (postedReg != null) { postedReg.remove(); postedReg = null; }
        if (acceptedReg != null) { acceptedReg.remove(); acceptedReg = null; }
    }

    private void onPostedChanged(QuerySnapshot snapshots, FirebaseFirestoreException e) {
        if (e != null) { showLoading(false); return; }
        postedData.clear();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Task t = toTask(doc);
            if (t != null) postedData.add(t);
        }
        postedAdapter.notifyDataSetChanged();
        textEmptyPosted.setVisibility(postedData.isEmpty() ? View.VISIBLE : View.GONE);
        showLoading(false);
        swipePosted.setRefreshing(false);
    }

    private void onAcceptedChanged(QuerySnapshot snapshots, FirebaseFirestoreException e) {
        if (e != null) { showLoading(false); return; }
        acceptedData.clear();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Task t = toTask(doc);
            if (t != null) acceptedData.add(t);
        }
        acceptedAdapter.notifyDataSetChanged();
        textEmptyAccepted.setVisibility(acceptedData.isEmpty() ? View.VISIBLE : View.GONE);
        showLoading(false);
        swipeAccepted.setRefreshing(false);
    }

    private void refreshPosted() {
        swipePosted.setRefreshing(true);
        if (postedReg == null) attachListeners();
        swipePosted.setRefreshing(false);
    }

    private void refreshAccepted() {
        swipeAccepted.setRefreshing(true);
        if (acceptedReg == null) attachListeners();
        swipeAccepted.setRefreshing(false);
    }

    private void toggleTab(int position) {
        if (position == 0) {
            swipePosted.setVisibility(View.VISIBLE);
            swipeAccepted.setVisibility(View.GONE);
        } else {
            swipePosted.setVisibility(View.GONE);
            swipeAccepted.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // Handle task clicks
    @Override
    public void onTaskClick(@NonNull Task task) {
        boolean iAmPoster = auth.getCurrentUser() != null && task.posterId != null &&
                task.posterId.equals(auth.getCurrentUser().getUid());

        if (iAmPoster && !STATUS_COMPLETED.equals(task.status)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Mark task as completed?")
                    .setMessage("This will mark the task as completed and allow ratings.")
                    .setPositiveButton("Mark Completed", (dialog, which) -> markCompleted(task))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            // Show task details
            Intent intent = new Intent(this, TaskDetailActivity.class);
            intent.putExtra("taskId", task.id);
            startActivity(intent);
        }
    }

    @Override
    public void onAcceptTask(@NonNull Task task) {
        // This shouldn't happen in MyTasksActivity as you can't accept your own tasks
        Toast.makeText(this, "Cannot accept your own task", Toast.LENGTH_SHORT).show();
    }

    private void markCompleted(@NonNull Task task) {
        Map<String, Object> update = new HashMap<>();
        update.put(FIELD_STATUS, STATUS_COMPLETED);
        update.put("updatedAt", System.currentTimeMillis());
        db.collection(COLLECTION_TASKS).document(task.id).update(update);
    }

    // Convert Firestore doc -> Task object
    private Task toTask(@NonNull DocumentSnapshot d) {
        try {
            Task t = new Task();
            t.id = d.getId();
            t.title = d.getString("title");
            t.description = d.getString("description");
            t.posterId = d.getString(FIELD_POSTER_ID);
            t.acceptedBy = d.getString(FIELD_ACCEPTED_BY);
            t.status = d.getString(FIELD_STATUS);
            Double pay = d.getDouble("payment");
            t.payment = pay == null ? 0 : pay;
            t.locationName = d.getString("locationName");
            return t;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            navigateBackToMain();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        navigateBackToMain();
        return true;
    }

    // Simple Task model
    public static class Task {
        public String id;
        public String title;
        public String description;
        public String posterId;
        public String acceptedBy;
        public String status;
        public double payment;
        public String locationName;
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_my_task;
    }

    @Override
    protected int getBottomNavMenuId() {
        return R.id.nav_tasks;
    }
}