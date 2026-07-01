@echo off
:: Check for administrator permissions
openfiles >nul 2>&1
if %errorlevel% neq 0 (
    echo =======================================================
    echo ERROR: This script must be run as an Administrator!
    echo Please right-click this file and select 'Run as administrator'.
    echo =======================================================
    pause
    exit /b 1
)

echo [1/4] Stopping the old Neurotechnology service...
net stop Neurotechnology >nul 2>&1

echo [2/4] Uninstalling the old service...
"C:\Users\ADMIN\Downloads\Neurotec_Biometric_2025_1_Python_2025-10-31\Activation\Windows\pg.exe" -uninstall

echo [3/4] Installing the new service from the 2025_2 SDK...
"C:\Users\ADMIN\Downloads\Neurotec_Biometric_2025_2_SDK_2026-04-03\Neurotec_Biometric_2025_2_SDK\Bin\Win64_x64\Activation\pg.exe" -install

echo [4/4] Starting the new Neurotechnology service...
net start Neurotechnology

echo =======================================================
echo Service successfully updated to the new SDK!
echo.
echo Now launching the Activation Wizard...
echo Please use it to activate the trial or local license.
echo =======================================================
start "" "C:\Users\ADMIN\Downloads\Neurotec_Biometric_2025_2_SDK_2026-04-03\Neurotec_Biometric_2025_2_SDK\Bin\Win64_x64\Activation\ActivationWizard.exe"

pause
