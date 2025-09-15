package my.edu.utar.taskmate;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class RatingAdapter extends RecyclerView.Adapter<RatingAdapter.RatingViewHolder> {

    private ArrayList<RatingModel> ratings;

    public RatingAdapter(ArrayList<RatingModel> ratings) {
        this.ratings = ratings;
    }

    @NonNull
    @Override
    public RatingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rating, parent, false);
        return new RatingViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RatingViewHolder holder, int position) {
        RatingModel model = ratings.get(position);

        // Show stars and review text
        holder.ratingBar.setRating(model.getStars());
        holder.tvReviewText.setText(model.getReview());

        // Format timestamp
        if (model.getTimestamp() > 0) {
            String formattedDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(new Date(model.getTimestamp()));
            holder.tvTimestamp.setText(formattedDate);
        } else {
            holder.tvTimestamp.setText("");
        }

        // Fetch reviewer name from Firestore using UID
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(model.getFromUser())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        holder.tvReviewerName.setText(doc.getString("name"));
                    } else {
                        holder.tvReviewerName.setText("Unknown User");
                    }
                })
                .addOnFailureListener(e ->
                        holder.tvReviewerName.setText("Unknown User"));
    }

    @Override
    public int getItemCount() {
        return ratings.size();
    }

    static class RatingViewHolder extends RecyclerView.ViewHolder {
        RatingBar ratingBar;
        TextView tvReviewText, tvReviewerName, tvTimestamp;

        RatingViewHolder(View itemView) {
            super(itemView);
            ratingBar = itemView.findViewById(R.id.itemRatingBar);
            tvReviewText = itemView.findViewById(R.id.tvReviewText);
            tvReviewerName = itemView.findViewById(R.id.tvReviewerName);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
    }
}
