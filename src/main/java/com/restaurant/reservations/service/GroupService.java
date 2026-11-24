package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OpsDtos.DeviceResponse;
import com.restaurant.reservations.dto.OpsDtos.GroupActivity;
import com.restaurant.reservations.dto.OpsDtos.GroupMembersRequest;
import com.restaurant.reservations.dto.OpsDtos.GroupRequest;
import com.restaurant.reservations.dto.OpsDtos.GroupResponse;
import com.restaurant.reservations.dto.OpsDtos.MemberSummary;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.ChatMessage;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.StaffGroup;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.ChatMessageRepository;
import com.restaurant.reservations.repository.StaffGroupRepository;
import com.restaurant.reservations.repository.UserDeviceRepository;
import com.restaurant.reservations.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Grupos de trabajo: solo el gerente los administra; los empleados ven los suyos. */
@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);
    private static final Set<Role> MEMBER_ROLES = Set.of(Role.ADMIN, Role.EMPLOYEE, Role.COOK, Role.DELIVERY);

    private final StaffGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final UserDeviceRepository deviceRepository;
    private final ChatMessageRepository messageRepository;
    private final BrandingService brandingService;
    private final AccessService access;
    private final RealtimeService realtime;

    public GroupService(
        StaffGroupRepository groupRepository,
        UserRepository userRepository,
        UserDeviceRepository deviceRepository,
        ChatMessageRepository messageRepository,
        BrandingService brandingService,
        AccessService access,
        RealtimeService realtime
    ) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.messageRepository = messageRepository;
        this.brandingService = brandingService;
        this.access = access;
        this.realtime = realtime;
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list() {
        List<StaffGroup> groups = access.isManager()
            ? groupRepository.findByRestaurantIdAndActiveTrueOrderByNameAsc(access.restaurantId())
            : groupRepository.findActiveByMember(access.userId());
        return groups.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse get(Long id) {
        StaffGroup group = find(id);
        if (!access.isManager() && !group.hasMember(access.userId())) {
            throw new ResourceNotFoundException("Group not found");
        }
        return toResponse(group);
    }

    @Transactional
    public GroupResponse create(GroupRequest request) {
        access.requireManager();
        String name = request.name().strip();
        if (groupRepository.existsActiveName(access.restaurantId(), name, null)) {
            throw new ValidationBusinessException("A group with that name already exists");
        }
        StaffGroup group = new StaffGroup(access.restaurantId(), name);
        group.setDescription(blankToNull(request.description()));
        group.setChatEnabled(request.chatEnabled() == null || request.chatEnabled());
        groupRepository.save(group);
        log.info("Grupo {} creado por userId={}", group.getId(), access.userId());
        realtime.broadcast(group.getRestaurantId(), RealtimeService.TOPIC_GROUPS);
        return toResponse(group);
    }

    @Transactional
    public GroupResponse update(Long id, GroupRequest request) {
        access.requireManager();
        StaffGroup group = find(id);
        String name = request.name().strip();
        if (groupRepository.existsActiveName(access.restaurantId(), name, id)) {
            throw new ValidationBusinessException("A group with that name already exists");
        }
        group.setName(name);
        group.setDescription(blankToNull(request.description()));
        if (request.chatEnabled() != null) {
            group.setChatEnabled(request.chatEnabled());
        }
        realtime.broadcast(group.getRestaurantId(), RealtimeService.TOPIC_GROUPS);
        return toResponse(groupRepository.save(group));
    }

    /**
     * Eliminacion segura: el grupo se archiva (deja de existir para la operacion y sus
     * miembros pierden su tema y canal) pero se conserva el historial de mensajes.
     */
    @Transactional
    public void delete(Long id) {
        access.requireManager();
        StaffGroup group = find(id);
        group.setActive(false);
        group.getMembers().clear();
        groupRepository.save(group);
        log.info("Grupo {} archivado por userId={}", id, access.userId());
        realtime.broadcast(group.getRestaurantId(), RealtimeService.TOPIC_GROUPS);
        realtime.broadcast(group.getRestaurantId(), RealtimeService.TOPIC_BRANDING);
    }

    /** Reemplaza los integrantes. Solo usuarios activos del mismo restaurante. */
    @Transactional
    public GroupResponse setMembers(Long id, GroupMembersRequest request) {
        access.requireManager();
        StaffGroup group = find(id);
        Long restaurantId = access.restaurantId();
        Set<User> members = new LinkedHashSet<>();
        for (Long userId : new LinkedHashSet<>(request.userIds())) {
            User user = userRepository.findByIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
            if (!user.isActive() || !MEMBER_ROLES.contains(user.getRole())) {
                throw new ValidationBusinessException("Only active staff can be group members");
            }
            members.add(user);
        }
        group.getMembers().clear();
        group.getMembers().addAll(members);
        groupRepository.save(group);
        realtime.broadcast(restaurantId, RealtimeService.TOPIC_GROUPS);
        realtime.broadcast(restaurantId, RealtimeService.TOPIC_BRANDING);
        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public GroupActivity activity(Long id) {
        access.requireManager();
        StaffGroup group = find(id);
        List<ChatMessage> last = messageRepository.findPage(group.getId(), null, PageRequest.of(0, 1));
        long total = messageRepository.countByGroupIdAndIdGreaterThan(group.getId(), 0L);
        Map<Long, String> names = group.getMembers().stream().collect(Collectors.toMap(User::getId, User::getName));
        List<DeviceResponse> devices = names.isEmpty() ? List.of()
            : deviceRepository.findByUserIdInOrderByLastSeenAtDesc(names.keySet()).stream()
                .map(d -> new DeviceResponse(d.getUserId(), names.get(d.getUserId()), d.getLabel(), d.getFirstSeenAt(),
                    d.getLastSeenAt()))
                .toList();
        LocalDateTime lastAt = last.isEmpty() ? null : last.get(0).getCreatedAt();
        return new GroupActivity(group.getId(), total, lastAt, devices);
    }

    StaffGroup find(Long id) {
        return groupRepository.findByIdAndRestaurantIdAndActiveTrue(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
    }

    private GroupResponse toResponse(StaffGroup group) {
        List<MemberSummary> members = group.getMembers().stream()
            .sorted(Comparator.comparing(User::getName))
            .map(u -> new MemberSummary(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive()))
            .toList();
        return new GroupResponse(group.getId(), group.getName(), group.getDescription(), group.isChatEnabled(), members,
            brandingService.read(group.getTheme()), MediaService.url(group.getLogoMediaId()), group.getLogoMediaId(),
            group.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static <T> Map<Long, T> index(List<T> items, Function<T, Long> key) {
        return items.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
