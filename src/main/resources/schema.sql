-- BioVerify Database Schema Setup
-- Uganda Institute of Information and Communications Technology (UICT)

-- Drop tables if they exist to start fresh
DROP TABLE IF EXISTS session_registrations;
DROP TABLE IF EXISTS attendance_logs;
DROP TABLE IF EXISTS fingerprints;
DROP TABLE IF EXISTS exam_sessions;
DROP TABLE IF EXISTS system_settings;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS students;

-- Students Table
CREATE TABLE students (
    student_id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    reg_number VARCHAR(50) NOT NULL UNIQUE,
    program VARCHAR(100) NOT NULL,
    year INT NOT NULL,
    role VARCHAR(20) DEFAULT 'Student',
    fees_cleared BOOLEAN NOT NULL DEFAULT FALSE,
    enrollment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_verified_date TIMESTAMP NULL DEFAULT NULL
);

-- Fingerprints Table (Biometric Data)
CREATE TABLE fingerprints (
    fingerprint_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    template_data BLOB NOT NULL, -- AES encrypted VeriFinger biometric template
    finger_position INT NOT NULL DEFAULT 1, -- 1=Right Index, 2=Left Index, etc.
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE
);

-- Exam Sessions Table
CREATE TABLE exam_sessions (
    session_id INT AUTO_INCREMENT PRIMARY KEY,
    paper_name VARCHAR(100) NOT NULL,
    paper_code VARCHAR(50) NOT NULL,
    exam_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    invigilator_id INT DEFAULT NULL
);

-- Session Registrations Table (Links students to sessions for scoped 1:N validation)
CREATE TABLE session_registrations (
    session_id INT NOT NULL,
    student_id INT NOT NULL,
    PRIMARY KEY (session_id, student_id),
    FOREIGN KEY (session_id) REFERENCES exam_sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE
);

-- Attendance Logs Table
CREATE TABLE attendance_logs (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    session_id INT NOT NULL,
    verification_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    match_score INT NOT NULL,
    status VARCHAR(20) NOT NULL, -- VERIFIED, FAILED, FEES_HOLD
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES exam_sessions(session_id) ON DELETE CASCADE,
    UNIQUE KEY unique_student_session (student_id, session_id) -- Prevent duplicate attendance in same session
);

-- System Settings Table
CREATE TABLE system_settings (
    setting_key VARCHAR(50) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL
);

-- Users Table (Staff & Admins)
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL -- ADMIN, INVIGILATOR
);
