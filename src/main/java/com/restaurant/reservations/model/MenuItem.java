package com.restaurant.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Plato o bebida del menu.
 *
 * active = false: archivado (fuera del menu, conserva historico).
 * available = false: agotado temporalmente (sigue en el menu, no se puede pedir).
 */
@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private MenuCategory category;

    @Column(nullable = false)
    private double price;

    // Especificaciones de receta que consulta la cocina
    @Column
    private String protein;

    @Column(length = 1000)
    private String condiments;

    @Column(length = 2000)
    private String ingredients;

    @Column(length = 1000)
    private String preparationNotes;

    @Column(name = "image_media_id", length = 36)
    private String imageMediaId;

    /** Tiempo estimado de preparacion en minutos (referencia; el tiempo real se mide). */
    private Integer prepMinutes;

    @Column(nullable = false)
    private boolean available = true;

    @ManyToOne
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private boolean active = true;

    protected MenuItem() {
    }

    public MenuItem(String name, MenuCategory category, double price, Restaurant restaurant) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.restaurant = restaurant;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public MenuCategory getCategory() { return category; }
    public void setCategory(MenuCategory category) { this.category = category; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getProtein() { return protein; }
    public void setProtein(String protein) { this.protein = protein; }

    public String getCondiments() { return condiments; }
    public void setCondiments(String condiments) { this.condiments = condiments; }

    public String getIngredients() { return ingredients; }
    public void setIngredients(String ingredients) { this.ingredients = ingredients; }

    public String getPreparationNotes() { return preparationNotes; }
    public void setPreparationNotes(String preparationNotes) { this.preparationNotes = preparationNotes; }

    public String getImageMediaId() { return imageMediaId; }
    public void setImageMediaId(String imageMediaId) { this.imageMediaId = imageMediaId; }

    public Integer getPrepMinutes() { return prepMinutes; }
    public void setPrepMinutes(Integer prepMinutes) { this.prepMinutes = prepMinutes; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
