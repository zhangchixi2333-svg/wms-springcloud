package com.example.wms.repo;

import com.example.wms.domain.ConfigItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigItemRepository extends JpaRepository<ConfigItem, Long> {
    List<ConfigItem> findByModuleKeyOrderByCreatedAtDescIdDesc(String moduleKey);

    Optional<ConfigItem> findByModuleKeyAndItemCode(String moduleKey, String itemCode);
}
