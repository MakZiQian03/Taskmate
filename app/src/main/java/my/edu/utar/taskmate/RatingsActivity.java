package my.edu.utar.taskmate;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RatingsActivity extends BaseActivity {

    private RatingBar ratingBar;
    private EditText etReview;
    private Button btnSubmit;
    private TextView tvAverageRating;
    private RecyclerView recyclerReviews;

    private FirebaseFirestore db;
    private RatingAdapter adapter;
    private ArrayList<RatingModel> reviewList;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_ratings;
    }

    @Override
    protected int getBottomNavMenuId() {
        return R.id.nav_tasks; // accessed after My Tasks
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ratingBar = findViewById(R.id.ratingBar);
        etReview = findViewById(R.id.etReview);
        btnSubmit = findViewById(R.id.btnSubmitRating);
        tvAverageRating = findViewById(R.id.tvAverageRating);
        recyclerReviews = findViewById(R.id.recyclerReviews);

        db = FirebaseFirestore.getInstance();

        recyclerReviews.setLayoutManager(new LinearLayoutManager(this));
        reviewList = new ArrayList<>();
        adapter = new RatingAdapter(reviewList);
        recyclerReviews.setAdapter(adapter);

        String userId = getIntent().getStringExtra("userId");
        if (userId == null) {
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadRatings(userId);

        btnSubmit.setOnClickListener(v -> submitRating(userId));
    }

    private void submitRating(String userId) {
        float stars = ratingBar.getRating();
        String review = etReview.getText().toString().trim();
        String fromUser = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (userId.equals(fromUser)) {
            Toast.makeText(this, "You cannot rate yourself", Toast.LENGTH_SHORT).show();
            return;
        }

        if (stars <= 0) {
            Toast.makeText(this, "Please give a star rating", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);

        long timestamp = System.currentTimeMillis();

        Map<String, Object> rating = new HashMap<>();
        rating.put("stars", stars);
        rating.put("review", review);
        rating.put("fromUser", fromUser);
        rating.put("timestamp", timestamp);

        db.collection("users").document(userId).collection("ratings")
                .add(rating)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Rating submitted!", Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                    ratingBar.setRating(0);
                    etReview.setText("");
                    loadRatings(userId); // reload list
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                });
    }

    private void loadRatings(String userId) {
        db.collection("users").document(userId).collection("ratings")
                .get()
                .addOnSuccessListener(snapshots -> {
                    reviewList.clear();
                    double total = 0;
                    for (DocumentSnapshot doc : snapshots) {
                        Float stars = doc.getDouble("stars") != null ? doc.getDouble("stars").floatValue() : 0;
                        String review = doc.getString("review");
                        String fromUser = doc.getString("fromUser");
                        Long timestamp = doc.getLong("timestamp");

                        if (timestamp == null) {
                            timestamp = System.currentTimeMillis(); // fallback
                        }

                        reviewList.add(new RatingModel(stars, review, fromUser, timestamp));
                        total += stars;
                    }
                    adapter.notifyDataSetChanged();

                    double avg = snapshots.size() > 0 ? total / snapshots.size() : 0;
                    tvAverageRating.setText("⭐ " + String.format("%.1f", avg) + " (" + snapshots.size() + " reviews)");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load ratings", Toast.LENGTH_SHORT).show());
    }
}
