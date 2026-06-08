package com.example.wms.repo;

import com.example.wms.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 系统用户数据访问接口
 * 继承JpaRepository，提供AppUser实体的CRUD操作
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户Optional对象，不存在则返回空
     */
    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByAuthToken(String authToken);

    List<AppUser> findAllByOrderByUsernameAsc();

    long countByRoleName(String roleName);
}
