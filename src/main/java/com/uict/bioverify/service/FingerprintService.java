package com.uict.bioverify.service;

import com.uict.bioverify.model.Fingerprint;
import com.uict.bioverify.model.Student;
import com.uict.bioverify.repository.FingerprintRepository;
import com.uict.bioverify.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NSubject;
import com.neurotec.io.NBuffer;
import com.neurotec.biometrics.NMatchingSpeed;

public class FingerprintService {

    private static final Logger logger = LoggerFactory.getLogger(FingerprintService.class);

    private final FingerprintRepository fingerprintRepository;
    private final StudentRepository studentRepository;
    private final ScannerService scannerService;
    private final String aesKeyString;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    // Cache to hold decrypted template byte arrays mapped by fingerprintId
    private final Map<Long, byte[]> decryptedTemplateCache = new ConcurrentHashMap<>();

    public FingerprintService(FingerprintRepository fingerprintRepository, 
                              StudentRepository studentRepository, 
                              ScannerService scannerService, 
                              Properties props) {
        this.fingerprintRepository = fingerprintRepository;
        this.studentRepository = studentRepository;
        this.scannerService = scannerService;
        this.aesKeyString = props.getProperty("bioverify.security.aes-key", "4a706e4e372e6b72646d51677a33567a");
    }

    private byte[] getDecryptedTemplate(Fingerprint fp) {
        return decryptedTemplateCache.computeIfAbsent(fp.getFingerprintId(), id -> decrypt(fp.getTemplateData()));
    }

    private int getMatchingThreshold(Connection conn) {
        try {
            String sql = "SELECT setting_value FROM system_settings WHERE setting_key = 'matching_threshold'";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Integer.parseInt(rs.getString("setting_value"));
                }
            }
        } catch (Exception e) {
            logger.warn("Could not load matching_threshold from database, using default 48", e);
        }
        return 48; // default FAR threshold
    }

    // ==========================================
    // AES Encryption / Decryption Utilities
    // ==========================================
    
    public byte[] encrypt(byte[] data) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(aesKeyString.getBytes(), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] ciphertext = cipher.doFinal(data);

            // Pack IV + Ciphertext
            byte[] encrypted = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, encrypted, 0, iv.length);
            System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);

            return encrypted;
        } catch (Exception e) {
            logger.error("Error encrypting template data", e);
            throw new RuntimeException("Encryption failure: " + e.getMessage());
        }
    }

    public byte[] decrypt(byte[] encryptedData) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            System.arraycopy(encryptedData, 0, iv, 0, iv.length);

            int ciphertextLength = encryptedData.length - iv.length;
            byte[] ciphertext = new byte[ciphertextLength];
            System.arraycopy(encryptedData, iv.length, ciphertext, 0, ciphertextLength);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(aesKeyString.getBytes(), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            logger.error("Error decrypting template data", e);
            throw new RuntimeException("Decryption failure: " + e.getMessage());
        }
    }

    // ==========================================
    // Biometric Operations
    // ==========================================

    // Enroll a captured template
    public Fingerprint enrollFingerprint(Connection conn, Student student, byte[] templateData, Integer position) throws Exception {
        byte[] encryptedTemplate = encrypt(templateData);

        // Delete any existing fingerprint for this position to avoid duplicates
        Optional<Fingerprint> existing = fingerprintRepository.findByStudentIdAndFingerPosition(conn, student.getStudentId(), position);
        if (existing.isPresent()) {
            decryptedTemplateCache.remove(existing.get().getFingerprintId()); // Invalidate cache
            fingerprintRepository.delete(conn, existing.get());
        }

        Fingerprint fingerprint = new Fingerprint(student.getStudentId(), encryptedTemplate, position);
        Fingerprint saved = fingerprintRepository.save(conn, fingerprint);
        decryptedTemplateCache.put(saved.getFingerprintId(), templateData); // Populate cache
        return saved;
    }

    // 1:1 Verification Match
    public MatchResult verifyFingerprint(Connection conn, Student student, byte[] candidateTemplate) {
        NBiometricClient client = null;
        NSubject candidateSubject = null;
        try {
            List<Fingerprint> fingerprints = fingerprintRepository.findByStudentId(conn, student.getStudentId());
            if (fingerprints.isEmpty()) {
                return new MatchResult(false, 0, "No enrolled fingerprint templates found for this student.");
            }

            // Real VeriFinger SDK Matching
            client = new NBiometricClient();
            client.setFingersMatchingSpeed(NMatchingSpeed.HIGH);
            client.setProperty("Fingers.MatchingThreshold", getMatchingThreshold(conn));
            client.initialize();

            logger.info("Candidate template size: {}", candidateTemplate.length);
            candidateSubject = NSubject.fromMemory(NBuffer.fromArray(candidateTemplate));

            for (Fingerprint fp : fingerprints) {
                byte[] enrolledTemplate = getDecryptedTemplate(fp);
                logger.info("Enrolled template position: {}, size: {}", fp.getFingerPosition(), enrolledTemplate.length);
                NSubject referenceSubject = null;
                try {
                    referenceSubject = NSubject.fromMemory(NBuffer.fromArray(enrolledTemplate));
                    NBiometricStatus status = client.verify(candidateSubject, referenceSubject);
                    logger.info("Biometric verification status for position {}: {}", fp.getFingerPosition(), status);
                    if (status == NBiometricStatus.OK) {
                        int score = candidateSubject.getMatchingResults().get(0).getScore();
                        logger.info("Verification succeeded with score: {}", score);
                        return new MatchResult(true, score, "Fingerprint matched successfully.");
                    }
                } finally {
                    if (referenceSubject != null) {
                        referenceSubject.dispose();
                    }
                }
            }

            logger.warn("Verification failed: no matching fingerprint found among enrolled templates.");
            return new MatchResult(false, 0, "Fingerprint did not match any enrolled templates.");
        } catch (Throwable t) {
            logger.error("Error executing real biometric match", t);
            return new MatchResult(false, 0, "Biometric match error: " + t.getMessage());
        } finally {
            if (candidateSubject != null) {
                candidateSubject.dispose();
            }
            if (client != null) {
                client.dispose();
            }
        }
    }

    // Identify Student by Fingerprint (1:N search - Restricted to Session scope!)
    public IdentifiedStudentResult identifyStudent(Connection conn, Long sessionId, byte[] candidateTemplate) {
        NBiometricClient client = null;
        NSubject candidateSubject = null;
        try {
            // Retrieve only fingerprints for students actively registered for this session
            List<Fingerprint> sessionFingerprints = fingerprintRepository.findFingerprintsForSession(conn, sessionId);
            if (sessionFingerprints.isEmpty()) {
                logger.warn("1:N Identification failed: No registered student fingerprints in active session.");
                return new IdentifiedStudentResult(null, 0, "No students registered for this session have enrolled fingerprints.");
            }

            client = new NBiometricClient();
            client.setFingersMatchingSpeed(NMatchingSpeed.HIGH);
            client.setProperty("Fingers.MatchingThreshold", getMatchingThreshold(conn));
            client.initialize();

            logger.info("Candidate template size (1:N): {}", candidateTemplate.length);
            candidateSubject = NSubject.fromMemory(NBuffer.fromArray(candidateTemplate));

            for (Fingerprint fp : sessionFingerprints) {
                byte[] enrolledTemplate = getDecryptedTemplate(fp);
                NSubject referenceSubject = null;
                try {
                    referenceSubject = NSubject.fromMemory(NBuffer.fromArray(enrolledTemplate));
                    NBiometricStatus status = client.verify(candidateSubject, referenceSubject);
                    if (status == NBiometricStatus.OK) {
                        int score = candidateSubject.getMatchingResults().get(0).getScore();
                        
                        // Load full Student entity only on a match
                        Optional<Student> studentOpt = studentRepository.findById(conn, fp.getStudentId());
                        if (studentOpt.isPresent()) {
                            Student student = studentOpt.get();
                            logger.info("1:N identification succeeded! Matched student: {}, reg: {}, score: {}", 
                                student.getFullName(), student.getRegNumber(), score);
                            return new IdentifiedStudentResult(student, score, "Identified");
                        }
                    }
                } finally {
                    if (referenceSubject != null) {
                        referenceSubject.dispose();
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Error during 1:N identification", t);
            return new IdentifiedStudentResult(null, 0, "Identification failed: " + t.getMessage());
        } finally {
            if (candidateSubject != null) {
                candidateSubject.dispose();
            }
            if (client != null) {
                client.dispose();
            }
        }

        logger.warn("1:N Identification failed: no matching template found in session.");
        return new IdentifiedStudentResult(null, 0, "No matching fingerprint template found.");
    }

    // Helper classes for result payloads
    public static class MatchResult {
        private final boolean matched;
        private final int score;
        private final String message;

        public MatchResult(boolean matched, int score, String message) {
            this.matched = matched;
            this.score = score;
            this.message = message;
        }

        public boolean isMatched() {
            return matched;
        }

        public int getScore() {
            return score;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class IdentifiedStudentResult {
        private final Student student;
        private final int score;
        private final String message;

        public IdentifiedStudentResult(Student student, int score, String message) {
            this.student = student;
            this.score = score;
            this.message = message;
        }

        public Student getStudent() {
            return student;
        }

        public int getScore() {
            return score;
        }

        public String getMessage() {
            return message;
        }
    }
}
