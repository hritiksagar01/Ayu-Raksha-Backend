package com.ayu.raksha.card.Ayu.Raksha.Card.controller;

import com.ayu.raksha.card.Ayu.Raksha.Card.dto.FileInfo;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.PresignRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.RecordMetadataRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.MedicalRecord;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.S3Service;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.MedicalRecordRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.s3.model.S3Object;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Autowired
    private S3Service s3Service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    private boolean isPatientAndNotSelf(String requestedPatientId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isPatient = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals("ROLE_PATIENT"));
        if (!isPatient) return false;
        Object principal = auth.getPrincipal();
        String email = null;
        if (principal instanceof User) {
            email = ((User) principal).getEmail();
        } else if (principal instanceof String) {
            email = (String) principal;
        }
        if (email == null) return true; // deny if cannot verify
        User u = userRepository.findByEmail(email).orElse(null);
        return u == null || u.getPatientId() == null || !u.getPatientId().equals(requestedPatientId);
    }

    // New: presigned PUT for direct upload
    @PostMapping("/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequest req) {
        if (req == null || req.getPatientId() == null || req.getType() == null || req.getFilename() == null || req.getContentType() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing required fields"));
        }
        if (isPatientAndNotSelf(req.getPatientId())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Patients can only upload for themselves"));
        }
        String key = s3Service.buildKey(req.getPatientId(), req.getType(), req.getFilename());
        String url = s3Service.getPresignedPutUrl(key, 60, req.getContentType());
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("url", url);
        data.put("filename", req.getFilename());
        return ResponseEntity.ok(Map.of("success", true, "data", data, "message", "OK"));
    }

    // New: create metadata record after upload
    @PostMapping("/metadata")
    public ResponseEntity<?> saveMetadata(@RequestBody RecordMetadataRequest req) {
        if (req == null || req.getPatientId() == null || req.getType() == null || req.getKey() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing required fields"));
        }
        if (isPatientAndNotSelf(req.getPatientId())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Patients can only create records for themselves"));
        }
        User patient = userRepository.findByPatientId(req.getPatientId()).orElse(null);
        if (patient == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Patient not found"));
        }
        MedicalRecord mr = new MedicalRecord();
        mr.setPatient(patient);
        mr.setType(req.getType());
        try { if (req.getDate() != null) mr.setDate(LocalDate.parse(req.getDate())); } catch (Exception ignored) {}
        mr.setDoctor(req.getDoctor());
        mr.setClinic(req.getClinic());
        mr.setFindings(req.getFindings());
        mr.setStatus(req.getStatus());
        mr.setFileKey(req.getKey());
        mr.setFilename(req.getFilename());
        mr.setSize(req.getSize());
        String url = s3Service.getPresignedUrl(req.getKey(), 60);
        mr.setFileUrl(url);
        medicalRecordRepository.save(mr);

        Map<String, Object> data = new HashMap<>();
        data.put("id", String.valueOf(mr.getId()));
        data.put("type", mr.getType());
        data.put("date", mr.getDate() != null ? mr.getDate().toString() : null);
        data.put("doctor", mr.getDoctor());
        data.put("clinic", mr.getClinic());
        data.put("findings", mr.getFindings());
        data.put("status", mr.getStatus());
        data.put("fileUrl", mr.getFileUrl());
        data.put("filename", mr.getFilename());
        data.put("size", mr.getSize());
        return ResponseEntity.ok(Map.of("success", true, "data", data, "message", "Record saved"));
    }

    // Admin/uploader uploads a file for a patient
    @PostMapping("/{patientId}/{type}")
    public ResponseEntity<?> uploadFile(@PathVariable String patientId, @PathVariable String type, @RequestParam("file") MultipartFile file) {
        // Authentication is handled by security; only users with ADMIN or UPLOADER roles will reach here
        try {
            String key = s3Service.uploadFile(patientId, type, file);
            String url = s3Service.getPresignedUrl(key, 60);
            FileInfo info = new FileInfo(key, file.getOriginalFilename(), url, file.getSize());
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("key", info.getKey());
            dataMap.put("filename", info.getFilename());
            dataMap.put("url", info.getUrl());
            dataMap.put("size", info.getSize());
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("data", dataMap);
            resp.put("message", "File uploaded successfully");
            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Upload failed: " + e.getMessage()));
        }
    }

    // Patient (or admin/uploader) lists files for a patientId
    @GetMapping("/{patientId}")
    public ResponseEntity<?> listFiles(@PathVariable String patientId) {
        if (isPatientAndNotSelf(patientId)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Patients can only view their own files"));
        }
        User patient = userRepository.findByPatientId(patientId).orElse(null);
        if (patient == null) return ResponseEntity.status(404).body(Map.of("success", false, "error", "Patient not found"));
        List<MedicalRecord> list = medicalRecordRepository.findByPatientOrderByDateDescIdDesc(patient);
        List<Map<String, Object>> files = list.stream().map(mr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("key", mr.getFileKey());
            m.put("filename", mr.getFilename());
            String url = mr.getFileUrl();
            if (url == null && mr.getFileKey() != null) url = s3Service.getPresignedUrl(mr.getFileKey(), 60);
            m.put("url", url);
            m.put("size", mr.getSize());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("success", true, "data", files));
    }
}
