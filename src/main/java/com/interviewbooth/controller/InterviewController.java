package com.interviewbooth.controller;

import com.interviewbooth.dto.StartSessionRequest;
import com.interviewbooth.dto.SubmitAnswerRequest;
import com.interviewbooth.model.Answer;
import com.interviewbooth.model.InterviewSession;
import com.interviewbooth.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@Valid @RequestBody StartSessionRequest request, Authentication auth) {
        try {
            Map<String, Object> result = interviewService.startSession(auth.getName(), request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/answer")
    public ResponseEntity<?> answer(@Valid @RequestBody SubmitAnswerRequest request) {
        try {
            Answer answer = interviewService.submitAnswer(request);
            return ResponseEntity.ok(answer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/finish/{sessionId}")
    public ResponseEntity<?> finish(@PathVariable Long sessionId) {
        try {
            InterviewSession session = interviewService.finishSession(sessionId);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
