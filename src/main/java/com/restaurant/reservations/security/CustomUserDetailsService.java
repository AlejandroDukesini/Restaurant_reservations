package com.restaurant.reservations.security;

import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmailAndActiveTrue(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return toPrincipal(user);
    }

    // Se usa en cada peticion con JWT. Exigir active=true revoca el acceso en cuanto
    // se da de baja a alguien; antes el token seguia valido hasta expirar (QA-SEC-02).
    @Transactional
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
            .filter(User::isActive)
            .orElseThrow(() -> new UsernameNotFoundException("User not found or inactive with id: " + id));
        return toPrincipal(user);
    }

    private static UserPrincipal toPrincipal(User user) {
        return new UserPrincipal(
            user.getId(),
            user.getEmail(),
            user.getPassword(),
            user.getRole(),
            user.getRestaurant() != null ? user.getRestaurant().getId() : null
        );
    }
}
