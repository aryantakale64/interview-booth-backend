package com.interviewbooth.service;

import com.interviewbooth.dto.QuestionDTO;
import com.interviewbooth.dto.StartSessionRequest;
import com.interviewbooth.dto.SubmitAnswerRequest;
import com.interviewbooth.model.Answer;
import com.interviewbooth.model.InterviewSession;
import com.interviewbooth.model.User;
import com.interviewbooth.repository.SessionRepository;
import com.interviewbooth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class InterviewService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AIService aiService;

    public InterviewService(SessionRepository sessionRepository, UserRepository userRepository, AIService aiService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.aiService = aiService;
    }

    public Map<String, Object> startSession(String userEmail, StartSessionRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        InterviewSession session = new InterviewSession();
        session.setUser(user);
        session.setRole(request.getRole());
        session.setDifficulty(request.getDifficulty());
        session.setRoundType(request.getRoundType());
        sessionRepository.save(session);

        List<QuestionDTO> questions = aiService.generateQuestions(
                request.getRole(), request.getDifficulty(), request.getRoundType(), 5);

        return Map.of("sessionId", session.getId(), "questions", questions);
    }

    public Answer submitAnswer(SubmitAnswerRequest request) {
        InterviewSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));

        AIService.ScoreResult result = aiService.scoreAnswer(
                request.getQuestionText(), request.getUserAnswer(),
                session.getRole(), session.getDifficulty());

        Answer answer = new Answer();
        answer.setSession(session);
        answer.setQuestionText(request.getQuestionText());
        answer.setUserAnswer(request.getUserAnswer());
        answer.setScore(result.score());
        answer.setFeedback(result.feedback());

        session.getAnswers().add(answer);
        sessionRepository.save(session);

        return answer;
    }

    public InterviewSession finishSession(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));

        int total = session.getAnswers().stream().mapToInt(Answer::getScore).sum();
        int avg = session.getAnswers().isEmpty() ? 0 : total / session.getAnswers().size();
        session.setAvgScore(avg);

        return sessionRepository.save(session);
    }

    public List<InterviewSession> history(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        return sessionRepository.findByUserOrderByCreatedAtDesc(user);
    }
}
