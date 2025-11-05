package com.ayu.raksha.card.Ayu.Raksha.Card.dto;

public class FileInfo {
    private String key;
    private String filename;
    private String url;
    private Long size;

    public FileInfo() {}

    public FileInfo(String key, String filename, String url, Long size) {
        this.key = key;
        this.filename = filename;
        this.url = url;
        this.size = size;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}

