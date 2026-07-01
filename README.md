BioVerify a Biometric Student Verification and Exam Attendance System

Problem & Solution

The Problem
Traditional manual student verification at the Uganda Institute of Information and Communications Technology (UICT) is slow, creates long queues, and relies on easily forged printed albums and paper fee-clearance permits. This manual approach is highly susceptible to exam impersonation and tuition fee clearance fraud, leading to administrative inefficiencies, academic and financial compromises.

The Solution
BioVerify solves these vulnerabilities by automating student identity verification and fees clearance checks using secure biometrics. Built specifically for UICT, it utilizes the Mantra MFS500 fingerprint scanner and the Neurotechnology VeriFinger SDK to establish a zero-trust, real-time security workflow.

Key features of the system include:
1. Biometric Authentication: Fast student identity matching using  fingerprint extraction to prevent exam impersonation.
2. Automated fees Clearance Checks: Real-time validation of outstanding tuition balances directly from the database, instantly flagging financial holds.
3. Secure Biometric Templates: Biometric fingerprint data is stored as encrypted binary templates using 256-bit AES encryption in a MySQL database.
4. Tamper-Proof Digital Audits: Automatic generation of attendance logs with precise timestamps, preventing duplicate records and post-exam tampering.

 Setup Instructions

This  guide explains how to install, configure, and run the BioVerify system on a local Windows.

Prerequisites
Before setting up the project, make sure you have the following installed:
1. Java 17 JDK that is OpenJDK 17 or Eclipse
2. Maven 3.x
3. MySQL Server a local like WampServer or XAMPP server
4. Mantra MFS500 Fingerprint Scanner and the physical hardware drivers installed
5. Neurotechnology VeriFinger SDK for licensing activation and native library DLLs

Step 1: Install Java 17 JDK
1. Download and run the Java 17 JDK installer for Windows.
2. Ensure you check the option to Add to PATH and Set JAVA_HOME environment variable during installation.
3. Verify that the correct version is active in your terminal with   java -version

Step 2: Set up the MySQL Database
1. Open your MySQL command-line client or administration interface in phpMyAdmin or MySQL Workbench.
2. Create a new database for the application:
   CREATE DATABASE bioverify;
3. The application tables and default settings will be created automatically using the schema defined in "src/main/resources/schema.sql"

Step 3: Install Mantra MFS500 Drivers and rd Services
1. Connect the Mantra MFS500 fingerprint scanner to a USB port.
2. Download the Mantra MFS500 Driver and Mantra MFS500 Client Service from the official Mantra Softech support page.
3. Install the downloaded packages as an Administrator.
4. Restart your computer if prompted, then verify the scanner's connection using the official Mantra Test Application.
   
Step 4: Neurotechnology Licensing Setup
All native JNI libraries (DLLs) and Java SDK dependencies (.jar files) are already included in the project directory
To run with a physical fingerprint scanner, you must activate the SDK license. Run the included "setup_licensing.bat" script as an Administrator to configure the local licensing service and launch the Activation Wizard in powershell ".\setup_licensing.bat"
Then follow the prompts in the Activation Wizard to activate your trial.

Step 5: Configure Application Properties
1. Open the configuration file application.properties in: file:///c:/Users/ADMIN/Desktop/project%20z/src/main/resources/application.properties.                
2. Adjust your MySQL database connection credentials to match your local setup:
   properties
   spring.datasource.username=your_mysql_username
   spring.datasource.password=your_mysql_password

Step 6: Compile and Run the Application in VS Code
The project is configured to run and compile using Visual Studio Code with the Extension Pack for Java.

1. Open the Project in VS Code:
   Open VS Code, select File , Open Folder, and choose the root directory of this project.
2. Install Java Extensions:
   Ensure that the official Extension Pack for Java is installed in VS Code.
   VS Code will automatically import the dependencies from "pom.xml" and compile the source code in the background.
3. Run the Project:
   Press F5 on your keyboard or navigate to the Run and Debug panel in the sidebar and click the play button.
   The application will automatically initialize the database schema and seed default staff accounts if the database is empty.
Step 7: Access and Test the Web Interface
1. Open your browser and navigate to:
   https://localhost:8444
2. Accept SSL Certificate Warning: Since the local HTTPS server uses a self-signed development certificate, the browser will display a security warning. Click Advanced and select Proceed to localhost.
3. Log in with Default Credentials:
   Username: admin
   Password:admin123
