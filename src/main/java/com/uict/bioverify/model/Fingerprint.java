package com.uict.bioverify.model;

import java.time.LocalDateTime;

public class Fingerprint {

    private Long fingerprintId;
    private Long studentId; // Flattened relationship
    private byte[] templateData;
    private Integer fingerPosition = 1; // 1 = Right Index, 2 = Left Index, etc.
    private LocalDateTime createdAt;

    public Fingerprint() {
    }

    public Fingerprint(Long studentId, byte[] templateData, Integer fingerPosition) {
        this.studentId = studentId;
        this.templateData = templateData;
        this.fingerPosition = fingerPosition;
    }

    public Long getFingerprintId() {
        return fingerprintId;
    }

    public void setFingerprintId(Long fingerprintId) {
        this.fingerprintId = fingerprintId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public byte[] getTemplateData() {
        return templateData;
    }

    public void setTemplateData(byte[] templateData) {
        this.templateData = templateData;
    }

    public Integer getFingerPosition() {
        return fingerPosition;
    }

    public void setFingerPosition(Integer fingerPosition) {
        this.fingerPosition = fingerPosition;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
