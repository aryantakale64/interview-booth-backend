package com.interviewbooth.controller;

import com.interviewbooth.model.InterviewSession;
import com.interviewbooth.service.InterviewService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final InterviewService interviewService;

    public HistoryController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping
    public List<InterviewSession> history(Authentication auth) {
        return interviewService.history(auth.getName());
    }
}
