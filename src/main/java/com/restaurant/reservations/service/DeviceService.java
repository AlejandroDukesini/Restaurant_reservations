package com.restaurant.reservations.service;

import com.restaurant.reservations.model.UserDevice;
import com.restaurant.reservations.repository.UserDeviceRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro de los dispositivos con los que cada usuario se conecta (sin datos sensibles). */
@Service
public class DeviceService {

    private final UserDeviceRepository deviceRepository;

    public DeviceService(UserDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public void touch(Long userId, String deviceKey, String userAgent) {
        if (deviceKey == null || !deviceKey.matches("[A-Za-z0-9-]{8,64}")) {
            return;
        }
        UserDevice device = deviceRepository.findByUserIdAndDeviceKey(userId, deviceKey)
            .orElseGet(() -> new UserDevice(userId, deviceKey));
        String ua = userAgent == null ? "" : userAgent;
        device.setUserAgent(ua.length() > 255 ? ua.substring(0, 255) : ua);
        device.setLabel(describe(ua));
        device.setLastSeenAt(LocalDateTime.now());
        deviceRepository.save(device);
    }

    /** Etiqueta legible: "Android · Chrome", "Windows · Edge", "Android · App instalada". */
    static String describe(String ua) {
        String os = ua.contains("Android") ? "Android"
            : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
            : ua.contains("Windows") ? "Windows"
            : ua.contains("Mac OS") ? "macOS"
            : ua.contains("Linux") ? "Linux" : "Otro";
        String browser = ua.contains("Edg/") ? "Edge"
            : ua.contains("Chrome/") ? "Chrome"
            : ua.contains("Firefox/") ? "Firefox"
            : ua.contains("Safari/") ? "Safari" : "Navegador";
        return os + " · " + browser;
    }
}
