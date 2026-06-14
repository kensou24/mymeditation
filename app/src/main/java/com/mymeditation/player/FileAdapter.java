package com.mymeditation.player;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PorterDuff;
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

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
    private List<FileItem> fileList;
    private OnFileClickListener listener;
    private int selectedPosition = -1;
    private ThemeManager.ThemeColors themeColors;
    private int lastAnimatedPosition = -1;
    private boolean isPlaying = false;

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

    /** A3: 按文件路径高亮（与正在播放的曲目同步） */
    public void highlightByPath(String path) {
        if (path == null || fileList == null) return;
        int newIndex = -1;
        for (int i = 0; i < fileList.size(); i++) {
            if (path.equals(fileList.get(i).getPath())) {
                newIndex = i;
                break;
            }
        }
        if (newIndex == selectedPosition) return;
        int old = selectedPosition;
        selectedPosition = newIndex;
        if (old != -1) notifyItemChanged(old);
        if (newIndex != -1) notifyItemChanged(newIndex);
    }

    /** B2: 控制「正在播放」脉冲动画（高亮保留，仅动画随播放状态） */
    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;
        if (selectedPosition != -1) {
            notifyItemChanged(selectedPosition);
        }
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

        // Now-playing indicator + B2 pulse animation
        if (holder.viewNowPlayingIndicator != null) {
            if (isSelected && themeColors != null) {
                holder.viewNowPlayingIndicator.setVisibility(View.VISIBLE);
                holder.viewNowPlayingIndicator.setBackgroundColor(themeColors.accent);
                if (isPlaying) {
                    startPulse(holder);
                } else {
                    stopPulse(holder);
                }
            } else {
                stopPulse(holder);
                holder.viewNowPlayingIndicator.setVisibility(View.GONE);
            }
        }

        // Apply theme colors
        if (themeColors != null) {
            // Card background - use surface color, tint selected cards
            MaterialCardView card = (MaterialCardView) holder.itemView;
            if (isSelected) {
                card.setCardBackgroundColor(mixColor(themeColors.surface, themeColors.accent, 0.12f));
                card.setStrokeColor(themeColors.accent);
                card.setStrokeWidth(2);
            } else {
                card.setCardBackgroundColor(themeColors.surface);
                card.setStrokeColor(Color.TRANSPARENT);
                card.setStrokeWidth(0);
            }

            holder.textViewFileName.setTextColor(
                    isSelected ? themeColors.accent : themeColors.textOnSurface);
            holder.textViewFileSize.setTextColor(themeColors.textSecondary);

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
            selectedPosition = holder.getBindingAdapterPosition();
            if (previousSelected != -1) notifyItemChanged(previousSelected);
            if (selectedPosition != -1) notifyItemChanged(selectedPosition);
            if (listener != null && selectedPosition != -1) {
                listener.onFileClick(fileList.get(selectedPosition), selectedPosition);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        stopPulse(holder);
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

    private void startPulse(ViewHolder holder) {
        if (holder.viewNowPlayingIndicator == null) return;
        stopPulse(holder);
        ObjectAnimator anim = ObjectAnimator.ofFloat(
                holder.viewNowPlayingIndicator, "alpha", 1f, 0.35f);
        anim.setDuration(700);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        holder.pulseAnimator = anim;
        anim.start();
    }

    private void stopPulse(ViewHolder holder) {
        if (holder.pulseAnimator != null) {
            holder.pulseAnimator.cancel();
            holder.pulseAnimator = null;
        }
        if (holder.viewNowPlayingIndicator != null) {
            holder.viewNowPlayingIndicator.setAlpha(1f);
        }
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
        ObjectAnimator pulseAnimator;

        ViewHolder(View itemView) {
            super(itemView);
            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFileSize = itemView.findViewById(R.id.textViewFileSize);
            imageViewIconBg = itemView.findViewById(R.id.imageViewIconBg);
            viewNowPlayingIndicator = itemView.findViewById(R.id.viewNowPlayingIndicator);
        }
    }
}
