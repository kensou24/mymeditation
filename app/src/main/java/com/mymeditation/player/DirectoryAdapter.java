package com.mymeditation.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.ViewHolder> {
    private List<DirectoryItem> directoryList;
    private OnDirectoryClickListener listener;

    public interface OnDirectoryClickListener {
        void onDirectoryClick(DirectoryItem directory);
    }

    public DirectoryAdapter(List<DirectoryItem> directoryList, OnDirectoryClickListener listener) {
        this.directoryList = directoryList;
        this.listener = listener;
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

        ViewHolder(View itemView) {
            super(itemView);
            textViewDirectoryName = itemView.findViewById(R.id.textViewDirectoryName);
            textViewFileCount = itemView.findViewById(R.id.textViewFileCount);
        }
    }
}


