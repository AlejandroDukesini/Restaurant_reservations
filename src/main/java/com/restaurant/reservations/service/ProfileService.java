package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OpsDtos.BrandingResponse;
import com.restaurant.reservations.dto.OpsDtos.ProfileRequest;
import com.restaurant.reservations.dto.OpsDtos.ProfileResponse;
import com.restaurant.reservations.model.MediaFile;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Perfil propio: cualquier miembro del personal (gerente incluido) cambia SU nombre visible,
 * SU foto y SU tema personal. Nada mas: ni correo, ni rol, ni permisos (sin escalada), y
 * siempre sobre el usuario del token, nunca por id (sin IDOR).
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final MediaService mediaService;
    private final BrandingService brandingService;
    private final AccessService access;
    private final RealtimeService realtime;

    public ProfileService(
        UserRepository userRepository,
        MediaService mediaService,
        BrandingService brandingService,
        AccessService access,
        RealtimeService realtime
    ) {
        this.userRepository = userRepository;
        this.mediaService = mediaService;
        this.brandingService = brandingService;
        this.access = access;
        this.realtime = realtime;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        return toResponse(access.user());
    }

    @Transactional
    public ProfileResponse update(ProfileRequest request) {
        User user = access.user();
        user.setName(request.name().strip());
        user.setTheme(request.theme() != null ? brandingService.write(request.theme()) : null);
        userRepository.save(user);
        changed(user);
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse setAvatar(MultipartFile file) {
        User user = access.user();
        MediaFile media = mediaService.storeOwn(file);
        String previous = user.getAvatarMediaId();
        user.setAvatarMediaId(media.getId());
        userRepository.saveAndFlush(user);
        mediaService.deleteInternal(previous);
        log.info("Foto de perfil actualizada por userId={}", user.getId());
        changed(user);
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse removeAvatar() {
        User user = access.user();
        String previous = user.getAvatarMediaId();
        user.setAvatarMediaId(null);
        userRepository.saveAndFlush(user);
        mediaService.deleteInternal(previous);
        changed(user);
        return toResponse(user);
    }

    private void changed(User user) {
        realtime.toUsers(access.restaurantId(), RealtimeService.TOPIC_BRANDING, null, List.of(user.getId()));
    }

    private ProfileResponse toResponse(User user) {
        BrandingResponse inherited = brandingService.inherited();
        return new ProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().name(),
            MediaService.url(user.getAvatarMediaId()), brandingService.read(user.getTheme()), inherited.theme(),
            inherited.groupName() != null ? "Grupo " + inherited.groupName() : "Restaurante");
    }
}
