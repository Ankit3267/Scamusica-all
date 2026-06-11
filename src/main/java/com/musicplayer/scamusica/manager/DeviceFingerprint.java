package com.musicplayer.scamusica.manager;

import com.musicplayer.scamusica.util.AppLogger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class DeviceFingerprint {
    public static String getFingerprint() {
        try {
            AppLogger.log("[FINGERPRINT] Starting device fingerprint generation...");
            
            // 1. Try to get a true OS-level Machine UUID
            String osUuid = getOsLevelMachineId();
            AppLogger.log("[FINGERPRINT] OS-level Machine UUID: " + osUuid);
            
            // 2. Gather fallback attributes
            String mac = DeviceUtil.getDeviceId();
            AppLogger.log("[FINGERPRINT] Fallback MAC Address: " + mac);
            
            String os = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
            String osVer = System.getProperty("os.version");
            AppLogger.log("[FINGERPRINT] OS Info: " + os + " | " + osArch + " | " + osVer);
            
            String userName = System.getProperty("user.name");
            
            String cpu = System.getenv("PROCESSOR_IDENTIFIER");
            if (cpu == null) {
                cpu = readLinuxCpuInfo();
            }
            if (cpu == null) {
                cpu = runCommand("sysctl -n machdep.cpu.brand_string");
            }
            if (cpu == null) cpu = "UNKNOWN_CPU";
            AppLogger.log("[FINGERPRINT] CPU Info: " + cpu);
            
            String diskSerial = getDiskSerial();
            AppLogger.log("[FINGERPRINT] Disk Serial: " + diskSerial);
            
            // 3. Combine logic
            String raw;
            if (osUuid != null && !osUuid.equals("UNKNOWN_UUID") && !osUuid.trim().isEmpty()) {
                AppLogger.log("[FINGERPRINT] Using OS UUID as primary source of truth for stable ID.");
                raw = osUuid; // Highly stable
            } else {
                AppLogger.log("[FINGERPRINT] OS UUID not found. Using fallback combination.");
                raw = mac + os + osArch + osVer + cpu + userName + diskSerial;
            }
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            
            String finalId = hex.toString();
            AppLogger.log("[FINGERPRINT] Final Generated Device ID: " + finalId);
            return finalId;

        } catch (Exception e) {
            e.printStackTrace();
            AppLogger.log("[FINGERPRINT] ERROR generating fingerprint: " + e.getMessage());
            return "UNKNOWN";
        }
    }

    private static String getOsLevelMachineId() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows
                Process p = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "wmic csproduct get uuid"});
                byte[] out = p.getInputStream().readAllBytes();
                String result = new String(out).replaceAll("\\s+", "").toLowerCase().replace("uuid", "");
                if (result.length() > 0) return result;
            } else if (os.contains("mac")) {
                // Mac
                Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", "ioreg -rd1 -c IOPlatformExpertDevice | grep IOPlatformUUID | awk '{print $3}' | sed 's/\"//g'"});
                byte[] out = p.getInputStream().readAllBytes();
                String result = new String(out).trim();
                if (result.length() > 0) return result;
            } else if (os.contains("linux")) {
                // Linux
                java.io.File file = new java.io.File("/etc/machine-id");
                if (file.exists()) {
                    try (java.util.Scanner sc = new java.util.Scanner(file)) {
                        if (sc.hasNextLine()) return sc.nextLine().trim();
                    }
                }
                file = new java.io.File("/var/lib/dbus/machine-id");
                if (file.exists()) {
                    try (java.util.Scanner sc = new java.util.Scanner(file)) {
                        if (sc.hasNextLine()) return sc.nextLine().trim();
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.log("[FINGERPRINT] Error getting OS UUID: " + e.getMessage());
        }
        return "UNKNOWN_UUID";
    }

    // Linux: /proc/cpuinfo se "model name" padhna
    private static String readLinuxCpuInfo() {
        try {
            java.io.File file = new java.io.File("/proc/cpuinfo");
            if (!file.exists()) return null;

            try (java.util.Scanner sc = new java.util.Scanner(file)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.startsWith("model name")) {
                        return line.split(":")[1].trim(); 
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    // Command run karna (Mac ke liye)
    private static String runCommand(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            byte[] bytes = p.getInputStream().readAllBytes();
            return new String(bytes).trim();
        } catch (Exception e) { return null; }
    }

    // Disk serial number — Windows/Linux/Mac
    private static String getDiskSerial() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows
                Process p = Runtime.getRuntime().exec(
                        new String[]{"cmd", "/c", "wmic diskdrive get SerialNumber"}
                );
                byte[] out = p.getInputStream().readAllBytes();
                return new String(out).replaceAll("\\s+", "").replace("SerialNumber", "");

            } else if (os.contains("linux")) {
                // Linux
                Process p = Runtime.getRuntime().exec(
                        new String[]{"bash", "-c", "lsblk -d -o SERIAL 2>/dev/null | tail -1"}
                );
                byte[] out = p.getInputStream().readAllBytes();
                return new String(out).trim();

            } else if (os.contains("mac")) {
                // Mac
                Process p = Runtime.getRuntime().exec(
                        new String[]{"bash", "-c",
                                "system_profiler SPStorageDataType | grep 'Serial Number' | head -1"}
                );
                byte[] out = p.getInputStream().readAllBytes();
                return new String(out).trim();
            }
        } catch (Exception e) { /* ignore */ }
        return "UNKNOWN_DISK";
    }
}
