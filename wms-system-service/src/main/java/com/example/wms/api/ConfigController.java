package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.common.BusinessException;
import com.example.wms.domain.ConfigItem;
import com.example.wms.repo.ConfigItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/config-items")
public class ConfigController {

    private final ConfigItemRepository configItemRepository;

    public ConfigController(ConfigItemRepository configItemRepository) {
        this.configItemRepository = configItemRepository;
    }

    @GetMapping
    public ApiResponse<List<ConfigItemView>> list(@RequestParam String moduleKey) {
        return ApiResponse.ok(configItemRepository.findByModuleKeyOrderByCreatedAtDescIdDesc(moduleKey).stream()
                .map(this::toView)
                .toList());
    }

    @PostMapping
    public ApiResponse<ConfigItemView> create(@Valid @RequestBody ConfigItemRequest request) {
        configItemRepository.findByModuleKeyAndItemCode(request.moduleKey(), request.itemCode()).ifPresent(existing -> {
            throw new BusinessException("Config item code already exists in this module");
        });
        ConfigItem item = new ConfigItem();
        item.setModuleKey(request.moduleKey());
        item.setItemCode(request.itemCode());
        item.setItemName(request.itemName());
        item.setStatus(request.status() == null || request.status().isBlank() ? "ENABLED" : request.status());
        item.setRemark(request.remark());
        item.setCreatedAt(LocalDateTime.now());
        return ApiResponse.ok(toView(configItemRepository.save(item)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        configItemRepository.deleteById(id);
        return ApiResponse.okMessage("Config item deleted");
    }

    private ConfigItemView toView(ConfigItem item) {
        return new ConfigItemView(
                item.getId(),
                item.getModuleKey(),
                item.getItemCode(),
                item.getItemName(),
                item.getStatus(),
                item.getRemark(),
                item.getCreatedAt()
        );
    }

    public record ConfigItemRequest(@NotBlank String moduleKey,
                                    @NotBlank String itemCode,
                                    @NotBlank String itemName,
                                    String status,
                                    String remark) {
    }

    public record ConfigItemView(Long id,
                                 String moduleKey,
                                 String itemCode,
                                 String itemName,
                                 String status,
                                 String remark,
                                 LocalDateTime createdAt) {
    }
}
