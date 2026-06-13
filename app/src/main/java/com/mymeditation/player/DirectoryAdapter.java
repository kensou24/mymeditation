package com.mymeditation.player;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.ViewHolder> {
    private List<DirectoryItem> directoryList;
    private OnDirectoryClickListener listener;
    private ThemeManager.ThemeColors themeColors;
    private int lastAnimatedPosition = -1;

    public interface OnDirectoryClickListener {
        void onDirectoryClick(DirectoryItem directory);
    }

    public DirectoryAdapter(List<DirectoryItem> directoryList, OnDirectoryClickListener listener) {
        this.directoryList = directoryList;
        this.listener = listener;
    }

    public void setThemeColors(ThemeManager.ThemeColors colors) {
        this.themeColors = colors;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_directory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DirectoryItem item = directoryList.get(position);
        holder.textViewDirectoryName.setText(item.getName());
        holder.textViewFileCount.setText(item.getFileCount() + " 个MP3文件");

        // Apply theme colors
        if (themeColors != null) {
            // Card background
            ((MaterialCardView) holder.itemView).setCardBackgroundColor(themeColors.surface);

            // Text colors
            holder.textViewDirectoryName.setTextColor(themeColors.textOnSurface);
            holder.textViewFileCount.setTextColor(themeColors.textSecondary);

            // Icon background tint (light primary)
            holder.imageViewIconBg.getBackground().setColorFilter(
                    themeColors.primaryLight, PorterDuff.Mode.SRC_ATOP);

            // Icon tint
            holder.imageViewIconBg.setColorFilter(themeColors.primary);

            // Chevron tint
            holder.imageViewChevron.setColorFilter(themeColors.textSecondary);
        }

        // Entrance animation
        if (position > lastAnimatedPosition) {
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(30f);
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(280)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            lastAnimatedPosition = position;
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDirectoryClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return directoryList != null ? directoryList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewDirectoryName;
        TextView textViewFileCount;
        ImageView imageViewIconBg;
        ImageView imageViewChevron;

        ViewHolder(View itemView) {
            super(itemView);
            textViewDirectoryName = itemView.findViewById(R.id.textViewDirectoryName);
            textViewFileCount = itemView.findViewById(R.id.textViewFileCount);
            imageViewIconBg = itemView.findViewById(R.id.imageViewIconBg);
            imageViewChevron = itemView.findViewById(R.id.imageViewChevron);
        }
    }
}
