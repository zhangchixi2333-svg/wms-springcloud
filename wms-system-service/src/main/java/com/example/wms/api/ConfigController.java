/**
 * 本文件实现 ConfigController 控制器。
 */
package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.common.BusinessException;
import com.example.wms.domain.ConfigItem;
import com.example.wms.repo.ConfigItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
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
        ensureDefaultItems(moduleKey);
        return ApiResponse.ok(configItemRepository.findByModuleKeyOrderByCreatedAtDescIdDesc(moduleKey).stream()
                .map(this::toView)
                .toList());
    }

    @PostMapping
    public ApiResponse<ConfigItemView> create(@Valid @RequestBody ConfigItemRequest request) {
        configItemRepository.findByModuleKeyAndItemCode(request.moduleKey(), request.itemCode()).ifPresent(existing -> {
            throw new BusinessException("配置项编码已存在");
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

    @PutMapping("/{id}")
    public ApiResponse<ConfigItemView> update(@PathVariable Long id, @Valid @RequestBody ConfigItemRequest request) {
        ConfigItem item = configItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("配置项不存在"));
        configItemRepository.findByModuleKeyAndItemCode(request.moduleKey(), request.itemCode()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BusinessException("配置项编码已存在");
            }
        });
        item.setModuleKey(request.moduleKey());
        item.setItemCode(request.itemCode());
        item.setItemName(request.itemName());
        item.setStatus(request.status() == null || request.status().isBlank() ? "ENABLED" : request.status());
        item.setRemark(request.remark());
        return ApiResponse.ok(toView(configItemRepository.save(item)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        configItemRepository.deleteById(id);
        return ApiResponse.okMessage("配置项已删除");
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

    private void ensureDefaultItems(String moduleKey) {
        if (!"categoryManagement".equals(moduleKey)) {
            return;
        }
        Map<String, String> defaults = Map.of(
                "DEFAULT", "默认分类",
                "MECHANICAL", "机械件",
                "ELECTRONIC", "电子件",
                "PACKAGING", "包装辅料",
                "OUTSOURCED", "外协件"
        );
        defaults.forEach((code, name) ->
                configItemRepository.findByModuleKeyAndItemCode(moduleKey, code).orElseGet(() -> {
                    ConfigItem item = new ConfigItem();
                    item.setModuleKey(moduleKey);
                    item.setItemCode(code);
                    item.setItemName(name);
                    item.setStatus("ENABLED");
                    item.setRemark("系统默认零件分类");
                    item.setCreatedAt(LocalDateTime.now());
                    return configItemRepository.save(item);
                })
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
