package com.mymeditation.player;

public class DirectoryItem {
    private String name;
    private String path;
    private int fileCount;

    public DirectoryItem(String name, String path, int fileCount) {
        this.name = name;
        this.path = path;
        this.fileCount = fileCount;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public int getFileCount() {
        return fileCount;
    }
}


