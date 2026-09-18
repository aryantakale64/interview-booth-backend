package com.interviewbooth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewbooth.dto.QuestionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Talks to the free-tier Google Gemini API to (1) generate interview questions on
 * demand and (2) score/critique a candidate's spoken/typed answer.
 *
 * Requires the GEMINI_API_KEY environment variable to be set before starting the
 * app. Get a free key (no credit card needed) at https://aistudio.google.com.
 * If it's missing, this service falls back to a small built-in question set and a
 * simple heuristic score, so the app still runs end-to-end without a key.
 */
@Service
public class AIService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model}")
    private String model;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public List<QuestionDTO> generateQuestions(String role, String difficulty, String roundType, int count) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackQuestions(role, roundType);
        }

        String systemPrompt = "You are an expert technical interviewer. " +
                "Generate interview questions and respond with ONLY a JSON array of strings, " +
                "no markdown, no preamble, no extra keys. Example: [\"question 1\", \"question 2\"]";

        String userPrompt = String.format(
                "Generate %d %s-round interview questions for a %s candidate at %s difficulty level. " +
                "Questions should be specific and realistic, the kind actually asked in interviews.",
                count, roundType, role, difficulty);

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String responseText = callGemini(systemPrompt, userPrompt);
                JsonNode arr = mapper.readTree(extractJson(responseText));
                List<QuestionDTO> questions = new ArrayList<>();
                for (JsonNode node : arr) {
                    questions.add(new QuestionDTO(UUID.randomUUID().toString(), node.asText()));
                }
                if (!questions.isEmpty()) return questions;
            } catch (Exception e) {
                System.err.println("AI question generation attempt " + attempt + " failed: " + e.getMessage());
            }
        }
        return fallbackQuestions(role, roundType);
    }

    public ScoreResult scoreAnswer(String question, String answer, String role, String difficulty) {
        if (apiKey == null || apiKey.isBlank()) {
            return heuristicScore(answer);
        }

        String systemPrompt = "You are a strict, no-nonsense technical interview evaluator. " +
                "Respond with ONLY a JSON object of the form " +
                "{\"score\": <integer 0-100>, \"feedback\": \"<2-3 sentence specific feedback>\"}. " +
                "No markdown, no extra text.\n\n" +
                "You MUST follow these scoring rules exactly, in order:\n" +
                "1. If the answer is gibberish, random keyboard mashing, nonsensical characters, or not real words in any language, score it 0-5, no exceptions.\n" +
                "2. If the answer is empty, just repeats the question, or is completely unrelated to the question topic, score it 0-10.\n" +
                "3. If the answer is real language but too short or vague to show any real understanding, score it 10-30.\n" +
                "4. Only score above 70 if the answer is coherent, relevant to the specific question asked, and demonstrates genuine technical understanding.\n" +
                "Do not be lenient, encouraging, or generous with the NUMBER — accuracy matters most. " +
                "Save any encouragement for the feedback text, and only if the score actually earned it.";

        String userPrompt = String.format(
                "Role: %s\nDifficulty: %s\nQuestion: %s\nCandidate answer: \"%s\"\n\n" +
                "First silently check: is this answer coherent, real language, and actually relevant to the question? " +
                "Then apply the scoring rules strictly and give your honest score and feedback.",
                role, difficulty, question, answer);

        // Retry once on transient failures (rate limits, brief network blips) before
        // giving up — during a live demo, one retry meaningfully cuts down how often
        // the fallback scorer kicks in.
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String responseText = callGemini(systemPrompt, userPrompt, 250);
                JsonNode obj = mapper.readTree(extractJson(responseText));
                int score = obj.get("score").asInt();
                String feedback = obj.get("feedback").asText();
                return new ScoreResult(score, feedback);
            } catch (Exception e) {
                System.err.println("AI scoring attempt " + attempt + " failed: " + e.getMessage());
                if (attempt == 2) {
                    return heuristicScore(answer);
                }
            }
        }
        return heuristicScore(answer); // unreachable, keeps the compiler happy
    }

    private String callGemini(String systemPrompt, String userPrompt) throws Exception {
        return callGemini(systemPrompt, userPrompt, 1024);
    }

    private String callGemini(String systemPrompt, String userPrompt, int maxOutputTokens) throws Exception {
        String escapedSystem = mapper.writeValueAsString(systemPrompt);
        String escapedUser = mapper.writeValueAsString(userPrompt);

        // Gemini's generateContent request shape: system_instruction + contents (each
        // holding "parts" of text). This differs from Anthropic/OpenAI's message format.
        String body = String.format("""
                {
                  "system_instruction": { "parts": [{ "text": %s }] },
                  "contents": [{ "parts": [{ "text": %s }] }],
                  "generationConfig": { "temperature": 0.4, "maxOutputTokens": %d }
                }
                """, escapedSystem, escapedUser, maxOutputTokens);

        String url = apiUrl + "/" + model + ":generateContent";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(20)) // bounds total wait: fails over to the fallback scorer instead of hanging indefinitely
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        return root.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
    }

    /** Gemini sometimes wraps JSON in prose or code fences despite instructions; this strips that. */
    private String extractJson(String text) {
        String trimmed = text.trim();
        int start = Math.min(
                indexOfOrMax(trimmed, '['),
                indexOfOrMax(trimmed, '{'));
        int endBracket = trimmed.lastIndexOf(']');
        int endBrace = trimmed.lastIndexOf('}');
        int end = Math.max(endBracket, endBrace);
        if (start == Integer.MAX_VALUE || end == -1) return trimmed;
        return trimmed.substring(start, end + 1);
    }

    private int indexOfOrMax(String s, char c) {
        int idx = s.indexOf(c);
        return idx == -1 ? Integer.MAX_VALUE : idx;
    }

    private ScoreResult heuristicScore(String answer) {
        String trimmed = answer.trim();
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;

        // Crude gibberish detector: real English text is roughly 35-45% vowels.
        // Random keyboard mashing (e.g. "hjebfjhebfjh") has very few vowels relative
        // to its length, since real words need vowels to be pronounceable/readable.
        String lettersOnly = trimmed.toLowerCase().replaceAll("[^a-z]", "");
        long vowelCount = lettersOnly.chars().filter(c -> "aeiou".indexOf(c) >= 0).count();
        double vowelRatio = lettersOnly.isEmpty() ? 0 : (double) vowelCount / lettersOnly.length();

        if (lettersOnly.isEmpty()) {
            return new ScoreResult(0, "No answer was provided for this question.");
        }
        if (vowelRatio < 0.15 || (words <= 2 && lettersOnly.length() > 8 && vowelRatio < 0.25)) {
            return new ScoreResult(5, "This answer doesn't appear to be real, relevant text for the question asked.");
        }

        int score = Math.min(90, Math.max(20, words * 3 + 25));
        String feedback = words > 15
                ? "Some relevant points were mentioned, but a full evaluation wasn't available for this answer."
                : "Consider expanding your answer with more specific detail.";
        return new ScoreResult(score, feedback);
    }

    private List<QuestionDTO> fallbackQuestions(String role, String roundType) {
        List<QuestionDTO> list = new ArrayList<>();
        list.add(new QuestionDTO(UUID.randomUUID().toString(),
                "Tell me about your experience relevant to the " + role + " role."));
        list.add(new QuestionDTO(UUID.randomUUID().toString(),
                "What was the most challenging problem you solved recently, and how did you approach it?"));
        list.add(new QuestionDTO(UUID.randomUUID().toString(),
                "For a " + roundType + " round: describe a project where you had to learn something new quickly."));
        return list;
    }

    public record ScoreResult(int score, String feedback) {}
}
