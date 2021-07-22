package com.market.repository.user;

import com.market.model.user.Role;
import com.market.model.user.UserRoles;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Role findByRole(UserRoles role);
}
