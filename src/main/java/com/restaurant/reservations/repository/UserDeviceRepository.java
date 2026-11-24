package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.UserDevice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUserIdAndDeviceKey(Long userId, String deviceKey);

    List<UserDevice> findByUserIdInOrderByLastSeenAtDesc(Collection<Long> userIds);
}
