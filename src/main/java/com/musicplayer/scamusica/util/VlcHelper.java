package com.musicplayer.scamusica.util;

import com.sun.jna.NativeLibrary;
import java.io.File;
import java.net.URL;

public class VlcHelper {

    private static boolean initialized = false;
    private static String resolvedVlcPath = null;

    /**
     * Automatically discovers, configures, and registers the VLC native library
     * and plugin directory across macOS, Windows, and Linux.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }

        String os = System.getProperty("os.name").toLowerCase();
        AppLogger.log("[VLC] Resolving VLC native library path for OS: " + os);

        File jarDir = getJarDirectory();
        AppLogger.log("[VLC] Jar directory: " + (jarDir != null ? jarDir.getAbsolutePath() : "null"));
        AppLogger.log("[VLC] user.dir: " + System.getProperty("user.dir"));

        if (os.contains("mac")) {
            initMacOS(jarDir);
        } else if (os.contains("win")) {
            initWindows(jarDir);
        } else {
            initLinux(jarDir);
        }

        initialized = true;
    }

    private static void initMacOS(File jarDir) {
        // Candidate paths on macOS in priority order:
        // 1. Bundled inside Scamusica.app: Contents/app/vlc/lib or Contents/app/vlc
        // 2. Bundled inside Scamusica.app: Contents/MacOS/lib or Contents/MacOS
        // 3. User directory vlc folder
        // 4. System /Applications/VLC.app (if official desktop VLC is installed)
        // 5. User ~/Applications/VLC.app
        // 6. Homebrew / Unix library paths

        String[] relativeBundledSubpaths = {
                "vlc/lib",
                "vlc",
                "../MacOS/lib",
                "../MacOS"
        };

        boolean found = false;

        // Check relative to JAR directory (inside Scamusica.app/Contents/app/)
        if (jarDir != null) {
            for (String sub : relativeBundledSubpaths) {
                File dir = new File(jarDir, sub);
                if (isValidVlcDir(dir, "libvlc.dylib")) {
                    applyVlcPath(dir);
                    found = true;
                    break;
                }
            }
        }

        // Check relative to user.dir
        if (!found) {
            String userDir = System.getProperty("user.dir");
            if (userDir != null) {
                for (String sub : relativeBundledSubpaths) {
                    File dir = new File(userDir, sub);
                    if (isValidVlcDir(dir, "libvlc.dylib")) {
                        applyVlcPath(dir);
                        found = true;
                        break;
                    }
                }
            }
        }

        // Check system desktop VLC installation
        if (!found) {
            String[] systemPaths = {
                    "/Applications/VLC.app/Contents/MacOS/lib",
                    "/Applications/VLC.app/Contents/MacOS",
                    System.getProperty("user.home") + "/Applications/VLC.app/Contents/MacOS/lib",
                    System.getProperty("user.home") + "/Applications/VLC.app/Contents/MacOS",
                    "/opt/homebrew/lib",
                    "/usr/local/lib"
            };

            for (String sysPath : systemPaths) {
                File dir = new File(sysPath);
                if (isValidVlcDir(dir, "libvlc.dylib")) {
                    applyVlcPath(dir);
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            AppLogger.log("[VLC] WARNING: libvlc.dylib not found in any candidate path on macOS.");
        }
    }

    private static void initWindows(File jarDir) {
        String[] candidates = {
                jarDir != null ? new File(jarDir, "vlc").getAbsolutePath() : null,
                System.getProperty("user.dir") + File.separator + "vlc"
        };

        for (String c : candidates) {
            if (c != null) {
                File dir = new File(c);
                if (isValidVlcDir(dir, "libvlc.dll")) {
                    applyVlcPath(dir);
                    return;
                }
            }
        }

        // Fallback default
        String fallback = System.getProperty("user.dir") + File.separator + "vlc";
        System.setProperty("jna.library.path", fallback);
        AppLogger.log("[VLC] Windows fallback jna.library.path=" + fallback);
    }

    private static void initLinux(File jarDir) {
        String[] candidates = {
                jarDir != null ? new File(jarDir, "vlc").getAbsolutePath() : null,
                System.getProperty("user.dir") + File.separator + "vlc",
                "/usr/lib",
                "/usr/lib/x86_64-linux-gnu",
                "/usr/lib/aarch64-linux-gnu",
                "/usr/local/lib"
        };

        for (String c : candidates) {
            if (c != null) {
                File dir = new File(c);
                if (isValidVlcDir(dir, "libvlc.so") || isValidVlcDir(dir, "libvlc.so.5")) {
                    applyVlcPath(dir);
                    return;
                }
            }
        }
    }

    private static boolean isValidVlcDir(File dir, String testFileName) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }
        File testFile = new File(dir, testFileName);
        return testFile.exists();
    }

    private static void applyVlcPath(File vlcDir) {
        resolvedVlcPath = vlcDir.getAbsolutePath();
        AppLogger.log("[VLC] Configuring VLC library path: " + resolvedVlcPath);

        // 1. JNA NativeLibrary search path
        try {
            NativeLibrary.addSearchPath("vlc", resolvedVlcPath);
            NativeLibrary.addSearchPath("vlccore", resolvedVlcPath);
        } catch (Throwable t) {
            AppLogger.log("[VLC] Failed to add search path via NativeLibrary: " + t.getMessage());
        }

        // 2. System jna.library.path property
        String existing = System.getProperty("jna.library.path");
        if (existing == null || existing.isEmpty()) {
            System.setProperty("jna.library.path", resolvedVlcPath);
        } else {
            System.setProperty("jna.library.path", resolvedVlcPath + File.pathSeparator + existing);
        }

        // 3. Locate plugins directory and set VLC_PLUGIN_PATH
        File plugins = new File(vlcDir, "plugins");
        if (!plugins.exists() && vlcDir.getParentFile() != null) {
            plugins = new File(vlcDir.getParentFile(), "plugins");
        }

        if (plugins.exists()) {
            System.setProperty("VLC_PLUGIN_PATH", plugins.getAbsolutePath());
            AppLogger.log("[VLC] Set VLC_PLUGIN_PATH: " + plugins.getAbsolutePath());
        } else {
            AppLogger.log("[VLC] Plugins directory not found near: " + resolvedVlcPath);
        }
    }

    private static File getJarDirectory() {
        try {
            URL url = VlcHelper.class.getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                File file = new File(url.toURI());
                return file.isDirectory() ? file : file.getParentFile();
            }
        } catch (Exception e) {
            AppLogger.log("[VLC] Could not determine JAR directory: " + e.getMessage());
        }
        return null;
    }

    public static String getResolvedVlcPath() {
        return resolvedVlcPath;
    }
}
