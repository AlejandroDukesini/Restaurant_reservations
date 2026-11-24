package com.restaurant.reservations.controller;

import com.restaurant.reservations.dto.MenuItemResponse;
import com.restaurant.reservations.dto.OpsDtos.AssignDriverRequest;
import com.restaurant.reservations.dto.OpsDtos.AvailabilityRequest;
import com.restaurant.reservations.dto.OpsDtos.BrandingResponse;
import com.restaurant.reservations.dto.OpsDtos.CategoryRequest;
import com.restaurant.reservations.dto.OpsDtos.CategoryResponse;
import com.restaurant.reservations.dto.OpsDtos.DeliveryOrderRequest;
import com.restaurant.reservations.dto.OpsDtos.DriverRequest;
import com.restaurant.reservations.dto.OpsDtos.DriverResponse;
import com.restaurant.reservations.dto.OpsDtos.GroupActivity;
import com.restaurant.reservations.dto.OpsDtos.GroupBrandingRequest;
import com.restaurant.reservations.dto.OpsDtos.GroupMembersRequest;
import com.restaurant.reservations.dto.OpsDtos.GroupRequest;
import com.restaurant.reservations.dto.OpsDtos.GroupResponse;
import com.restaurant.reservations.dto.OpsDtos.MediaResponse;
import com.restaurant.reservations.dto.OpsDtos.PlatformRequest;
import com.restaurant.reservations.dto.OpsDtos.PlatformResponse;
import com.restaurant.reservations.dto.OpsDtos.ProfileRequest;
import com.restaurant.reservations.dto.OpsDtos.ProfileResponse;
import com.restaurant.reservations.dto.OpsDtos.RestaurantBrandingRequest;
import com.restaurant.reservations.dto.OrderResponse;
import com.restaurant.reservations.dto.TableRequest;
import com.restaurant.reservations.dto.TableResponse;
import com.restaurant.reservations.model.MediaFile;
import com.restaurant.reservations.service.BrandingService;
import com.restaurant.reservations.service.DeliveryService;
import com.restaurant.reservations.service.GroupService;
import com.restaurant.reservations.service.MediaService;
import com.restaurant.reservations.service.MenuService;
import com.restaurant.reservations.service.ProfileService;
import com.restaurant.reservations.service.TableService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Configuracion del restaurante: personalizacion, imagenes, grupos, mesas, menu y domicilios.
 *
 * Rutas /api/admin/**: solo gerente (regla de ruta + comprobacion en el servicio).
 * El resto exige personal autenticado y el servicio decide por permisos (p. ej. un mesero
 * con MANAGE_DELIVERIES puede registrar domicilios, pero nunca configurar plataformas).
 */
@RestController
@RequestMapping("/api")
public class ManagementController {

    private final BrandingService branding;
    private final MediaService media;
    private final GroupService groups;
    private final TableService tables;
    private final MenuService menu;
    private final DeliveryService deliveries;
    private final ProfileService profile;

    public ManagementController(
        BrandingService branding,
        MediaService media,
        GroupService groups,
        TableService tables,
        MenuService menu,
        DeliveryService deliveries,
        ProfileService profile
    ) {
        this.branding = branding;
        this.media = media;
        this.groups = groups;
        this.tables = tables;
        this.menu = menu;
        this.deliveries = deliveries;
        this.profile = profile;
    }

    // ------------------------------------------------------------------ Imagenes

    /** Publico a proposito: un <img> no puede enviar el token. El id es un UUID aleatorio. */
    @GetMapping("/media/{id}")
    public ResponseEntity<byte[]> mediaFile(@PathVariable String id) {
        MediaFile file = media.get(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header("Content-Disposition", "inline")
            .body(file.getData());
    }

    @PostMapping(path = "/admin/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public MediaResponse upload(@RequestPart("file") MultipartFile file) {
        return media.upload(file);
    }

    @DeleteMapping("/admin/media/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteMedia(@PathVariable String id) {
        media.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ Perfil propio

    @GetMapping("/me/profile")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
    public ProfileResponse profile() {
        return profile.get();
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
    public ProfileResponse updateProfile(@Valid @RequestBody ProfileRequest request) {
        return profile.update(request);
    }

    @PostMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
    public ProfileResponse avatar(@RequestPart("file") MultipartFile file) {
        return profile.setAvatar(file);
    }

    @DeleteMapping("/me/avatar")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
    public ProfileResponse removeAvatar() {
        return profile.removeAvatar();
    }

    // ------------------------------------------------------------------ Personalizacion

    @GetMapping("/branding")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
    public BrandingResponse currentBranding(@RequestParam(required = false) Long groupId) {
        return branding.current(groupId);
    }

    @GetMapping("/admin/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public BrandingResponse restaurantBranding() {
        return branding.restaurantBranding();
    }

    @PutMapping("/admin/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public BrandingResponse updateBranding(@Valid @RequestBody RestaurantBrandingRequest request) {
        return branding.updateRestaurant(request);
    }

    @PutMapping("/admin/groups/{id}/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public BrandingResponse updateGroupBranding(@PathVariable Long id, @Valid @RequestBody GroupBrandingRequest request) {
        return branding.updateGroup(id, request);
    }

    // ------------------------------------------------------------------ Grupos

    @GetMapping("/groups")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
    public List<GroupResponse> groups() {
        return groups.list();
    }

    @GetMapping("/groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
    public GroupResponse group(@PathVariable Long id) {
        return groups.get(id);
    }

    @PostMapping("/admin/groups")
    @PreAuthorize("hasRole('ADMIN')")
    public GroupResponse createGroup(@Valid @RequestBody GroupRequest request) {
        return groups.create(request);
    }

    @PutMapping("/admin/groups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GroupResponse updateGroup(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        return groups.update(id, request);
    }

    @DeleteMapping("/admin/groups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groups.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/admin/groups/{id}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public GroupResponse members(@PathVariable Long id, @Valid @RequestBody GroupMembersRequest request) {
        return groups.setMembers(id, request);
    }

    @GetMapping("/admin/groups/{id}/activity")
    @PreAuthorize("hasRole('ADMIN')")
    public GroupActivity activity(@PathVariable Long id) {
        return groups.activity(id);
    }

    // ------------------------------------------------------------------ Mesas (gerente o MANAGE_TABLES)

    @GetMapping("/tables/manage")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public List<TableResponse> allTables() {
        return tables.getAllTables();
    }

    @PostMapping("/tables/manage")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public TableResponse createTable(@Valid @RequestBody TableRequest request) {
        return tables.createTable(request);
    }

    @PutMapping("/tables/manage/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public TableResponse updateTable(@PathVariable Long id, @Valid @RequestBody TableRequest request) {
        return tables.updateTable(id, request);
    }

    @PutMapping("/tables/manage/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public TableResponse tableActive(@PathVariable Long id, @RequestParam boolean value) {
        return tables.setActive(id, value);
    }

    @DeleteMapping("/tables/manage/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public ResponseEntity<Void> deleteTable(@PathVariable Long id) {
        tables.deleteTable(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ Menu

    @GetMapping("/menu/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public List<CategoryResponse> categories() {
        return menu.categories();
    }

    @PostMapping("/admin/menu/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return menu.saveCategory(null, request);
    }

    @PutMapping("/admin/menu/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return menu.saveCategory(id, request);
    }

    @DeleteMapping("/admin/menu/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        menu.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/menu")
    @PreAuthorize("hasRole('ADMIN')")
    public List<MenuItemResponse> adminMenu() {
        return menu.getAdminMenu();
    }

    @PutMapping("/admin/menu/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public MenuItemResponse menuActive(@PathVariable Long id, @RequestParam boolean value) {
        return menu.setActive(id, value);
    }

    @PutMapping("/menu/items/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public MenuItemResponse availability(@PathVariable Long id, @Valid @RequestBody AvailabilityRequest request) {
        return menu.setAvailability(id, request.available());
    }

    // ------------------------------------------------------------------ Domicilios

    @GetMapping("/deliveries")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public List<OrderResponse> deliveryBoard() {
        return deliveries.board();
    }

    @PostMapping("/deliveries")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public OrderResponse createDelivery(@Valid @RequestBody DeliveryOrderRequest request) {
        return deliveries.create(request);
    }

    @GetMapping("/deliveries/mine")
    @PreAuthorize("hasRole('DELIVERY')")
    public List<OrderResponse> myDeliveries() {
        return deliveries.mine();
    }

    @PostMapping("/deliveries/{id}/packing/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public OrderResponse startPacking(@PathVariable Long id) {
        return deliveries.startPacking(id);
    }

    @PostMapping("/deliveries/{id}/packing/finish")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public OrderResponse finishPacking(@PathVariable Long id) {
        return deliveries.finishPacking(id);
    }

    @PutMapping("/deliveries/{id}/driver")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public OrderResponse assign(@PathVariable Long id, @RequestBody AssignDriverRequest request) {
        return deliveries.assignDriver(id, request);
    }

    @PostMapping("/deliveries/{id}/dispatch")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
    public OrderResponse dispatch(@PathVariable Long id) {
        return deliveries.dispatch(id);
    }

    @PostMapping("/deliveries/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
    public OrderResponse deliverDelivery(@PathVariable Long id) {
        return deliveries.deliver(id);
    }

    @GetMapping("/deliveries/platforms")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public List<PlatformResponse> platforms() {
        return deliveries.platforms();
    }

    @PostMapping("/admin/delivery/platforms")
    @PreAuthorize("hasRole('ADMIN')")
    public PlatformResponse createPlatform(@Valid @RequestBody PlatformRequest request) {
        return deliveries.savePlatform(null, request);
    }

    @PutMapping("/admin/delivery/platforms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PlatformResponse updatePlatform(@PathVariable Long id, @Valid @RequestBody PlatformRequest request) {
        return deliveries.savePlatform(id, request);
    }

    @GetMapping("/deliveries/drivers")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public List<DriverResponse> drivers() {
        return deliveries.drivers();
    }

    @PostMapping("/admin/delivery/drivers")
    @PreAuthorize("hasRole('ADMIN')")
    public DriverResponse createDriver(@Valid @RequestBody DriverRequest request) {
        return deliveries.saveDriver(null, request);
    }

    @PutMapping("/admin/delivery/drivers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DriverResponse updateDriver(@PathVariable Long id, @Valid @RequestBody DriverRequest request) {
        return deliveries.saveDriver(id, request);
    }
}
