package com.ayu.raksha.card.Ayu.Raksha.Card.controller;

import com.ayu.raksha.card.Ayu.Raksha.Card.repository.UserRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.S3Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import software.amazon.awssdk.services.s3.model.S3Object;

import com.ayu.raksha.card.Ayu.Raksha.Card.models.AppointmentStatus;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.MedicalRecord;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.AlertRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.AppointmentRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.MedicalRecordRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final AppointmentRepository appointmentRepository;
    private final AlertRepository alertRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    public PatientController(UserRepository userRepository, S3Service s3Service,
                             AppointmentRepository appointmentRepository,
                             AlertRepository alertRepository,
                             MedicalRecordRepository medicalRecordRepository) {
        this.userRepository = userRepository;
        this.s3Service = s3Service;
        this.appointmentRepository = appointmentRepository;
        this.alertRepository = alertRepository;
        this.medicalRecordRepository = medicalRecordRepository;
    }

    // GET /api/patients/{patientId}?flat=true
    @GetMapping("/{patientId}")
    public ResponseEntity<?> getPatientByPatientId(
            @PathVariable String patientId

    ) {
        return userRepository.findByPatientId(patientId)
                .map(user -> {
                    Map<String, Object> patientMap = new HashMap<>();
                    patientMap.put("id", String.valueOf(user.getId()));
                    patientMap.put("name", user.getName());
                    Integer age = null;
                    try {
                        if (user.getDateOfBirth() != null) {
                            age = java.time.Period.between(user.getDateOfBirth(), java.time.LocalDate.now()).getYears();
                        }
                    } catch (Exception ignored) {}
                    patientMap.put("age", age);
                    patientMap.put("gender", user.getGender());
                    patientMap.put("email", user.getEmail());
                    patientMap.put("phone", user.getPhone());
                    patientMap.put("patientCode", user.getPatientId());
                    return ResponseEntity.ok(Map.of("success", true, "data", patientMap));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Patient not found")));
    }

    // Helper: ensure that patients can only access their own data
    private boolean isSelfOrPrivileged(String requestedPatientId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        boolean isPatient = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals("ROLE_PATIENT"));
        if (!isPatient) return true; // doctor/uploader/admin handled by SecurityConfig, allow
        // Patient: must match their own patientId
        Object principal = auth.getPrincipal();
        String email = null;
        if (principal instanceof org.springframework.security.core.userdetails.User u) {
            email = u.getUsername();
        } else if (principal instanceof String) {
            email = (String) principal;
        } else if (principal instanceof com.ayu.raksha.card.Ayu.Raksha.Card.models.User u) {
            email = u.getEmail();
        }
        if (email == null) return false;
        return userRepository.findByEmail(email)
                .map(u -> requestedPatientId.equals(u.getPatientId()))
                .orElse(false);
    }

    @GetMapping("/{patientId}/profile")
    public ResponseEntity<?> getPatientProfile(@PathVariable String patientId) {
        if (!isSelfOrPrivileged(patientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "error", "Access denied"));
        }
        return userRepository.findByPatientId(patientId)
                .map(user -> {
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("id", String.valueOf(user.getId()));
                    profile.put("name", user.getName());
                    profile.put("email", user.getEmail());
                    profile.put("phone", user.getPhone());
                    profile.put("gender", user.getGender());
                    profile.put("dateOfBirth", user.getDateOfBirth() != null ? user.getDateOfBirth().format(DateTimeFormatter.ISO_DATE) : null);
                    profile.put("patientCode", user.getPatientId());
                    return ResponseEntity.ok(Map.of("success", true, "data", profile));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Patient not found")));
    }

    @GetMapping("/{patientId}/dashboard")
    public ResponseEntity<?> getPatientDashboard(@PathVariable String patientId) {
        if (!isSelfOrPrivileged(patientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "error", "Access denied"));
        }
        return userRepository.findByPatientId(patientId).map((User patient) -> {
            // Counts
            long appointmentsUpcoming = appointmentRepository
                    .countByPatientAndStatusAndDateGreaterThanEqual(patient, AppointmentStatus.SCHEDULED, LocalDate.now());
            long alertsActive = alertRepository.countByPatientAndDateGreaterThanEqual(patient, LocalDate.now().minusDays(30));
            var recent = medicalRecordRepository.findTop5ByPatientOrderByDateDescIdDesc(patient);
            int reportsRecent = recent.size();

            Map<String, Object> counts = new HashMap<>();
            counts.put("appointmentsUpcoming", appointmentsUpcoming);
            counts.put("alertsActive", alertsActive);
            counts.put("reportsRecent", reportsRecent);

            Map<String, Object> latestRecord = null;
            var latestOpt = medicalRecordRepository.findTopByPatientOrderByDateDescIdDesc(patient);
            if (latestOpt.isPresent()) {
                MedicalRecord mr = latestOpt.get();
                latestRecord = new HashMap<>();
                latestRecord.put("id", String.valueOf(mr.getId()));
                latestRecord.put("type", mr.getType());
                latestRecord.put("date", mr.getDate() != null ? mr.getDate().format(DateTimeFormatter.ISO_DATE) : null);
                latestRecord.put("findings", mr.getFindings());
                latestRecord.put("status", mr.getStatus());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("counts", counts);
            data.put("latestRecord", latestRecord);
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "error", "Patient not found")));
    }

    @GetMapping("/{patientId}/appointments")
    public ResponseEntity<?> listAppointments(
            @PathVariable String patientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        if (!isSelfOrPrivileged(patientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "error", "Access denied"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", java.util.List.of(),
                "message", "OK"
        ));
    }

    @GetMapping("/{patientId}/alerts")
    public ResponseEntity<?> listAlerts(
            @PathVariable String patientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        if (!isSelfOrPrivileged(patientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "error", "Access denied"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", java.util.List.of()
        ));
    }

    @GetMapping("/{patientId}/records")
    public ResponseEntity<?> listRecords(
            @PathVariable String patientId,
            @RequestParam(required = false, defaultValue = "5") int limit
    ) {
        System.out.println("=== GET /api/patients/" + patientId + "/records called ===");
        System.out.println("Limit: " + limit);

        if (!isSelfOrPrivileged(patientId)) {
            System.err.println("Access denied for patientId: " + patientId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "error", "Access denied"));
        }

        return userRepository.findByPatientId(patientId).map((User patient) -> {
            System.out.println("Patient found: " + patient.getEmail() + " (ID: " + patient.getId() + ")");

            List<MedicalRecord> list = limit > 0 ? medicalRecordRepository.findTop5ByPatientOrderByDateDescIdDesc(patient)
                    : medicalRecordRepository.findByPatientOrderByDateDescIdDesc(patient);

            System.out.println("Found " + list.size() + " medical records in database");

            List<Map<String, Object>> data = new ArrayList<>();

            // Add database records
            for (MedicalRecord mr : list) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", String.valueOf(mr.getId()));
                m.put("type", mr.getType());
                m.put("date", mr.getDate() != null ? mr.getDate().format(DateTimeFormatter.ISO_DATE) : null);
                m.put("doctor", mr.getDoctor());
                m.put("clinic", mr.getClinic());
                m.put("findings", mr.getFindings());
                m.put("status", mr.getStatus());
                String url = mr.getFileUrl();
                if (url == null && mr.getFileKey() != null) {
                    url = s3Service.getPresignedUrl(mr.getFileKey(), 60);
                    System.out.println("Generated presigned URL for key: " + mr.getFileKey());
                }
                m.put("fileUrl", url);
                data.add(m);
            }

            // If no database records, check S3 and create records from files
            if (data.isEmpty()) {
                System.out.println("No database records found, checking S3...");
                List<S3Object> s3Files = s3Service.listFilesForPatient(patientId);
                System.out.println("Found " + s3Files.size() + " files in S3 for patient " + patientId);

                for (S3Object s3Object : s3Files) {
                    String key = s3Object.key();
                    String filename = key.substring(key.lastIndexOf('/') + 1);

                    // Extract type from path (e.g., "029451840406/blood-report/file.pdf" -> "blood-report")
                    String type = "Medical Document";
                    String[] pathParts = key.split("/");
                    if (pathParts.length >= 2) {
                        type = pathParts[1].replace("-", " ").replace("_", " ");
                        // Capitalize first letter of each word
                        type = java.util.Arrays.stream(type.split(" "))
                                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                                .collect(java.util.stream.Collectors.joining(" "));
                    }

                    Map<String, Object> m = new HashMap<>();
                    m.put("id", "s3-" + s3Object.key().hashCode()); // Generate temporary ID
                    m.put("type", type);
                    m.put("date", LocalDate.now().format(DateTimeFormatter.ISO_DATE)); // Use current date as fallback
                    m.put("doctor", null);
                    m.put("clinic", null);
                    m.put("findings", "Uploaded file: " + filename);
                    m.put("status", "Available");
                    m.put("filename", filename);
                    m.put("fileUrl", s3Service.getPresignedUrl(key, 60));
                    m.put("fileKey", key);
                    m.put("size", s3Object.size());
                    data.add(m);
                }

                System.out.println("Created " + data.size() + " records from S3 files");
            }

            // Sort by date descending
            data.sort((a, b) -> {
                String dateA = (String) a.get("date");
                String dateB = (String) b.get("date");
                if (dateA == null) return 1;
                if (dateB == null) return -1;
                return dateB.compareTo(dateA);
            });

            // Apply limit if specified
            if (limit > 0 && data.size() > limit) {
                data = data.subList(0, limit);
            }

            System.out.println("Returning " + data.size() + " records to frontend");
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        }).orElseGet(() -> {
            System.err.println("Patient not found with patientCode: " + patientId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "error", "Patient not found"));
        });
    }

    // New endpoint to sync S3 files to database
    @PostMapping("/{patientId}/records/sync")
    public ResponseEntity<?> syncS3RecordsToDatabase(@PathVariable String patientId) {
        System.out.println("=== POST /api/patients/" + patientId + "/records/sync called ===");

        if (!isSelfOrPrivileged(patientId)) {
            System.err.println("Access denied for patientId: " + patientId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "error", "Access denied"));
        }

        return userRepository.findByPatientId(patientId).map((User patient) -> {
            System.out.println("Syncing S3 files for patient: " + patient.getEmail());

            List<S3Object> s3Files = s3Service.listFilesForPatient(patientId);
            System.out.println("Found " + s3Files.size() + " files in S3");

            int created = 0;
            int skipped = 0;

            for (S3Object s3Object : s3Files) {
                String key = s3Object.key();

                // Check if record already exists
                if (medicalRecordRepository.findByFileKey(key).isPresent()) {
                    skipped++;
                    continue;
                }

                // Create new record
                MedicalRecord record = new MedicalRecord();
                record.setPatient(patient);
                record.setFileKey(key);

                String filename = key.substring(key.lastIndexOf('/') + 1);
                record.setFilename(filename);
                record.setSize(s3Object.size());

                // Extract type from path
                String type = "Medical Document";
                String[] pathParts = key.split("/");
                if (pathParts.length >= 2) {
                    type = pathParts[1].replace("-", " ").replace("_", " ");
                    type = java.util.Arrays.stream(type.split(" "))
                            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                            .collect(java.util.stream.Collectors.joining(" "));
                }
                record.setType(type);

                record.setDate(LocalDate.now());
                record.setStatus("Available");
                record.setFindings("Imported from S3");

                medicalRecordRepository.save(record);
                created++;
            }

            System.out.println("Sync complete: " + created + " created, " + skipped + " skipped");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "created", created,
                            "skipped", skipped,
                            "total", s3Files.size()
                    ),
                    "message", "S3 files synced to database"
            ));
        }).orElseGet(() -> {
            System.err.println("Patient not found with patientCode: " + patientId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "error", "Patient not found"));
        });
    }
}
