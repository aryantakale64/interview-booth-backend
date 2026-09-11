package com.interviewbooth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InterviewBoothApplication {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public static void main(String[] args) {
        SpringApplication.run(InterviewBoothApplication.class, args);
    }

    @PostConstruct
    public void logAiStatus() {
        // Makes it immediately obvious in the console whether the app will use real
        // Gemini AI or the built-in fallback — instead of silently falling back and
        // leaving you guessing why the questions/scores look generic.
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            System.out.println();
            System.out.println("=================================================================");
            System.out.println(" GEMINI_API_KEY is NOT set - using fallback questions/scoring.");
            System.out.println(" Set it with: export GEMINI_API_KEY=your-key-here (before running)");
            System.out.println("=================================================================");
            System.out.println();
        } else {
            System.out.println();
            System.out.println("=================================================================");
            System.out.println(" GEMINI_API_KEY detected - using real AI for questions/scoring.");
            System.out.println("=================================================================");
            System.out.println();
        }
    }
}
