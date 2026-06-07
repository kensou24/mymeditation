package com.mymeditation.player;

import android.graphics.Color;
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

    public interface OnFileClickListener {
        void onFileClick(FileItem file, int position);
    }

    public FileAdapter(List<FileItem> fileList, OnFileClickListener listener) {
        this.fileList = fileList;
        this.listener = listener;
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
            holder.itemView.setBackgroundColor(ContextCompat.getColor(
                    holder.itemView.getContext(), android.R.color.holo_blue_light));
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
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

    public void setSelectedPosition(int position) {
        int previousSelected = selectedPosition;
        selectedPosition = position;
        if (previousSelected != -1) {
            notifyItemChanged(previousSelected);
        }
        if (selectedPosition != -1) {
            notifyItemChanged(selectedPosition);
        }
    }

    public int getSelectedPosition() {
        return selectedPosition;
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

