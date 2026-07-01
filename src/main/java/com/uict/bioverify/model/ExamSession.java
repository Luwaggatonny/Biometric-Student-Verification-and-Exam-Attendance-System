package com.uict.bioverify.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class ExamSession {

    private Long sessionId;
    private String paperName;
    private String paperCode;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long invigilatorId; // Flattened relationship

    public ExamSession() {
    }

    public ExamSession(String paperName, String paperCode, LocalDate examDate, LocalTime startTime, LocalTime endTime, Long invigilatorId) {
        this.paperName = paperName;
        this.paperCode = paperCode;
        this.examDate = examDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.invigilatorId = invigilatorId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getPaperName() {
        return paperName;
    }

    public void setPaperName(String paperName) {
        this.paperName = paperName;
    }

    public String getPaperCode() {
        return paperCode;
    }

    public void setPaperCode(String paperCode) {
        this.paperCode = paperCode;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Long getInvigilatorId() {
        return invigilatorId;
    }

    public void setInvigilatorId(Long invigilatorId) {
        this.invigilatorId = invigilatorId;
    }
}
