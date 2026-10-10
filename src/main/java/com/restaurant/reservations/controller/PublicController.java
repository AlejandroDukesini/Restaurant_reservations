package com.restaurant.reservations.controller;

import com.restaurant.reservations.dto.RestaurantResponse;
import com.restaurant.reservations.dto.TableResponse;
import com.restaurant.reservations.service.RestaurantService;
import com.restaurant.reservations.service.TableService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final RestaurantService restaurantService;
    private final TableService tableService;

    public PublicController(RestaurantService restaurantService, TableService tableService) {
        this.restaurantService = restaurantService;
        this.tableService = tableService;
    }

    @GetMapping("/restaurants")
    public ResponseEntity<List<RestaurantResponse>> getAllRestaurants() {
        return ResponseEntity.ok(restaurantService.getAllRestaurants());
    }

    @GetMapping("/restaurants/{slug}")
    public ResponseEntity<RestaurantResponse> getRestaurantBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(restaurantService.getRestaurantBySlug(slug));
    }

    @GetMapping("/restaurants/{slug}/tables")
    public ResponseEntity<List<TableResponse>> getRestaurantTables(
        @PathVariable String slug,
        @RequestParam(required = false) Integer floor
    ) {
        RestaurantResponse restaurant = restaurantService.getRestaurantBySlug(slug);
        List<TableResponse> tables = floor != null
            ? tableService.getTablesByFloor(restaurant.id(), floor)
            : tableService.getTablesByRestaurant(restaurant.id());
        return ResponseEntity.ok(tables);
    }
}
