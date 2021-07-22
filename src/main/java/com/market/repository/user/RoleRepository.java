package com.market.repository.user;

import com.market.model.user.Role;
import com.market.model.user.UserRoles;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findOneByRole(UserRoles role);
}
