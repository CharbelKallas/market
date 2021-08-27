package com.market.repository.user;

import com.market.model.user.Role;
import com.market.model.user.UserRoles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findOneByName(UserRoles name);
}
