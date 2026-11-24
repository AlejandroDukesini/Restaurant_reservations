package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.MenuItemRequest;
import com.restaurant.reservations.dto.MenuItemResponse;
import com.restaurant.reservations.dto.OpsDtos.CategoryRequest;
import com.restaurant.reservations.dto.OpsDtos.CategoryResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.StateConflictException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.MenuCategory;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Permission;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.repository.MenuCategoryRepository;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.OrderItemRepository;
import com.restaurant.reservations.repository.RestaurantRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menu: categorias libres y productos con imagen, tiempo estimado y disponibilidad.
 *
 * Solo el gerente crea, edita, cambia precios, imagenes o categorias. "Agotado/disponible" lo
 * puede cambiar ademas quien tenga TOGGLE_AVAILABILITY (p. ej. cocina), sin tocar nada mas.
 */
@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);

    /** Codigos del enum anterior, aceptados por compatibilidad con clientes antiguos. */
    private static final Map<String, String> LEGACY_CATEGORIES = Map.of(
        "STARTER", "Entradas", "MAIN", "Platos fuertes", "DESSERT", "Postres", "DRINK", "Bebidas");

    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final MediaService mediaService;
    private final AccessService access;
    private final RealtimeService realtime;

    public MenuService(
        MenuItemRepository menuItemRepository,
        MenuCategoryRepository categoryRepository,
        OrderItemRepository orderItemRepository,
        RestaurantRepository restaurantRepository,
        MediaService mediaService,
        AccessService access,
        RealtimeService realtime
    ) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
        this.orderItemRepository = orderItemRepository;
        this.restaurantRepository = restaurantRepository;
        this.mediaService = mediaService;
        this.access = access;
        this.realtime = realtime;
    }

    // ------------------------------------------------------------------ Productos

    /** Menu activo (incluye agotados, marcados como no disponibles). */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenu() {
        return sorted(menuItemRepository.findByRestaurantIdAndActiveTrue(access.restaurantId()));
    }

    /** Vista de administracion: incluye archivados. */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAdminMenu() {
        access.requireManager();
        return sorted(menuItemRepository.findByRestaurantId(access.restaurantId()));
    }

    @Transactional
    public MenuItemResponse createMenuItem(MenuItemRequest request) {
        access.requireManager();
        Long restaurantId = access.restaurantId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        MenuItem item = new MenuItem(request.name().strip(), resolveCategory(request), request.price(), restaurant);
        apply(item, request);
        MenuItemResponse response = toResponse(menuItemRepository.save(item));
        changed();
        return response;
    }

    @Transactional
    public MenuItemResponse updateMenuItem(Long id, MenuItemRequest request) {
        access.requireManager();
        MenuItem item = find(id);
        item.setName(request.name().strip());
        item.setCategory(resolveCategory(request));
        if (item.getPrice() != request.price()) {
            log.info("Precio del plato {} cambiado de {} a {} por userId={}", id, item.getPrice(), request.price(),
                access.userId());
        }
        item.setPrice(request.price());
        apply(item, request);
        MenuItemResponse response = toResponse(menuItemRepository.save(item));
        changed();
        return response;
    }

    /** Agotado / disponible. */
    @Transactional
    public MenuItemResponse setAvailability(Long id, boolean available) {
        access.require(Permission.TOGGLE_AVAILABILITY);
        MenuItem item = find(id);
        item.setAvailable(available);
        MenuItemResponse response = toResponse(menuItemRepository.save(item));
        changed();
        return response;
    }

    /** Archivar (fuera del menu, conserva historico) o reactivar. */
    @Transactional
    public MenuItemResponse setActive(Long id, boolean active) {
        access.requireManager();
        MenuItem item = find(id);
        if (active && !item.getCategory().isActive()) {
            throw new StateConflictException("The category is archived; move the product to another category first");
        }
        item.setActive(active);
        MenuItemResponse response = toResponse(menuItemRepository.save(item));
        changed();
        return response;
    }

    /**
     * Eliminacion segura: si el plato aparece en algun pedido se archiva (borrarlo romperia el
     * historico y las metricas); si nunca se pidio, se borra de verdad.
     */
    @Transactional
    public void deleteMenuItem(Long id) {
        access.requireManager();
        MenuItem item = find(id);
        if (orderItemRepository.existsByMenuItemId(item.getId())) {
            item.setActive(false);
            menuItemRepository.save(item);
        } else {
            menuItemRepository.delete(item);
        }
        changed();
    }

    // ------------------------------------------------------------------ Categorias

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categoryRepository.findByRestaurantIdAndActiveTrueOrderBySortOrderAscNameAsc(access.restaurantId())
            .stream()
            .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getSortOrder(),
                menuItemRepository.countByCategoryIdAndActiveTrue(c.getId())))
            .toList();
    }

    @Transactional
    public CategoryResponse saveCategory(Long id, CategoryRequest request) {
        access.requireManager();
        Long restaurantId = access.restaurantId();
        String name = request.name().strip();
        categoryRepository.findFirstByRestaurantIdAndActiveTrueAndNameIgnoreCase(restaurantId, name)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new ValidationBusinessException("A category with that name already exists");
            });
        MenuCategory category = id == null
            ? new MenuCategory(restaurantId, name, request.sortOrder() != null ? request.sortOrder() : 0)
            : categoryRepository.findByIdAndRestaurantIdAndActiveTrue(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        category.setName(name);
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        categoryRepository.save(category);
        changed();
        return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder(),
            menuItemRepository.countByCategoryIdAndActiveTrue(category.getId()));
    }

    /** Solo se elimina (archiva) una categoria sin productos activos. */
    @Transactional
    public void deleteCategory(Long id) {
        access.requireManager();
        MenuCategory category = categoryRepository.findByIdAndRestaurantIdAndActiveTrue(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (menuItemRepository.countByCategoryIdAndActiveTrue(category.getId()) > 0) {
            throw new StateConflictException("Move or archive the category's products first");
        }
        category.setActive(false);
        categoryRepository.save(category);
        changed();
    }

    // ------------------------------------------------------------------ Utilidades

    private MenuItem find(Long id) {
        return menuItemRepository.findByIdAndRestaurantId(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
    }

    private void apply(MenuItem item, MenuItemRequest request) {
        item.setDescription(OrderService.blankToNull(request.description()));
        item.setProtein(OrderService.blankToNull(request.protein()));
        item.setCondiments(OrderService.blankToNull(request.condiments()));
        item.setIngredients(OrderService.blankToNull(request.ingredients()));
        item.setPreparationNotes(OrderService.blankToNull(request.preparationNotes()));
        item.setPrepMinutes(request.prepMinutes());
        item.setImageMediaId(mediaService.requireOwned(request.imageMediaId()));
        if (request.available() != null) {
            item.setAvailable(request.available());
        }
    }

    /** categoryId (nuevo) o nombre/codigo antiguo; si el nombre no existe se crea la categoria. */
    private MenuCategory resolveCategory(MenuItemRequest request) {
        Long restaurantId = access.restaurantId();
        if (request.categoryId() != null) {
            return categoryRepository.findByIdAndRestaurantIdAndActiveTrue(request.categoryId(), restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }
        String raw = request.category() == null || request.category().isBlank() ? "MAIN" : request.category().strip();
        String name = LEGACY_CATEGORIES.getOrDefault(raw.toUpperCase(Locale.ROOT), raw);
        return categoryRepository.findFirstByRestaurantIdAndActiveTrueAndNameIgnoreCase(restaurantId, name)
            .orElseGet(() -> categoryRepository.save(new MenuCategory(restaurantId, name, 0)));
    }

    private List<MenuItemResponse> sorted(List<MenuItem> items) {
        return items.stream()
            .sorted(Comparator.comparingInt((MenuItem i) -> i.getCategory().getSortOrder())
                .thenComparing(i -> i.getCategory().getName())
                .thenComparing(MenuItem::getName))
            .map(MenuService::toResponse)
            .toList();
    }

    private void changed() {
        realtime.broadcast(access.restaurantId(), RealtimeService.TOPIC_MENU);
    }

    static MenuItemResponse toResponse(MenuItem item) {
        return new MenuItemResponse(
            item.getId(),
            item.getName(),
            item.getDescription(),
            item.getCategory().getName(),
            item.getPrice(),
            item.getProtein(),
            item.getCondiments(),
            item.getIngredients(),
            item.getPreparationNotes(),
            item.isActive(),
            item.getCategory().getId(),
            item.getImageMediaId(),
            MediaService.url(item.getImageMediaId()),
            item.getPrepMinutes(),
            item.isAvailable()
        );
    }
}
