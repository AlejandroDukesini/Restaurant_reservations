package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.MenuItemRequest;
import com.restaurant.reservations.dto.MenuItemResponse;
import com.restaurant.reservations.model.MenuCategory;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.RestaurantRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final AuthService authService;

    public MenuService(
        MenuItemRepository menuItemRepository,
        RestaurantRepository restaurantRepository,
        AuthService authService
    ) {
        this.menuItemRepository = menuItemRepository;
        this.restaurantRepository = restaurantRepository;
        this.authService = authService;
    }

    public List<MenuItemResponse> getMenu() {
        Long restaurantId = currentRestaurantId();
        return menuItemRepository.findByRestaurantIdAndActiveTrue(restaurantId).stream()
            .map(MenuService::toResponse)
            .toList();
    }

    @Transactional
    public MenuItemResponse createMenuItem(MenuItemRequest request) {
        Long restaurantId = currentRestaurantId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        MenuItem item = new MenuItem(request.name(), parseCategory(request.category()), request.price(), restaurant);
        applyRecipe(item, request);
        return toResponse(menuItemRepository.save(item));
    }

    @Transactional
    public MenuItemResponse updateMenuItem(Long id, MenuItemRequest request) {
        Long restaurantId = currentRestaurantId();
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        MenuCategory category = parseCategory(request.category());

        item.setName(request.name());
        item.setCategory(category);
        item.setPrice(request.price());
        applyRecipe(item, request);
        return toResponse(menuItemRepository.save(item));
    }

    @Transactional
    public void deleteMenuItem(Long id) {
        Long restaurantId = currentRestaurantId();
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        item.setActive(false);
        menuItemRepository.save(item);
    }

    private static void applyRecipe(MenuItem item, MenuItemRequest request) {
        item.setDescription(request.description());
        item.setProtein(request.protein());
        item.setCondiments(request.condiments());
        item.setIngredients(request.ingredients());
        item.setPreparationNotes(request.preparationNotes());
    }

    private Long currentRestaurantId() {
        Long restaurantId = authService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            throw new IllegalArgumentException("User not associated with a restaurant");
        }
        return restaurantId;
    }

    private static MenuCategory parseCategory(String value) {
        try {
            return MenuCategory.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid category: " + value);
        }
    }

    private static MenuItemResponse toResponse(MenuItem item) {
        return new MenuItemResponse(
            item.getId(),
            item.getName(),
            item.getDescription(),
            item.getCategory().name(),
            item.getPrice(),
            item.getProtein(),
            item.getCondiments(),
            item.getIngredients(),
            item.getPreparationNotes(),
            item.isActive()
        );
    }
}
