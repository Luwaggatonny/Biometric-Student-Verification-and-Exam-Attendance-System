package com.uict.bioverify.model;

import java.time.LocalDateTime;

public class Student {

    private Long studentId;
    private String fullName;
    private String regNumber;
    private String program;
    private Integer year;
    private String role = "Student";
    private Boolean feesCleared = false;
    private LocalDateTime enrollmentDate;
    private LocalDateTime lastVerifiedDate;

    public Student() {
    }

    public Student(String fullName, String regNumber, String program, Integer year, Boolean feesCleared) {
        this.fullName = fullName;
        this.regNumber = regNumber;
        this.program = program;
        this.year = year;
        this.feesCleared = feesCleared;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRegNumber() {
        return regNumber;
    }

    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getFeesCleared() {
        return feesCleared;
    }

    public void setFeesCleared(Boolean feesCleared) {
        this.feesCleared = feesCleared;
    }

    public LocalDateTime getEnrollmentDate() {
        return enrollmentDate;
    }

    public void setEnrollmentDate(LocalDateTime enrollmentDate) {
        this.enrollmentDate = enrollmentDate;
    }

    public LocalDateTime getLastVerifiedDate() {
        return lastVerifiedDate;
    }

    public void setLastVerifiedDate(LocalDateTime lastVerifiedDate) {
        this.lastVerifiedDate = lastVerifiedDate;
    }
}
