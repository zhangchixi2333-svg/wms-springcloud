package com.example.wms.repo;

import com.example.wms.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 客户数据访问接口
 * 继承JpaRepository，提供Customer实体的CRUD操作
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    /**
     * 根据客户编码查询客户
     * @param customerCode 客户编码
     * @return 客户Optional对象，不存在则返回空
     */
    Optional<Customer> findByCustomerCode(String customerCode);
}