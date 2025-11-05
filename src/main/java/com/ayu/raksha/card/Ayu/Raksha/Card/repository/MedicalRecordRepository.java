package com.ayu.raksha.card.Ayu.Raksha.Card.repository;

import com.ayu.raksha.card.Ayu.Raksha.Card.models.MedicalRecord;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    long countByPatient(User patient);
    Optional<MedicalRecord> findTopByPatientOrderByDateDescIdDesc(User patient);
    List<MedicalRecord> findTop5ByPatientOrderByDateDescIdDesc(User patient);
    List<MedicalRecord> findByPatientOrderByDateDescIdDesc(User patient);
    Optional<MedicalRecord> findByFileKey(String fileKey);
}
