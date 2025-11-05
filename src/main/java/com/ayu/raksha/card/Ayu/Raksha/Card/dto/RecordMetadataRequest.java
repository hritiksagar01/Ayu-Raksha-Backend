package com.ayu.raksha.card.Ayu.Raksha.Card.dto;

public class RecordMetadataRequest {
    private String patientId; // required
    private String type; // required
    private String date; // ISO date
    private String doctor;
    private String clinic;
    private String findings;
    private String status;
    private String key; // S3 object key (required)
    private String filename;
    private Long size;

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getDoctor() { return doctor; }
    public void setDoctor(String doctor) { this.doctor = doctor; }
    public String getClinic() { return clinic; }
    public void setClinic(String clinic) { this.clinic = clinic; }
    public String getFindings() { return findings; }
    public void setFindings(String findings) { this.findings = findings; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
}

