/**
 * 本文件定义 AppUserRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByAuthToken(String authToken);

    List<AppUser> findAllByOrderByUsernameAsc();

    long countByRoleName(String roleName);
}
