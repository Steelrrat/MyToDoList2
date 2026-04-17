package com.example.mytodolist2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {

    private ArrayList<Task> tasks;
    private OnTaskClickListener clickListener;
    private OnTaskCopyListener copyListener;
    private OnTaskDeleteListener deleteListener;
    private OnTaskEditListener editListener;
    private OnTaskDoneListener doneListener;

    public interface OnTaskClickListener { void onView(Task task); }
    public interface OnTaskCopyListener { void onCopy(Task task); }
    public interface OnTaskDeleteListener { void onDelete(int position); }
    public interface OnTaskEditListener { void onEdit(int position, Task task); }
    public interface OnTaskDoneListener { void onToggleDone(int position); }

    public TaskAdapter(ArrayList<Task> tasks, OnTaskClickListener clickListener,
                       OnTaskCopyListener copyListener, OnTaskDeleteListener deleteListener,
                       OnTaskEditListener editListener, OnTaskDoneListener doneListener) {
        this.tasks = tasks;
        this.clickListener = clickListener;
        this.copyListener = copyListener;
        this.deleteListener = deleteListener;
        this.editListener = editListener;
        this.doneListener = doneListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.textViewTask.setText(task.getText());
        
        String dateStr = task.getFormattedDate();
        if (!dateStr.equals("Без даты")) {
            holder.textViewDate.setText("📅 " + dateStr);
        } else {
            holder.textViewDate.setText(dateStr);
        }
        
        if (task.isDone()) {
            holder.textViewCheckbox.setText("☑");
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.done_background));
            holder.textViewTask.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.done_text));
        } else {
            holder.textViewCheckbox.setText("☐");
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.card_background));
            holder.textViewTask.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
        }
        
        holder.textViewCheckbox.setOnClickListener(v -> doneListener.onToggleDone(position));
        
        String reaction = task.getReaction();
        if (reaction != null) {
            switch (reaction) {
                case "like":
                    holder.textViewReaction.setText("❤️");
                    break;
                case "lightning":
                    holder.textViewReaction.setText("⚡");
                    break;
                case "cat":
                    holder.textViewReaction.setText("😺");
                    break;
                default:
                    holder.textViewReaction.setVisibility(View.GONE);
                    break;
            }
            holder.textViewReaction.setVisibility(View.VISIBLE);
        } else {
            holder.textViewReaction.setVisibility(View.GONE);
        }
        
        if (task.hasFile()) {
            holder.textViewFileIcon.setVisibility(View.VISIBLE);
        } else {
            holder.textViewFileIcon.setVisibility(View.GONE);
        }

        holder.cardView.setOnClickListener(v -> clickListener.onView(task));
        holder.cardView.setOnLongClickListener(v -> { editListener.onEdit(position, task); return true; });
    }

    @Override
    public int getItemCount() { return tasks.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView textViewCheckbox;
        TextView textViewTask;
        TextView textViewDate;
        TextView textViewReaction;
        TextView textViewFileIcon;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            textViewCheckbox = itemView.findViewById(R.id.textViewCheckbox);
            textViewTask = itemView.findViewById(R.id.textViewTask);
            textViewDate = itemView.findViewById(R.id.textViewDate);
            textViewReaction = itemView.findViewById(R.id.textViewReaction);
            textViewFileIcon = itemView.findViewById(R.id.textViewFileIcon);
        }
    }
}