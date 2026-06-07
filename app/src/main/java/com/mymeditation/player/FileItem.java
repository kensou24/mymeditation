package com.mymeditation.player;

public class FileItem {
    private String name;
    private String path;
    private long size;
    private long lastModified;

    public FileItem(String name, String path, long size, long lastModified) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.lastModified = lastModified;
    }

    // 兼容旧调用
    public FileItem(String name, String path, long size) {
        this(name, path, size, 0);
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}
