package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OpsDtos.ChatGroupSummary;
import com.restaurant.reservations.dto.OpsDtos.ChatMessageRequest;
import com.restaurant.reservations.dto.OpsDtos.ChatMessageResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.StateConflictException;
import com.restaurant.reservations.model.ChatMessage;
import com.restaurant.reservations.model.ChatRead;
import com.restaurant.reservations.model.StaffGroup;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.ChatMessageRepository;
import com.restaurant.reservations.repository.ChatReadRepository;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.StaffGroupRepository;
import com.restaurant.reservations.repository.TableRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mensajeria por grupos. Solo leen y escriben los integrantes del grupo (y el gerente, que
 * administra la comunicacion). Un empleado que pide un grupo ajeno recibe 404.
 *
 * El cuerpo se guarda y se devuelve como texto plano; el SPA lo pinta como texto (React
 * escapa), nunca como HTML, asi que un mensaje no puede inyectar scripts (XSS).
 */
@Service
public class ChatService {

    private static final int PAGE = 50;

    private final StaffGroupRepository groupRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatReadRepository readRepository;
    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final AccessService access;
    private final RealtimeService realtime;

    public ChatService(
        StaffGroupRepository groupRepository,
        ChatMessageRepository messageRepository,
        ChatReadRepository readRepository,
        TableRepository tableRepository,
        OrderRepository orderRepository,
        AccessService access,
        RealtimeService realtime
    ) {
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
        this.readRepository = readRepository;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.access = access;
        this.realtime = realtime;
    }

    @Transactional(readOnly = true)
    public List<ChatGroupSummary> summaries() {
        List<StaffGroup> groups = access.isManager()
            ? groupRepository.findByRestaurantIdAndActiveTrueOrderByNameAsc(access.restaurantId())
            : groupRepository.findActiveByMember(access.userId());
        List<ChatGroupSummary> result = new ArrayList<>();
        for (StaffGroup group : groups) {
            long lastRead = readRepository.findByGroupIdAndUserId(group.getId(), access.userId())
                .map(ChatRead::getLastReadId).orElse(0L);
            long unread = messageRepository.countByGroupIdAndIdGreaterThan(group.getId(), lastRead);
            List<ChatMessage> last = messageRepository.findPage(group.getId(), null, PageRequest.of(0, 1));
            ChatMessage lastMessage = last.isEmpty() ? null : last.get(0);
            result.add(new ChatGroupSummary(group.getId(), group.getName(), group.isChatEnabled(), unread,
                lastMessage != null ? preview(lastMessage.getBody()) : null,
                lastMessage != null ? lastMessage.getSender().getName() : null,
                lastMessage != null ? lastMessage.getCreatedAt() : null,
                group.getMembers().size()));
        }
        return result;
    }

    /** beforeId pagina hacia atras; afterId trae solo lo nuevo (recuperacion tras reconectar). */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> messages(Long groupId, Long beforeId, Long afterId) {
        StaffGroup group = accessible(groupId);
        List<ChatMessage> page;
        if (afterId != null) {
            page = messageRepository.findAfter(group.getId(), afterId, PageRequest.of(0, 200));
        } else {
            page = new ArrayList<>(messageRepository.findPage(group.getId(), beforeId, PageRequest.of(0, PAGE)));
            Collections.reverse(page);
        }
        Long me = access.userId();
        return page.stream().map(m -> toResponse(m, me)).toList();
    }

    @Transactional
    public ChatMessageResponse post(Long groupId, ChatMessageRequest request) {
        StaffGroup group = accessible(groupId);
        if (!group.isChatEnabled()) {
            throw new StateConflictException("Messaging is disabled for this group");
        }
        String refType = request.refType();
        Long refId = request.refId();
        if (refType != null || refId != null) {
            validateReference(refType, refId);
        }
        User sender = access.user();
        ChatMessage message = messageRepository.save(new ChatMessage(access.restaurantId(), group.getId(), sender,
            request.body().strip(), refType, refId));
        markReadUpTo(group.getId(), message.getId());
        List<Long> members = group.getMembers().stream().map(User::getId).toList();
        realtime.toUsers(group.getRestaurantId(), RealtimeService.TOPIC_CHAT, group.getId(), members);
        return toResponse(message, sender.getId());
    }

    @Transactional
    public void markRead(Long groupId) {
        StaffGroup group = accessible(groupId);
        List<ChatMessage> last = messageRepository.findPage(group.getId(), null, PageRequest.of(0, 1));
        if (!last.isEmpty()) {
            markReadUpTo(group.getId(), last.get(0).getId());
        }
    }

    private void markReadUpTo(Long groupId, long messageId) {
        ChatRead read = readRepository.findByGroupIdAndUserId(groupId, access.userId())
            .orElseGet(() -> new ChatRead(groupId, access.userId()));
        if (messageId > read.getLastReadId()) {
            read.setLastReadId(messageId);
            readRepository.save(read);
        }
    }

    private StaffGroup accessible(Long groupId) {
        StaffGroup group = groupRepository.findByIdAndRestaurantIdAndActiveTrue(groupId, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        if (!access.isManager() && !group.hasMember(access.userId())) {
            throw new ResourceNotFoundException("Group not found");
        }
        return group;
    }

    /** La referencia debe ser una mesa o pedido del mismo restaurante. */
    private void validateReference(String refType, Long refId) {
        if (refType == null || refId == null) {
            throw new ResourceNotFoundException("Reference not found");
        }
        Long restaurantId = access.restaurantId();
        boolean exists = switch (refType) {
            case "TABLE" -> tableRepository.findById(refId)
                .filter(t -> t.getRestaurant().getId().equals(restaurantId)).isPresent();
            case "ORDER" -> orderRepository.findByIdAndRestaurantId(refId, restaurantId).isPresent();
            default -> false;
        };
        if (!exists) {
            throw new ResourceNotFoundException("Reference not found");
        }
    }

    private static ChatMessageResponse toResponse(ChatMessage m, Long me) {
        return new ChatMessageResponse(m.getId(), m.getGroupId(), m.getSender().getId(), m.getSender().getName(),
            m.getSender().getRole().name(), m.getBody(), m.getRefType(), m.getRefId(), m.getCreatedAt(),
            m.getSender().getId().equals(me));
    }

    private static String preview(String body) {
        return body.length() <= 80 ? body : body.substring(0, 79) + "…";
    }
}
