package com.interviewbooth.dto;

public class QuestionDTO {
    private String id;
    private String text;

    public QuestionDTO() {}

    public QuestionDTO(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
