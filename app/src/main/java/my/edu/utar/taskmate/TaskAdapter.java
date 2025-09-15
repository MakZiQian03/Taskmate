package my.edu.utar.taskmate;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    public interface OnTaskClickListener {
        void onTaskClick(@NonNull MyTasksActivity.Task task);
        void onAcceptTask(@NonNull MyTasksActivity.Task task); // New method for accepting tasks
    }

    private final List<MyTasksActivity.Task> taskList;
    private final OnTaskClickListener listener;

    public TaskAdapter(List<MyTasksActivity.Task> taskList, OnTaskClickListener listener) {
        this.taskList = taskList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        MyTasksActivity.Task task = taskList.get(position);
        holder.bind(task, listener);
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc, tvPayment, tvLocation;
        Button btnAccept;

        TaskViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvDesc = itemView.findViewById(R.id.tvTaskDesc);
            tvPayment = itemView.findViewById(R.id.tvTaskPayment);
            tvLocation = itemView.findViewById(R.id.tvTaskLocation);
            btnAccept = itemView.findViewById(R.id.btnAccept);
        }

        void bind(MyTasksActivity.Task task, OnTaskClickListener listener) {
            tvTitle.setText(task.title);
            tvDesc.setText(task.description);
            tvPayment.setText("RM " + task.payment);

            if (task.locationName != null && !task.locationName.isEmpty()) {
                tvLocation.setText("📍 " + task.locationName);
            } else {
                tvLocation.setText("📍 No location set");
            }

            // Show/hide accept button based on task status and user
            String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            boolean canAccept = "open".equals(task.status) &&
                    !task.posterId.equals(currentUid);

            btnAccept.setVisibility(canAccept ? View.VISIBLE : View.GONE);

            btnAccept.setOnClickListener(v -> {
                if (canAccept && listener != null) {
                    listener.onAcceptTask(task);
                }
            });

            // ✅ Grey out completed tasks
            if ("completed".equalsIgnoreCase(task.status)) {
                tvTitle.setTextColor(itemView.getResources().getColor(android.R.color.darker_gray));
                tvDesc.setTextColor(itemView.getResources().getColor(android.R.color.darker_gray));
                tvPayment.setTextColor(itemView.getResources().getColor(android.R.color.darker_gray));
                tvLocation.setTextColor(itemView.getResources().getColor(android.R.color.darker_gray));
            } else {
                tvTitle.setTextColor(itemView.getResources().getColor(android.R.color.black));
                tvDesc.setTextColor(itemView.getResources().getColor(android.R.color.darker_gray));
                tvPayment.setTextColor(itemView.getResources().getColor(android.R.color.holo_green_dark));
                tvLocation.setTextColor(itemView.getResources().getColor(android.R.color.darker_gray));
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTaskClick(task);
            });
        }
    }
}