package com.musicplayer.scamusica.manager;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

//public class DeviceUtil {
//    public static String getDeviceId() {
//        try {
//            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
//
//            while (interfaces.hasMoreElements()) {
//                NetworkInterface network = interfaces.nextElement();
//
//                // skip loopback and virtual interfaces
//                if (network.isLoopback() || network.isVirtual() || !network.isUp()) {
//                    continue;
//                }
//
//                byte[] mac = network.getHardwareAddress();
//                if (mac != null && mac.length > 0) {
//                    StringBuilder sb = new StringBuilder();
//                    for (byte b : mac) {
//                        sb.append(String.format("%02X:", b));
//                    }
//                    return sb.substring(0, sb.length() - 1); // remove trailing colon
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return "UNKNOWN_DEVICE";
//    }
//}
public class DeviceUtil {
    public static String getDeviceId() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            List<String> macList = new ArrayList<>();

            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();

                if (network.isLoopback() || network.isVirtual()) {
                    continue;
                }

                // Extra virtual interface filter
                String name = network.getName().toLowerCase();
                if (name.startsWith("docker") || name.startsWith("virbr")
                        || name.startsWith("vmnet") || name.startsWith("veth")
                        || name.startsWith("br-")  || name.startsWith("tun")
                        || name.startsWith("tap")) {
                    continue;
                }

                byte[] mac = network.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X:", b));
                    }
                    macList.add(sb.substring(0, sb.length() - 1));
                }
            }

            if (!macList.isEmpty()) {
                // Sort karo — order always same rahega
                Collections.sort(macList);
                // Sabhi MACs combine karo — zyada unique
                return String.join("|", macList);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "UNKNOWN_DEVICE";
    }
}
