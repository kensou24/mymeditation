package com.mymeditation.player;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
    private List<FileItem> fileList;
    private OnFileClickListener listener;
    private int selectedPosition = -1;
    private ThemeManager.ThemeColors themeColors;

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

        // 高亮选中的文件
        if (position == selectedPosition) {
            int highlightColor = (themeColors != null) ? themeColors.accent : ContextCompat.getColor(
                    holder.itemView.getContext(), android.R.color.holo_blue_light);
            holder.itemView.setBackgroundColor(highlightColor);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        // T15: 应用主题颜色到文字
        if (themeColors != null) {
            holder.textViewFileName.setTextColor(themeColors.textOnSurface);
            holder.textViewFileSize.setTextColor(themeColors.accent);
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewFileName;
        TextView textViewFileSize;

        ViewHolder(View itemView) {
            super(itemView);
            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFileSize = itemView.findViewById(R.id.textViewFileSize);
        }
    }
}
