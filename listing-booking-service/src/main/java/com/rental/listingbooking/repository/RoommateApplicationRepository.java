package com.rental.listingbooking.repository;

import com.rental.listingbooking.entity.RoommateApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoommateApplicationRepository extends JpaRepository<RoommateApplication, Long> {

    Optional<RoommateApplication> findByRoommatePostIdAndCustomerId(Long roommatePostId, Long customerId);

    Page<RoommateApplication> findByRoommatePostId(Long roommatePostId, Pageable pageable);

    Page<RoommateApplication> findByRoommatePostIdAndStatus(Long roommatePostId, String status, Pageable pageable);

    List<RoommateApplication> findByCustomerId(Long customerId);
}
