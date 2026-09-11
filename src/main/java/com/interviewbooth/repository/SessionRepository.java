package com.interviewbooth.repository;

import com.interviewbooth.model.InterviewSession;
import com.interviewbooth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findByUserOrderByCreatedAtDesc(User user);
}
