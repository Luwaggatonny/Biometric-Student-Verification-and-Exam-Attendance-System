package com.uict.bioverify.model;

import java.time.LocalDateTime;

public class AttendanceLog {

    private Long logId;
    private Long studentId; // Flattened relationship
    private Long sessionId; // Flattened relationship
    private LocalDateTime verificationTime;
    private Integer matchScore;
    private String status; // VERIFIED, FAILED, FEES_HOLD

    public AttendanceLog() {
    }

    public AttendanceLog(Long studentId, Long sessionId, Integer matchScore, String status) {
        this.studentId = studentId;
        this.sessionId = sessionId;
        this.matchScore = matchScore;
        this.status = status;
    }

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getVerificationTime() {
        return verificationTime;
    }

    public void setVerificationTime(LocalDateTime verificationTime) {
        this.verificationTime = verificationTime;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Integer matchScore) {
        this.matchScore = matchScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
