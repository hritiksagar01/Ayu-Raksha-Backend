package com.ayu.raksha.card.Ayu.Raksha.Card.repository;

import com.ayu.raksha.card.Ayu.Raksha.Card.models.Alert;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    long countByPatientAndDateGreaterThanEqual(User patient, LocalDate fromDate);
}

