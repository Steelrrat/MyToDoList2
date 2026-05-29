package com.capitalfoto.voicediary;

import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class EmojiRecyclerAdapter extends RecyclerView.Adapter<EmojiRecyclerAdapter.EmojiViewHolder> {

    private List<String> emojis;
    private OnEmojiClickListener listener;

    public interface OnEmojiClickListener {
        void onEmojiClick(String emoji);
    }

    public EmojiRecyclerAdapter(List<String> emojis, OnEmojiClickListener listener) {
        this.emojis = emojis;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emoji, parent, false);

        int itemSize = (int) (50 * parent.getContext().getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(itemSize, itemSize);
        view.setLayoutParams(lp);

        return new EmojiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
        String emoji = emojis.get(position);
        holder.emojiText.setText(emoji);

        // ИСПРАВЛЕНО: используем ContextCompat.getColor() для получения цвета
        int transparentColor = ContextCompat.getColor(holder.itemView.getContext(), android.R.color.transparent);
        holder.cardView.setCardBackgroundColor(transparentColor);

        // Используем атрибут темы вместо фиксированного цвета
        int textColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary);
        holder.emojiText.setTextColor(textColor);

        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEmojiClick(emoji);
            }
        });
    }

    @Override
    public int getItemCount() {
        return emojis != null ? emojis.size() : 0;
    }

    static class EmojiViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView emojiText;

        EmojiViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.emojiCardView);
            emojiText = itemView.findViewById(R.id.emojiText);
        }
    }
}