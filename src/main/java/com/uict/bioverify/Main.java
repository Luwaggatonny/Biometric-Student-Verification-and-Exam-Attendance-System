package com.uict.bioverify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.*;
import com.uict.bioverify.model.*;
import com.uict.bioverify.repository.*;
import com.uict.bioverify.service.*;
import com.uict.bioverify.util.DatabaseConnection;
import com.uict.bioverify.util.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, User> activeSessions = new ConcurrentHashMap<>();

    // Repositories (Instantiated manually)
    private static UserRepository userRepository;
    private static StudentRepository studentRepository;
    private static FingerprintRepository fingerprintRepository;
    private static ExamSessionRepository examSessionRepository;
    private static AttendanceLogRepository attendanceLogRepository;
    private static SystemSettingRepository systemSettingRepository;

    // Services (Wired via constructors)
    private static ScannerService scannerService;
    private static FingerprintService fingerprintService;
    private static AttendanceService attendanceService;
    private static AuditService auditService;

    public static void main(String[] args) {
        try {
            logger.info("Bootstrapping BioVerify Standalone Application...");

            // Register Jackson time module for local date/time serialization
            objectMapper.registerModule(new JavaTimeModule());

            // 1. Load application.properties
            Properties props = new Properties();
            try (InputStream is = Main.class.getResourceAsStream("/application.properties")) {
                if (is != null) {
                    props.load(is);
                    logger.info("Loaded application.properties from classpath.");
                } else {
                    try (InputStream fis = new FileInputStream("src/main/resources/application.properties")) {
                        props.load(fis);
                        logger.info("Loaded application.properties from filesystem path.");
                    }
                }
            } catch (IOException e) {
                logger.error("Could not load application.properties", e);
                System.exit(1);
            }

            // 2. Initialize Database Connection Pool
            DatabaseConnection.initialize(props);
            logger.info("Database connection pool initialized.");

            // 3. Instantiate Repositories
            userRepository = new UserRepository();
            studentRepository = new StudentRepository();
            fingerprintRepository = new FingerprintRepository();
            examSessionRepository = new ExamSessionRepository();
            attendanceLogRepository = new AttendanceLogRepository();
            systemSettingRepository = new SystemSettingRepository();

            // 4. Initialize Database Schema and Seeding
            try (Connection conn = DatabaseConnection.getConnection()) {
                setupDatabaseSchema(conn);
                seedDatabase(conn);
            }

            // 5. Instantiate Services
            auditService = new AuditService();
            scannerService = new ScannerService(props);
            fingerprintService = new FingerprintService(fingerprintRepository, studentRepository, scannerService, props);
            attendanceService = new AttendanceService(attendanceLogRepository, studentRepository, auditService);

            // Register Shutdown Hook for physical scanner cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down scanner service and releasing SDK licenses...");
                scannerService.shutdown();
            }));

            // 6. Bootstrap HttpsServer
            int port = Integer.parseInt(props.getProperty("server.port", "8444"));
            HttpsServer server = HttpsServer.create(new InetSocketAddress(port), 0);

            // Configure SSL/TLS using local keystore.p12
            char[] ksPassword = props.getProperty("server.ssl.key-store-password", "bioverify123").toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream is = Main.class.getResourceAsStream("/keystore.p12")) {
                if (is != null) {
                    ks.load(is, ksPassword);
                } else {
                    try (InputStream fis = new FileInputStream("src/main/resources/keystore.p12")) {
                        ks.load(fis, ksPassword);
                    }
                }
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, ksPassword);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    try {
                        SSLContext context = getSSLContext();
                        SSLEngine engine = context.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());
                        SSLParameters sslParams = context.getDefaultSSLParameters();
                        params.setSSLParameters(sslParams);
                    } catch (Exception ex) {
                        logger.error("Failed to configure Https parameters", ex);
                    }
                }
            });

            // Bind handlers
            server.createContext("/", new StaticFileHandler());
            server.createContext("/api", new ApiHandler());

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();
            logger.info("HTTPS web server started successfully on port {}!", port);

        } catch (Exception e) {
            logger.error("System Bootstrapping failed", e);
            System.exit(1);
        }
    }

    // Static Web Handler serving frontend index.html
    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            InputStream is = getClass().getResourceAsStream("/static" + path);
            if (is == null) {
                File file = new File("src/main/resources/static" + path);
                if (file.exists() && file.isFile()) {
                    is = new FileInputStream(file);
                }
            }

            if (is == null) {
                String response = "404 (Not Found)";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            String contentType = "text/html";
            if (path.endsWith(".css")) {
                contentType = "text/css";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript";
            } else if (path.endsWith(".png")) {
                contentType = "image/png";
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, 0); // chunked transfer
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            } finally {
                is.close();
            }
        }
    }

    // API Routing Handler (Processes JSON requests, maps URL routes)
    private static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Cookie");

            if (method.equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Connection conn = null;
            try {
                conn = DatabaseConnection.getConnection();

                // 1. Unauthenticated Login route
                if (path.equals("/api/auth/login") && method.equals("POST")) {
                    handleLogin(exchange, conn);
                    return;
                }

                // 2. Cookie session security interceptor
                User currentUser = getAuthenticatedUser(exchange);
                if (currentUser == null) {
                    writeJsonResponse(exchange, 401, Map.of("error", "Not authenticated."));
                    return;
                }

                // 3. Authenticated routes routing table
                if (path.equals("/api/auth/logout") && method.equals("POST")) {
                    handleLogout(exchange);
                } else if (path.equals("/api/auth/me") && method.equals("GET")) {
                    writeJsonResponse(exchange, 200, currentUser);
                } else if (path.equals("/api/sessions") && method.equals("GET")) {
                    handleGetSessions(exchange, conn);
                } else if (path.equals("/api/sessions") && method.equals("POST")) {
                    handleCreateSession(exchange, conn, currentUser);
                } else if (path.equals("/api/students") && method.equals("GET")) {
                    writeJsonResponse(exchange, 200, studentRepository.findAll(conn));
                } else if (path.equals("/api/students/register") && method.equals("POST")) {
                    handleRegisterStudent(exchange, conn);
                } else if (path.startsWith("/api/students/") && method.equals("GET")) {
                    handleGetStudent(exchange, conn, path);
                } else if (path.equals("/api/fingerprint/enroll") && method.equals("POST")) {
                    handleEnrollFingerprint(exchange, conn);
                } else if (path.equals("/api/fingerprint/verify") && method.equals("POST")) {
                    handleVerifyFingerprint(exchange, conn);
                } else if (path.startsWith("/api/attendance/") && method.equals("GET")) {
                    if (path.startsWith("/api/attendance/export/")) {
                        handleExportAttendance(exchange, conn, path);
                    } else {
                        handleGetAttendanceForSession(exchange, conn, path);
                    }
                } else if (path.equals("/api/logs") && method.equals("GET")) {
                    writeJsonResponse(exchange, 200, auditService.getLogs());
                } else if (path.equals("/api/scanner/status") && method.equals("GET")) {
                    writeJsonResponse(exchange, 200, Map.of(
                        "status", scannerService.getScannerStatus(),
                        "isSimulator", false,
                        "lastError", scannerService.getLastErrorMessage()
                    ));
                } else {
                    writeJsonResponse(exchange, 404, Map.of("error", "API endpoint not found."));
                }
            } catch (Exception e) {
                logger.error("API error at path: " + path, e);
                try {
                    writeJsonResponse(exchange, 500, Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal Server Error"));
                } catch (Exception ignored) {}
            } finally {
                if (conn != null) {
                    DatabaseConnection.releaseConnection(conn);
                }
            }
        }
    }

    // Helper controllers implementation
    private static void handleLogin(HttpExchange exchange, Connection conn) throws Exception {
        Map<String, String> creds = readJsonRequest(exchange, Map.class);
        String username = creds.get("username");
        String password = creds.get("password");

        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            writeJsonResponse(exchange, 400, Map.of("error", "Username and password are required."));
            return;
        }

        Optional<User> userOpt = userRepository.findByUsername(conn, username.trim());
        if (userOpt.isEmpty()) {
            auditService.logEvent("AUTH", "Failed login attempt for username: " + username);
            writeJsonResponse(exchange, 401, Map.of("error", "Invalid username or password."));
            return;
        }

        User user = userOpt.get();
        if (!PasswordHasher.checkPassword(password, user.getPasswordHash())) {
            auditService.logEvent("AUTH", "Failed password match for username: " + username);
            writeJsonResponse(exchange, 401, Map.of("error", "Invalid username or password."));
            return;
        }

        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, user);
        exchange.getResponseHeaders().add("Set-Cookie", "session=" + sessionId + "; Path=/; HttpOnly; Secure; SameSite=Strict");

        auditService.logEvent("AUTH", "Staff " + username + " (" + user.getRole() + ") logged in successfully.");
        writeJsonResponse(exchange, 200, Map.of(
            "fullName", user.getFullName(),
            "username", user.getUsername(),
            "role", user.getRole()
        ));
    }

    private static void handleLogout(HttpExchange exchange) throws Exception {
        String sessionId = getSessionId(exchange);
        if (sessionId != null) {
            User user = activeSessions.remove(sessionId);
            if (user != null) {
                auditService.logEvent("AUTH", "Staff " + user.getUsername() + " logged out.");
            }
        }
        exchange.getResponseHeaders().add("Set-Cookie", "session=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; Secure");
        writeJsonResponse(exchange, 200, Map.of("message", "Logged out successfully."));
    }

    private static void handleGetSessions(HttpExchange exchange, Connection conn) throws Exception {
        List<ExamSession> sessions = examSessionRepository.findAll(conn);
        List<Map<String, Object>> sessionsWithInv = new ArrayList<>();
        for (ExamSession s : sessions) {
            Map<String, Object> sessionMap = new HashMap<>();
            sessionMap.put("sessionId", s.getSessionId());
            sessionMap.put("paperName", s.getPaperName());
            sessionMap.put("paperCode", s.getPaperCode());
            sessionMap.put("examDate", s.getExamDate().toString());
            sessionMap.put("startTime", s.getStartTime().toString());
            sessionMap.put("endTime", s.getEndTime().toString());
            sessionMap.put("invigilatorId", s.getInvigilatorId());
            if (s.getInvigilatorId() != null) {
                Optional<User> invOpt = userRepository.findById(conn, s.getInvigilatorId());
                invOpt.ifPresent(user -> sessionMap.put("invigilator", user));
            }
            sessionsWithInv.add(sessionMap);
        }
        writeJsonResponse(exchange, 200, sessionsWithInv);
    }

    private static void handleCreateSession(HttpExchange exchange, Connection conn, User currentUser) throws Exception {
        Map<String, Object> payload = readJsonRequest(exchange, Map.class);
        String paperName = (String) payload.get("paperName");
        String paperCode = (String) payload.get("paperCode");
        String examDateStr = (String) payload.get("examDate");
        String startTimeStr = (String) payload.get("startTime");
        String endTimeStr = (String) payload.get("endTime");

        if (paperName == null || paperName.trim().isEmpty() ||
            paperCode == null || paperCode.trim().isEmpty() ||
            examDateStr == null || startTimeStr == null || endTimeStr == null) {
            writeJsonResponse(exchange, 400, Map.of("error", "Missing required fields."));
            return;
        }

        LocalDate examDate = LocalDate.parse(examDateStr);
        LocalTime startTime = parseTime(startTimeStr);
        LocalTime endTime = parseTime(endTimeStr);

        ExamSession examSession = new ExamSession(
            paperName.trim(),
            paperCode.trim().toUpperCase(),
            examDate,
            startTime,
            endTime,
            currentUser.getUserId()
        );

        ExamSession saved = examSessionRepository.save(conn, examSession);
        
        // Auto-register all existing students for this newly created session
        List<Student> allStudents = studentRepository.findAll(conn);
        for (Student s : allStudents) {
            examSessionRepository.registerStudentForSession(conn, saved.getSessionId(), s.getStudentId());
        }

        auditService.logEvent("ATTENDANCE", "Created new exam session: " + saved.getPaperName() + " (" + saved.getPaperCode() + ")");
        writeJsonResponse(exchange, 201, saved);
    }

    private static void handleRegisterStudent(HttpExchange exchange, Connection conn) throws Exception {
        Map<String, Object> payload = readJsonRequest(exchange, Map.class);
        String fullName = (String) payload.get("fullName");
        String regNumber = (String) payload.get("regNumber");
        String program = (String) payload.get("program");
        Object yearObj = payload.get("year");
        Object feesClearedObj = payload.get("feesCleared");
        Object fingerPosObj = payload.get("fingerPosition");

        if (fullName == null || fullName.trim().isEmpty() ||
            regNumber == null || regNumber.trim().isEmpty() ||
            program == null || program.trim().isEmpty() ||
            yearObj == null || fingerPosObj == null) {
            writeJsonResponse(exchange, 400, Map.of("error", "Missing required fields: fullName, regNumber, program, year, and fingerPosition."));
            return;
        }

        String regNo = regNumber.trim().toUpperCase();
        if (studentRepository.existsByRegNumber(conn, regNo)) {
            writeJsonResponse(exchange, 409, Map.of("error", "Student with registration number " + regNo + " already exists."));
            return;
        }

        int year = Integer.parseInt(yearObj.toString());
        boolean feesCleared = feesClearedObj != null && Boolean.parseBoolean(feesClearedObj.toString());
        int fingerPosition = Integer.parseInt(fingerPosObj.toString());

        String status = scannerService.getScannerStatus();
        if ("DISCONNECTED".equals(status)) {
            writeJsonResponse(exchange, 503, Map.of("error", "Scanner not connected. Check Mantra MFS500 connection."));
            return;
        } else if ("ERROR".equals(status)) {
            writeJsonResponse(exchange, 500, Map.of("error", "Scanner in error state. Check hardware connection."));
            return;
        } else if ("CAPTURE_IN_PROGRESS".equals(status)) {
            writeJsonResponse(exchange, 423, Map.of("error", "Scanner is already in use. Please wait."));
            return;
        }

        byte[] capturedTemplate;
        try {
            capturedTemplate = scannerService.captureTemplate();
            if (capturedTemplate == null || capturedTemplate.length == 0) {
                writeJsonResponse(exchange, 500, Map.of("error", "No fingerprint detected. Please try again."));
                return;
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("timeout")) {
                writeJsonResponse(exchange, 408, Map.of("error", "Fingerprint capture timeout."));
            } else {
                writeJsonResponse(exchange, 500, Map.of("error", "Capture failure: " + msg));
            }
            return;
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            Student student = new Student(fullName.trim(), regNo, program.trim(), year, feesCleared);
            student.setEnrollmentDate(LocalDateTime.now());
            Student saved = studentRepository.save(conn, student);

            Fingerprint fingerprint = fingerprintService.enrollFingerprint(conn, saved, capturedTemplate, fingerPosition);

            // Auto-register the newly registered student for all existing sessions
            List<ExamSession> sessions = examSessionRepository.findAll(conn);
            for (ExamSession session : sessions) {
                examSessionRepository.registerStudentForSession(conn, session.getSessionId(), saved.getStudentId());
            }

            conn.commit();

            auditService.logEvent("REGISTER", "Registered student and enrolled fingerprint: " + saved.getFullName() + " (" + saved.getRegNumber() + ")");
            writeJsonResponse(exchange, 201, Map.of(
                "message", "Enrollment confirmation displayed. Student registered and fingerprint enrolled successfully.",
                "studentId", saved.getStudentId(),
                "regNumber", saved.getRegNumber(),
                "fullName", saved.getFullName(),
                "fingerprintId", fingerprint.getFingerprintId()
            ));
        } catch (Exception e) {
            conn.rollback();
            logger.error("Failed to register student and fingerprint atomically", e);
            writeJsonResponse(exchange, 500, Map.of("error", "Failed to save student details: " + e.getMessage()));
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    private static void handleGetStudent(HttpExchange exchange, Connection conn, String path) throws Exception {
        String regNumber = path.substring("/api/students/".length());
        Optional<Student> studentOpt = studentRepository.findByRegNumber(conn, regNumber.toUpperCase());
        if (studentOpt.isEmpty()) {
            writeJsonResponse(exchange, 404, Map.of("error", "Student not registered."));
            return;
        }
        writeJsonResponse(exchange, 200, studentOpt.get());
    }

    private static void handleEnrollFingerprint(HttpExchange exchange, Connection conn) throws Exception {
        Map<String, Object> payload = readJsonRequest(exchange, Map.class);
        String regNumber = (String) payload.get("regNumber");
        Object fingerPosObj = payload.get("fingerPosition");

        if (regNumber == null || regNumber.trim().isEmpty()) {
            writeJsonResponse(exchange, 400, Map.of("error", "Registration number is required."));
            return;
        }

        int fingerPosition = 1;
        if (fingerPosObj != null) {
            fingerPosition = Integer.parseInt(fingerPosObj.toString());
        }

        Optional<Student> studentOpt = studentRepository.findByRegNumber(conn, regNumber.trim().toUpperCase());
        if (studentOpt.isEmpty()) {
            writeJsonResponse(exchange, 404, Map.of("error", "Student not enrolled. Please register the student first."));
            return;
        }

        Student student = studentOpt.get();

        String status = scannerService.getScannerStatus();
        if ("DISCONNECTED".equals(status)) {
            writeJsonResponse(exchange, 503, Map.of("error", "Scanner not connected. Check Mantra MFS500 connection."));
            return;
        } else if ("ERROR".equals(status)) {
            writeJsonResponse(exchange, 500, Map.of("error", "Scanner in error state. Check hardware connection."));
            return;
        }

        try {
            byte[] capturedTemplate = scannerService.captureTemplate();
            if (capturedTemplate == null || capturedTemplate.length == 0) {
                writeJsonResponse(exchange, 500, Map.of("error", "No fingerprint detected. Please try again."));
                return;
            }

            Fingerprint fingerprint = fingerprintService.enrollFingerprint(conn, student, capturedTemplate, fingerPosition);
            auditService.logEvent("REGISTER", "Enrolled fingerprint for student " + student.getRegNumber() + " at position " + fingerPosition);

            writeJsonResponse(exchange, 200, Map.of(
                "message", "Enrollment confirmation displayed. Fingerprint enrolled successfully.",
                "studentId", student.getStudentId(),
                "regNumber", student.getRegNumber(),
                "fingerprintId", fingerprint.getFingerprintId()
            ));
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("timeout")) {
                writeJsonResponse(exchange, 408, Map.of("error", "Fingerprint capture timeout."));
            } else {
                writeJsonResponse(exchange, 500, Map.of("error", "Capture failure: " + msg));
            }
        }
    }

    private static void handleVerifyFingerprint(HttpExchange exchange, Connection conn) throws Exception {
        Map<String, Object> payload = readJsonRequest(exchange, Map.class);
        Object sessionIdObj = payload.get("sessionId");
        String regNumber = (String) payload.get("regNumber");

        if (sessionIdObj == null) {
            writeJsonResponse(exchange, 400, Map.of("error", "Session ID is required."));
            return;
        }

        Long sessionId = Long.parseLong(sessionIdObj.toString());
        Optional<ExamSession> sessionOpt = examSessionRepository.findById(conn, sessionId);
        if (sessionOpt.isEmpty()) {
            writeJsonResponse(exchange, 404, Map.of("error", "Exam session not found."));
            return;
        }
        ExamSession session = sessionOpt.get();

        String scannerStatus = scannerService.getScannerStatus();
        if ("DISCONNECTED".equals(scannerStatus)) {
            writeJsonResponse(exchange, 503, Map.of("error", "Scanner not connected. Check Mantra MFS500 connection."));
            return;
        } else if ("ERROR".equals(scannerStatus)) {
            writeJsonResponse(exchange, 500, Map.of("error", "Scanner in error state. Check hardware connection."));
            return;
        } else if ("CAPTURE_IN_PROGRESS".equals(scannerStatus)) {
            writeJsonResponse(exchange, 423, Map.of("error", "Scanner is already in use. Please wait."));
            return;
        }

        byte[] capturedTemplate;
        try {
            capturedTemplate = scannerService.captureTemplate();
            if (capturedTemplate == null || capturedTemplate.length == 0) {
                writeJsonResponse(exchange, 500, Map.of("error", "No fingerprint detected."));
                return;
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("timeout")) {
                writeJsonResponse(exchange, 408, Map.of("error", "Fingerprint capture timeout."));
            } else {
                writeJsonResponse(exchange, 500, Map.of("error", "Scanner error: " + msg));
            }
            return;
        }

        Student student = null;
        int matchScore = 0;

        if (regNumber != null && !regNumber.trim().isEmpty()) {
            // 1:1 match
            Optional<Student> studentOpt = studentRepository.findByRegNumber(conn, regNumber.trim().toUpperCase());
            if (studentOpt.isEmpty()) {
                auditService.logEvent("VERIFICATION", "Failed verification: Student not enrolled for reg: " + regNumber);
                writeJsonResponse(exchange, 404, Map.of("error", "Student not enrolled", "status", "Not Registered"));
                return;
            }
            student = studentOpt.get();
            FingerprintService.MatchResult result = fingerprintService.verifyFingerprint(conn, student, capturedTemplate);
            if (!result.isMatched()) {
                auditService.logEvent("VERIFICATION", "Failed match for student: " + student.getRegNumber());
                writeJsonResponse(exchange, 401, Map.of(
                    "error", "Fingerprint does not match.",
                    "status", "No Match",
                    "score", 0,
                    "studentName", student.getFullName(),
                    "regNumber", student.getRegNumber(),
                    "program", student.getProgram(),
                    "year", student.getYear(),
                    "feesCleared", student.getFeesCleared()
                ));
                return;
            }
            matchScore = result.getScore();
        } else {
            // 1:N identification (Scoped to Session!)
            FingerprintService.IdentifiedStudentResult result = fingerprintService.identifyStudent(conn, sessionId, capturedTemplate);
            if (result.getStudent() == null) {
                auditService.logEvent("VERIFICATION", "Failed 1:N identification: No match found.");
                writeJsonResponse(exchange, 401, Map.of("error", "No match found.", "status", "No Match", "score", 0));
                return;
            }
            student = result.getStudent();
            matchScore = result.getScore();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("studentName", student.getFullName());
        response.put("regNumber", student.getRegNumber());
        response.put("program", student.getProgram());
        response.put("year", student.getYear());
        response.put("matchScore", matchScore);
        response.put("feesCleared", student.getFeesCleared());

        boolean verified = false;
        boolean feesHold = false;

        if (!student.getFeesCleared()) {
            response.put("status", "Fees Not Cleared");
            response.put("message", "Verification Hold: Student has outstanding fees balance.");
            feesHold = true;
        } else {
            response.put("status", "Verified & Cleared");
            response.put("message", "Verification successful. Exam entry allowed.");
            verified = true;
        }

        try {
            AttendanceLog log = attendanceService.recordVerification(student, session, matchScore, verified, feesHold);
            response.put("logId", log.getLogId());
            response.put("verificationTime", log.getVerificationTime().toString());
            writeJsonResponse(exchange, 200, response);
        } catch (IllegalStateException e) {
            response.put("status", "Duplicate Attempt");
            response.put("error", "Duplicate attendance attempt: Student already checked in.");
            writeJsonResponse(exchange, 409, response);
        }
    }

    private static void handleGetAttendanceForSession(HttpExchange exchange, Connection conn, String path) throws Exception {
        String sessionIdStr = path.substring("/api/attendance/".length());
        Long sessionId = Long.parseLong(sessionIdStr);
        Optional<ExamSession> sessionOpt = examSessionRepository.findById(conn, sessionId);
        if (sessionOpt.isEmpty()) {
            writeJsonResponse(exchange, 404, Map.of("error", "Exam session not found."));
            return;
        }
        ExamSession session = sessionOpt.get();
        List<AttendanceLog> logs = attendanceService.getSessionLogs(session);

        List<Map<String, Object>> logsWithStudent = new ArrayList<>();
        for (AttendanceLog log : logs) {
            Optional<Student> studentOpt = studentRepository.findById(conn, log.getStudentId());
            if (studentOpt.isPresent()) {
                Map<String, Object> logMap = new HashMap<>();
                logMap.put("logId", log.getLogId());
                logMap.put("studentId", log.getStudentId());
                logMap.put("sessionId", log.getSessionId());
                logMap.put("verificationTime", log.getVerificationTime().toString());
                logMap.put("matchScore", log.getMatchScore());
                logMap.put("status", log.getStatus());
                logMap.put("student", studentOpt.get());
                logsWithStudent.add(logMap);
            }
        }

        Map<String, Object> stats = attendanceService.getSessionStats(session);

        Map<String, Object> sessionMap = new HashMap<>();
        sessionMap.put("sessionId", session.getSessionId());
        sessionMap.put("paperName", session.getPaperName());
        sessionMap.put("paperCode", session.getPaperCode());
        sessionMap.put("examDate", session.getExamDate().toString());
        sessionMap.put("startTime", session.getStartTime().toString());
        sessionMap.put("endTime", session.getEndTime().toString());
        sessionMap.put("invigilatorId", session.getInvigilatorId());
        if (session.getInvigilatorId() != null) {
            Optional<User> invOpt = userRepository.findById(conn, session.getInvigilatorId());
            invOpt.ifPresent(user -> sessionMap.put("invigilator", user));
        }

        writeJsonResponse(exchange, 200, Map.of(
            "session", sessionMap,
            "logs", logsWithStudent,
            "stats", stats
        ));
    }

    private static void handleExportAttendance(HttpExchange exchange, Connection conn, String path) throws Exception {
        String sessionIdStr = path.substring("/api/attendance/export/".length());
        Long sessionId = Long.parseLong(sessionIdStr);
        Optional<ExamSession> sessionOpt = examSessionRepository.findById(conn, sessionId);
        if (sessionOpt.isEmpty()) {
            writeJsonResponse(exchange, 404, Map.of("error", "Exam session not found."));
            return;
        }
        ExamSession session = sessionOpt.get();
        List<AttendanceLog> logs = attendanceLogRepository.findBySession(conn, session.getSessionId());

        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        String filename = String.format("attendance_%s_%s.csv", session.getPaperCode(), session.getExamDate().toString());
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.write('\ufeff'); // BOM
            writer.println("Registration Number,Full Name,Program,Year,Verification Time,Match Score,Status");

            for (AttendanceLog log : logs) {
                Optional<Student> studentOpt = studentRepository.findById(conn, log.getStudentId());
                if (studentOpt.isPresent()) {
                    Student s = studentOpt.get();
                    writer.println(String.format(
                        "\"%s\",\"%s\",\"%s\",%d,\"%s\",%d,\"%s\"",
                        escapeCsv(s.getRegNumber()),
                        escapeCsv(s.getFullName()),
                        escapeCsv(s.getProgram()),
                        s.getYear(),
                        log.getVerificationTime().toString(),
                        log.getMatchScore(),
                        log.getStatus()
                    ));
                }
            }
        }

        byte[] bytes = baos.toByteArray();
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeCsv(String text) {
        if (text == null) return "";
        return text.replace("\"", "\"\"");
    }

    private static void setupDatabaseSchema(Connection conn) throws Exception {
        boolean schemaExists = false;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1 FROM system_settings LIMIT 1");
            schemaExists = true;
        } catch (SQLException e) {
            // Table does not exist, need to run schema.sql
        }
        if (schemaExists) {
            return;
        }

        logger.info("Initializing database schema from schema.sql...");
        StringBuilder sb = new StringBuilder();
        try (InputStream is = Main.class.getResourceAsStream("/schema.sql")) {
            InputStream targetIs = is;
            if (targetIs == null) {
                targetIs = new FileInputStream("src/main/resources/schema.sql");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(targetIs, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("--") || line.trim().startsWith("#")) {
                        continue;
                    }
                    sb.append(line).append("\n");
                }
            }
        }

        String[] statements = sb.toString().split(";");
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                if (sql.trim().isEmpty()) continue;
                stmt.execute(sql.trim());
            }
        }
        logger.info("Database schema initialized successfully.");
    }

    private static void seedDatabase(Connection conn) throws SQLException {
        if (systemSettingRepository.count(conn) == 0) {
            systemSettingRepository.save(conn, new SystemSetting("matching_threshold", "48"));
            systemSettingRepository.save(conn, new SystemSetting("institution_name", "Uganda Institute of Information and Communications Technology"));
            systemSettingRepository.save(conn, new SystemSetting("academic_year", "2025/2026"));
            logger.info("Seeded system settings.");
        }

        User adminUser = null;
        if (userRepository.count(conn) == 0) {
            adminUser = new User("UICT Administrator", "admin", PasswordHasher.hashPassword("admin123"), "ADMIN");
            User invUser = new User("Naigaga Edith (Supervisor)", "invigilator", PasswordHasher.hashPassword("staff123"), "INVIGILATOR");
            userRepository.save(conn, adminUser);
            userRepository.save(conn, invUser);
            logger.info("Seeded default users.");
        } else {
            adminUser = userRepository.findByUsername(conn, "admin").orElse(null);
        }

        if (studentRepository.count(conn) == 0) {
            Student s1 = new Student("Nakato Sarah", "UG/2024/001", "Bachelor of Science in Information Technology (BSIT)", 2, true);
            Student s2 = new Student("Okello John", "UG/2024/002", "Bachelor of Science in Computer Engineering (BSCE)", 3, false);
            Student s3 = new Student("Kiiza Emmanuel", "UG/2024/003", "Diploma in Information Technology (DIT)", 1, true);
            Student s4 = new Student("Namubiru Gloria", "UG/2024/004", "Bachelor of Science in Information Technology (BSIT)", 2, false);
            
            s1.setEnrollmentDate(LocalDateTime.now());
            s2.setEnrollmentDate(LocalDateTime.now());
            s3.setEnrollmentDate(LocalDateTime.now());
            s4.setEnrollmentDate(LocalDateTime.now());

            studentRepository.save(conn, s1);
            studentRepository.save(conn, s2);
            studentRepository.save(conn, s3);
            studentRepository.save(conn, s4);
            logger.info("Seeded sample students.");
        }

        if (examSessionRepository.count(conn) == 0) {
            ExamSession es1 = new ExamSession("System Administration & Maintenance", "BIT 2201", LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(12, 0), adminUser != null ? adminUser.getUserId() : null);
            ExamSession es2 = new ExamSession("Computer Network Design", "BCE 3102", LocalDate.now(), LocalTime.of(14, 0), LocalTime.of(17, 0), adminUser != null ? adminUser.getUserId() : null);
            ExamSession es3 = new ExamSession("Introduction to Programming in Java", "DIT 1104", LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(12, 0), adminUser != null ? adminUser.getUserId() : null);
            
            examSessionRepository.save(conn, es1);
            examSessionRepository.save(conn, es2);
            examSessionRepository.save(conn, es3);
            logger.info("Seeded sample exam sessions.");

            Optional<Student> s1 = studentRepository.findByRegNumber(conn, "UG/2024/001");
            Optional<Student> s2 = studentRepository.findByRegNumber(conn, "UG/2024/002");
            Optional<Student> s3 = studentRepository.findByRegNumber(conn, "UG/2024/003");
            Optional<Student> s4 = studentRepository.findByRegNumber(conn, "UG/2024/004");

            if (s1.isPresent() && s4.isPresent()) {
                examSessionRepository.registerStudentForSession(conn, es1.getSessionId(), s1.get().getStudentId());
                examSessionRepository.registerStudentForSession(conn, es1.getSessionId(), s4.get().getStudentId());
            }
            if (s2.isPresent()) {
                examSessionRepository.registerStudentForSession(conn, es2.getSessionId(), s2.get().getStudentId());
            }
            if (s3.isPresent()) {
                examSessionRepository.registerStudentForSession(conn, es3.getSessionId(), s3.get().getStudentId());
            }
            logger.info("Seeded session registrations mapping.");
        }

        // Auto-register all existing students to all existing exam sessions for seamless 1:N verification
        List<Student> allStudents = studentRepository.findAll(conn);
        List<ExamSession> allSessions = examSessionRepository.findAll(conn);
        for (Student s : allStudents) {
            for (ExamSession es : allSessions) {
                examSessionRepository.registerStudentForSession(conn, es.getSessionId(), s.getStudentId());
            }
        }
    }

    private static String getSessionId(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String cookieHeader : cookies) {
            String[] pairs = cookieHeader.split(";");
            for (String pair : pairs) {
                String[] kv = pair.trim().split("=");
                if (kv.length == 2 && kv[0].equals("session")) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private static User getAuthenticatedUser(HttpExchange exchange) {
        String sessionId = getSessionId(exchange);
        if (sessionId == null) return null;
        return activeSessions.get(sessionId);
    }

    private static LocalTime parseTime(String timeStr) {
        timeStr = timeStr.trim();
        if (timeStr.length() == 5) {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } else {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }

    private static void writeJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static <T> T readJsonRequest(HttpExchange exchange, Class<T> clazz) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return objectMapper.readValue(is, clazz);
        }
    }
}
