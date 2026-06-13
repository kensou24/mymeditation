package com.mymeditation.player;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
    private List<FileItem> fileList;
    private OnFileClickListener listener;
    private int selectedPosition = -1;
    private ThemeManager.ThemeColors themeColors;
    private int lastAnimatedPosition = -1;

    public interface OnFileClickListener {
        void onFileClick(FileItem file, int position);
    }

    public FileAdapter(List<FileItem> fileList, OnFileClickListener listener) {
        this.fileList = fileList;
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
                .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = fileList.get(position);
        holder.textViewFileName.setText(item.getName());
        holder.textViewFileSize.setText(item.getFormattedSize());

        boolean isSelected = (position == selectedPosition);

        // Now-playing indicator
        if (holder.viewNowPlayingIndicator != null) {
            if (isSelected && themeColors != null) {
                holder.viewNowPlayingIndicator.setVisibility(View.VISIBLE);
                holder.viewNowPlayingIndicator.setBackgroundColor(themeColors.accent);
            } else {
                holder.viewNowPlayingIndicator.setVisibility(View.GONE);
            }
        }

        // Apply theme colors
        if (themeColors != null) {
            // Card background - use surface color, tint selected cards
            MaterialCardView card = (MaterialCardView) holder.itemView;
            if (isSelected) {
                // Selected: use a light tint of accent for the card
                card.setCardBackgroundColor(mixColor(themeColors.surface, themeColors.accent, 0.12f));
                card.setStrokeColor(themeColors.accent);
                card.setStrokeWidth(2);
            } else {
                card.setCardBackgroundColor(themeColors.surface);
                card.setStrokeColor(Color.TRANSPARENT);
                card.setStrokeWidth(0);
            }

            // Text colors
            holder.textViewFileName.setTextColor(
                    isSelected ? themeColors.accent : themeColors.textOnSurface);
            holder.textViewFileSize.setTextColor(themeColors.textSecondary);

            // Icon background tint
            if (isSelected) {
                holder.imageViewIconBg.getBackground().setColorFilter(
                        themeColors.accent, PorterDuff.Mode.SRC_ATOP);
                holder.imageViewIconBg.setColorFilter(Color.WHITE);
            } else {
                holder.imageViewIconBg.getBackground().setColorFilter(
                        themeColors.primaryLight, PorterDuff.Mode.SRC_ATOP);
                holder.imageViewIconBg.setColorFilter(themeColors.primary);
            }
        }

        // Entrance animation
        if (position > lastAnimatedPosition) {
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(20f);
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(250)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            lastAnimatedPosition = position;
        }

        holder.itemView.setOnClickListener(v -> {
            int previousSelected = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(previousSelected);
            notifyItemChanged(selectedPosition);
            if (listener != null) {
                listener.onFileClick(item, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return fileList != null ? fileList.size() : 0;
    }

    public void resetSelection() {
        int previousSelected = selectedPosition;
        selectedPosition = -1;
        if (previousSelected != -1) {
            notifyItemChanged(previousSelected);
        }
    }

    /**
     * Reset the animation tracking so items re-animate when list is refreshed.
     */
    public void resetAnimation() {
        lastAnimatedPosition = -1;
    }

    /**
     * Mix two colors with a ratio.
     */
    private int mixColor(int baseColor, int tintColor, float ratio) {
        int rBase = Color.red(baseColor);
        int gBase = Color.green(baseColor);
        int bBase = Color.blue(baseColor);
        int rTint = Color.red(tintColor);
        int gTint = Color.green(tintColor);
        int bTint = Color.blue(tintColor);
        int r = (int) (rBase + (rTint - rBase) * ratio);
        int g = (int) (gBase + (gTint - gBase) * ratio);
        int b = (int) (bBase + (bTint - bBase) * ratio);
        return Color.rgb(r, g, b);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewFileName;
        TextView textViewFileSize;
        ImageView imageViewIconBg;
        View viewNowPlayingIndicator;

        ViewHolder(View itemView) {
            super(itemView);
            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFileSize = itemView.findViewById(R.id.textViewFileSize);
            imageViewIconBg = itemView.findViewById(R.id.imageViewIconBg);
            viewNowPlayingIndicator = itemView.findViewById(R.id.viewNowPlayingIndicator);
        }
    }
}
