package com.ayu.raksha.card.Ayu.Raksha.Card.repository;

import com.ayu.raksha.card.Ayu.Raksha.Card.models.Appointment;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.AppointmentStatus;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    long countByPatientAndStatusAndDateGreaterThanEqual(User patient, AppointmentStatus status, LocalDate date);
}

