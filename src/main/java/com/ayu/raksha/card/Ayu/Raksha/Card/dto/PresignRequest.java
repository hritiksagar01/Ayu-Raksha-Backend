package com.ayu.raksha.card.Ayu.Raksha.Card.dto;

public class PresignRequest {
    private String patientId; // 12-digit patient code
    private String type; // record type folder
    private String filename;
    private String contentType;

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}

