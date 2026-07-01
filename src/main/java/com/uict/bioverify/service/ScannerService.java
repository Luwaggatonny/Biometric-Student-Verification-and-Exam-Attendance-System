package com.uict.bioverify.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.EnumSet;
import java.util.Properties;

import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NFinger;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.devices.NDevice;
import com.neurotec.devices.NDeviceManager;
import com.neurotec.devices.NDeviceType;
import com.neurotec.devices.NFingerScanner;
import com.neurotec.licensing.NLicense;
import com.neurotec.plugins.NDataFileManager;
import com.neurotec.plugins.NDataFile;

public class ScannerService {

    private static final Logger logger = LoggerFactory.getLogger(ScannerService.class);

    private final String licenseServer;
    private final int licensePort;
    private final String licenseComponents;

    private String scannerStatus = "DISCONNECTED"; // DISCONNECTED, INITIALIZING, READY, CAPTURE_IN_PROGRESS, SUCCESS, ERROR
    private String lastErrorMessage = "";

    private NBiometricClient biometricClient;
    private NDeviceManager deviceManager;

    public ScannerService(Properties props) {
        this.licenseServer = props.getProperty("bioverify.biometrics.license.server", "127.0.0.1");
        this.licensePort = Integer.parseInt(props.getProperty("bioverify.biometrics.license.port", "5000"));
        this.licenseComponents = props.getProperty("bioverify.biometrics.license.components", "Biometrics.FingerExtraction,Biometrics.FingerMatching");
        
        init();
    }

    public void init() {
        try {
            logger.info("Initializing Neurotechnology VeriFinger SDK...");
            setScannerStatus("INITIALIZING");

            // Configure NDataFileManager with model directories
            try {
                NDataFileManager manager = NDataFileManager.getInstance();
                String sdkDataPath = "C:\\Users\\ADMIN\\Downloads\\Neurotec_Biometric_2025_2_SDK_2026-04-03\\Neurotec_Biometric_2025_2_SDK\\Bin\\Data\\";
                File sdkDataDir = new File(sdkDataPath);
                if (sdkDataDir.exists() && sdkDataDir.isDirectory()) {
                    manager.addFromDirectory(sdkDataPath, false);
                    logger.info("Loaded biometric model files from SDK path: {}", sdkDataPath);
                } else {
                    logger.warn("Primary SDK biometric model path does not exist: {}", sdkDataPath);
                    // Fallback to Python SDK data path
                    String pythonSdkPath = "C:\\Users\\ADMIN\\Downloads\\Neurotec_Biometric_2025_1_Python_2025-10-31\\neurotec_extracted\\NeurotecSDK\\Bin\\Data\\";
                    File pythonSdkDir = new File(pythonSdkPath);
                    if (pythonSdkDir.exists() && pythonSdkDir.isDirectory()) {
                        manager.addFromDirectory(pythonSdkPath, false);
                        logger.info("Loaded biometric model files from fallback SDK path: {}", pythonSdkPath);
                    } else {
                        logger.error("Neither SDK biometric model path exists!");
                    }
                }

                // Also load from current root directory
                manager.addFromDirectory(".", false);
                logger.info("Loaded biometric model files from current directory");

                NDataFile[] files = manager.getAllFiles();
                logger.info("Total biometric model files loaded: {}", files.length);
                for (NDataFile file : files) {
                    logger.info("  - Loaded NDF: {}", file);
                }
            } catch (Throwable t) {
                logger.error("Failed to configure NDataFileManager", t);
            }

            // 1. Obtain licenses
            String[] components = licenseComponents.split(",");
            boolean licensed = true;
            for (String comp : components) {
                boolean result = NLicense.obtainComponents(licenseServer, licensePort, comp.trim());
                logger.info("License component {}: {}", comp, result ? "OBTAINED" : "FAILED");
                if (!result) {
                    licensed = false;
                }
            }

            if (!licensed) {
                throw new RuntimeException("One or more required VeriFinger license components were not obtained.");
            }

            // 2. Initialize Biometric Client setup
            biometricClient = new NBiometricClient();
            biometricClient.setUseDeviceManager(true);
            
            try {
                biometricClient.setProperty("Fingers.ScannerTimeout", 30000);
            } catch (Throwable e) {
                logger.warn("Fingers.ScannerTimeout not supported: {}", e.getMessage());
            }
            try {
                biometricClient.setProperty("Fingers.CaptureTimeout", 30000);
            } catch (Throwable e) {
                logger.warn("Fingers.CaptureTimeout not supported: {}", e.getMessage());
            }
            try {
                biometricClient.setProperty("Fingers.Timeout", 30000);
            } catch (Throwable e) {
                logger.warn("Fingers.Timeout not supported: {}", e.getMessage());
            }

            // 3. Connect to Device Manager
            deviceManager = biometricClient.getDeviceManager();
            deviceManager.setDeviceTypes(EnumSet.of(NDeviceType.FINGER_SCANNER));
            deviceManager.initialize();

            // 4. Initialize Biometric Client
            biometricClient.initialize();

            logger.info("VeriFinger SDK and Device Manager initialized successfully.");
            checkRealScanner();

            // Setup listener
            deviceManager.getDevices().addCollectionChangeListener(event -> {
                logger.info("Scanner device collection changed: {}", event.getAction());
                checkRealScanner();
            });

        } catch (Throwable t) {
            logger.error("SDK Initialization failed. Scanner offline.", t);
            lastErrorMessage = t.toString();
            setScannerStatus("ERROR");
        }
    }

    private void checkRealScanner() {
        try {
            if (biometricClient == null || biometricClient.isDisposed()) {
                logger.debug("checkRealScanner: Biometric client is null or disposed.");
                return;
            }
            if (deviceManager == null || deviceManager.isDisposed()) {
                logger.debug("checkRealScanner: Device manager is null or disposed.");
                return;
            }
            if (deviceManager.getDevices().size() > 0) {
                NDevice device = deviceManager.getDevices().get(0);
                logger.info("Scanner connected: {} ({})", device.getDisplayName(), device.getId());
                
                if (device instanceof NFingerScanner) {
                    NFingerScanner scanner = (NFingerScanner) device;
                    biometricClient.setFingerScanner(scanner);
                    logger.info("Scanner assigned to Biometric Client.");
                }
                
                setScannerStatus("READY");
            } else {
                logger.warn("No physical scanners detected.");
                biometricClient.setFingerScanner(null);
                setScannerStatus("DISCONNECTED");
            }
        } catch (Exception e) {
            logger.error("Error checking real scanner status", e);
            setScannerStatus("ERROR");
            lastErrorMessage = e.getMessage();
        }
    }

    public synchronized byte[] captureTemplate() {
        if ("CAPTURE_IN_PROGRESS".equals(getScannerStatus())) {
            throw new IllegalStateException("Scanner capture is already in progress.");
        }
        if ("ERROR".equals(getScannerStatus()) || "DISCONNECTED".equals(getScannerStatus())) {
            throw new RuntimeException("Scanner is not ready: " + getScannerStatus() + " (" + lastErrorMessage + ")");
        }

        NSubject subject = null;
        NFinger finger = null;
        NBiometricTask task = null;
        try {
            setScannerStatus("CAPTURE_IN_PROGRESS");
            logger.info("Fingerprint capture started on Mantra MFS500...");

            subject = new NSubject();
            finger = new NFinger();
            subject.getFingers().add(finger);

            // Synchronous capture & template extraction using task
            task = biometricClient.createTask(
                EnumSet.of(NBiometricOperation.CAPTURE, NBiometricOperation.CREATE_TEMPLATE),
                subject
            );
            biometricClient.performTask(task);
            NBiometricStatus status = task.getStatus();
            logger.info("Capture and template extraction completed with status: {}", status);

            if (status == NBiometricStatus.OK) {
                setScannerStatus("READY");
                return subject.getTemplateBuffer().toByteArray();
            } else {
                checkRealScanner();
                if (status == NBiometricStatus.CANCELED) {
                    throw new RuntimeException("Capture cancelled by operator.");
                } else if (status == NBiometricStatus.TIMEOUT) {
                    throw new RuntimeException("Capture timeout. Please place your finger on the scanner.");
                } else {
                    throw new RuntimeException("Capture failed: " + status.toString());
                }
            }
        } catch (Exception e) {
            logger.error("Scanner capture exception", e);
            checkRealScanner();
            if (!"READY".equals(getScannerStatus())) {
                setScannerStatus("ERROR");
                lastErrorMessage = e.getMessage();
            }
            throw new RuntimeException(e.getMessage());
        } finally {
            if (task != null) {
                task.dispose();
            }
            if (finger != null) {
                finger.dispose();
            }
            if (subject != null) {
                subject.dispose();
            }
        }
    }

    public void shutdown() {
        try {
            if (biometricClient != null) {
                biometricClient.dispose();
            }
            String[] components = licenseComponents.split(",");
            for (String comp : components) {
                NLicense.releaseComponents(comp.trim());
            }
            logger.info("SDK Biometric licenses released.");
        } catch (Exception e) {
            logger.error("Error during SDK cleanup", e);
        }
    }

    public String getScannerStatus() {
        return scannerStatus;
    }

    public void setScannerStatus(String scannerStatus) {
        this.scannerStatus = scannerStatus;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }
}
