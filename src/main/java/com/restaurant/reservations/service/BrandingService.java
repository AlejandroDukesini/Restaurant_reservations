package com.restaurant.reservations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservations.dto.OpsDtos.BrandingResponse;
import com.restaurant.reservations.dto.OpsDtos.GroupBrandingRequest;
import com.restaurant.reservations.dto.OpsDtos.RestaurantBrandingRequest;
import com.restaurant.reservations.dto.ThemeSettings;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.StaffGroup;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.StaffGroupRepository;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identidad visual. Resolucion para un usuario: el tema del primer grupo (por antiguedad) al
 * que pertenece que tenga tema propio; si no, el del restaurante; si no, el tema por defecto.
 * Asi todos los dispositivos de un grupo reciben la misma configuracion, y la de un grupo nunca
 * llega a miembros de otro.
 */
@Service
public class BrandingService {

    private static final Logger log = LoggerFactory.getLogger(BrandingService.class);

    private final RestaurantRepository restaurantRepository;
    private final StaffGroupRepository groupRepository;
    private final MediaService mediaService;
    private final AccessService access;
    private final RealtimeService realtime;
    private final ObjectMapper objectMapper;

    public BrandingService(
        RestaurantRepository restaurantRepository,
        StaffGroupRepository groupRepository,
        MediaService mediaService,
        AccessService access,
        RealtimeService realtime,
        ObjectMapper objectMapper
    ) {
        this.restaurantRepository = restaurantRepository;
        this.groupRepository = groupRepository;
        this.mediaService = mediaService;
        this.access = access;
        this.realtime = realtime;
        this.objectMapper = objectMapper;
    }

    /** Tema efectivo del usuario actual. El gerente puede previsualizar el de un grupo. */
    public BrandingResponse current(Long previewGroupId) {
        Restaurant restaurant = restaurant();
        if (previewGroupId != null) {
            access.requireManager();
            StaffGroup group = group(previewGroupId);
            return forGroup(restaurant, group);
        }
        BrandingResponse base = baseFor(restaurant);
        ThemeSettings personal = read(access.user().getTheme());
        if (personal != null) {
            return new BrandingResponse(base.restaurantName(), personal, base.logoUrl(), base.coverUrl(), "personal",
                base.groupId(), base.groupName());
        }
        return base;
    }

    /** Identidad que hereda el usuario actual (grupo o restaurante), sin su tema personal. */
    public BrandingResponse inherited() {
        return baseFor(restaurant());
    }

    /** Tema del grupo (o restaurante) sin el tema personal: lo que el usuario "hereda". */
    private BrandingResponse baseFor(Restaurant restaurant) {
        if (!access.isManager()) {
            List<StaffGroup> groups = groupRepository.findActiveByMember(access.userId());
            for (StaffGroup group : groups) {
                if (group.getTheme() != null || group.getLogoMediaId() != null) {
                    return forGroup(restaurant, group);
                }
            }
        }
        return forRestaurant(restaurant);
    }

    public BrandingResponse restaurantBranding() {
        return forRestaurant(restaurant());
    }

    @Transactional
    public BrandingResponse updateRestaurant(RestaurantBrandingRequest request) {
        access.requireManager();
        Restaurant restaurant = restaurant();
        String name = request.restaurantName().strip();
        if (!name.equals(restaurant.getName())) {
            boolean taken = restaurantRepository.findAll().stream()
                .anyMatch(r -> !Objects.equals(r.getId(), restaurant.getId()) && r.getName().equalsIgnoreCase(name));
            if (taken) {
                throw new ValidationBusinessException("Restaurant name already exists");
            }
            restaurant.setName(name);
        }
        restaurant.setTheme(request.theme() != null ? write(request.theme()) : null);
        restaurant.setLogoMediaId(mediaService.requireOwned(request.logoMediaId()));
        restaurant.setCoverMediaId(mediaService.requireOwned(request.coverMediaId()));
        restaurantRepository.save(restaurant);
        log.info("Personalizacion del restaurante {} actualizada por userId={}", restaurant.getId(), access.userId());
        realtime.broadcast(restaurant.getId(), RealtimeService.TOPIC_BRANDING);
        return forRestaurant(restaurant);
    }

    /** theme = null restablece el grupo al tema del restaurante. */
    @Transactional
    public BrandingResponse updateGroup(Long groupId, GroupBrandingRequest request) {
        access.requireManager();
        StaffGroup group = group(groupId);
        group.setTheme(request.theme() != null ? write(request.theme()) : null);
        group.setLogoMediaId(mediaService.requireOwned(request.logoMediaId()));
        groupRepository.save(group);
        realtime.broadcast(group.getRestaurantId(), RealtimeService.TOPIC_BRANDING);
        return forGroup(restaurant(), group);
    }

    public ThemeSettings read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ThemeSettings.class);
        } catch (JsonProcessingException ex) {
            log.warn("Tema guardado ilegible; se usa el tema por defecto");
            return null;
        }
    }

    /** Valida (formato y contraste) y serializa un tema. */
    String write(ThemeSettings theme) {
        ThemeSettings normalized = theme.normalized();
        normalized.requireReadable();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new ValidationBusinessException("Invalid theme");
        }
    }

    private BrandingResponse forRestaurant(Restaurant restaurant) {
        ThemeSettings theme = read(restaurant.getTheme());
        return new BrandingResponse(restaurant.getName(), theme != null ? theme : ThemeSettings.defaults(),
            MediaService.url(restaurant.getLogoMediaId()), MediaService.url(restaurant.getCoverMediaId()),
            theme != null ? "restaurant" : "default", null, null);
    }

    private BrandingResponse forGroup(Restaurant restaurant, StaffGroup group) {
        ThemeSettings groupTheme = read(group.getTheme());
        ThemeSettings restaurantTheme = read(restaurant.getTheme());
        ThemeSettings theme = groupTheme != null ? groupTheme
            : restaurantTheme != null ? restaurantTheme : ThemeSettings.defaults();
        String logo = group.getLogoMediaId() != null ? group.getLogoMediaId() : restaurant.getLogoMediaId();
        return new BrandingResponse(restaurant.getName(), theme, MediaService.url(logo),
            MediaService.url(restaurant.getCoverMediaId()), "group", group.getId(), group.getName());
    }

    private Restaurant restaurant() {
        return restaurantRepository.findById(access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
    }

    private StaffGroup group(Long id) {
        return groupRepository.findByIdAndRestaurantIdAndActiveTrue(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
    }
}
