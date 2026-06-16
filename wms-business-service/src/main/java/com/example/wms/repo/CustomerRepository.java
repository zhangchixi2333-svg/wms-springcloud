/**
 * 本文件定义 CustomerRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerCode(String customerCode);
}
