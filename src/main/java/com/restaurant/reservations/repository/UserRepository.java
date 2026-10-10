package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    List<User> findByRestaurantId(Long restaurantId);

    Optional<User> findByEmailAndActiveTrue(String email);

    List<User> findByRestaurantIdAndRoleIn(Long restaurantId, List<Role> roles);

    Optional<User> findByIdAndRestaurantId(Long id, Long restaurantId);
}
