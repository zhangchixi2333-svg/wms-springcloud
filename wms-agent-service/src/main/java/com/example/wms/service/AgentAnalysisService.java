/**
 * 本文件实现智能助手的本地库存预测、建议生成和轻量 RAG 数据维护。
 */
package com.example.wms.service;

import com.example.wms.config.AgentProperties;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentAnalysisService {

    private static final int HISTORY_DAYS = 30;
    private static final long DASHBOARD_CACHE_MILLIS = 15_000;

    private final JdbcTemplate jdbcTemplate;
    private final AgentProperties properties;
    private volatile CachedDashboard cachedDashboard;

    public AgentAnalysisService(JdbcTemplate jdbcTemplate, AgentProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public Map<String, Object> health() {
        return Map.of(
                "status", "可用",
                "callApi", properties.isCallApi(),
                "ragEnabled", properties.getRag().isEnabled(),
                "ragProvider", properties.getRag().getProvider(),
                "mode", properties.isCallApi() ? "外部模型预留模式" : "本地规则分析模式"
        );
    }

    public AgentDashboard dashboard(Integer requestedDays) {
        int days = normalizeDays(requestedDays);
        CachedDashboard cached = cachedDashboard;
        long now = System.currentTimeMillis();
        if (cached != null && cached.days() == days && now - cached.createdAtMillis() <= DASHBOARD_CACHE_MILLIS) {
            return cached.dashboard();
        }
        Map<String, Object> health = health();
        List<ForecastRow> rows = forecast(days);
        List<AgentSuggestionDto> generatedSuggestions = buildSuggestions(rows);
        AgentOverview overview = buildOverview(rows, generatedSuggestions);
        List<AgentSuggestionDto> persistedSuggestions = latestSuggestions();
        List<AgentSuggestionDto> finalSuggestions = persistedSuggestions.isEmpty()
                ? generatedSuggestions
                : persistedSuggestions;
        AgentDashboard dashboard = new AgentDashboard(
                health,
                overview,
                rows.stream().limit(50).toList(),
                finalSuggestions.stream().limit(Math.max(properties.getSuggestionLimit(), 1)).toList(),
                latestRun(),
                ragDocumentPreviews(10)
        );
        cachedDashboard = new CachedDashboard(days, now, dashboard);
        return dashboard;
    }

    public AgentOverview overview() {
        List<ForecastRow> rows = forecast(properties.getForecastDays());
        return buildOverview(rows, buildSuggestions(rows));
    }

    private AgentOverview buildOverview(List<ForecastRow> rows, List<AgentSuggestionDto> topSuggestions) {
        long critical = rows.stream().filter(row -> "CRITICAL".equals(row.riskLevel())).count();
        long low = rows.stream().filter(row -> "LOW".equals(row.riskLevel())).count();
        long attention = rows.stream().filter(row -> "ATTENTION".equals(row.riskLevel())).count();
        BigDecimal currentQty = rows.stream()
                .map(ForecastRow::currentQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AgentOverview(
                properties.isCallApi(),
                properties.getRag().isEnabled(),
                properties.getRag().getProvider(),
                rows.size(),
                currentQty,
                critical,
                low,
                attention,
                topSuggestions.stream().limit(Math.max(properties.getSuggestionLimit(), 1)).toList()
        );
    }

    public List<ForecastRow> forecast(Integer requestedDays) {
        int days = normalizeDays(requestedDays);
        List<InventoryAggregate> rows = jdbcTemplate.query("""
                WITH inventory_sum AS (
                  SELECT part_id, COALESCE(SUM(qty), 0) AS current_qty
                  FROM inventory
                  GROUP BY part_id
                ),
                outbound_sum AS (
                  SELECT part_id, ABS(COALESCE(SUM(qty_change), 0)) AS outbound_qty
                  FROM inventory_transaction
                  WHERE qty_change < 0
                    AND created_at >= DATE_SUB(NOW(6), INTERVAL ? DAY)
                  GROUP BY part_id
                ),
                thresholds AS (
                  SELECT item_code, remark
                  FROM config_item
                  WHERE module_key = 'inventoryWarning'
                    AND status = 'ENABLED'
                )
                SELECT
                  p.id AS part_id,
                  p.part_code,
                  p.part_name,
                  COALESCE(i.current_qty, 0) AS current_qty,
                  COALESCE(o.outbound_qty, 0) AS outbound_qty,
                  COALESCE(t.remark, td.remark) AS threshold_remark
                FROM part p
                LEFT JOIN inventory_sum i ON i.part_id = p.id
                LEFT JOIN outbound_sum o ON o.part_id = p.id
                LEFT JOIN thresholds t ON t.item_code = p.part_code
                LEFT JOIN thresholds td ON td.item_code = 'DEFAULT'
                ORDER BY p.part_code
                """, (rs, rowNum) -> new InventoryAggregate(
                rs.getLong("part_id"),
                rs.getString("part_code"),
                rs.getString("part_name"),
                valueOrZero(rs.getBigDecimal("current_qty")),
                valueOrZero(rs.getBigDecimal("outbound_qty")),
                ThresholdConfig.parse(rs.getString("threshold_remark"))
        ), HISTORY_DAYS);

        return rows.stream()
                .map(row -> {
                    BigDecimal avgDailyOutQty = row.outboundQty()
                            .divide(BigDecimal.valueOf(HISTORY_DAYS), 6, RoundingMode.HALF_UP);
                    BigDecimal forecastQty = row.currentQty()
                            .subtract(avgDailyOutQty.multiply(BigDecimal.valueOf(days)))
                            .max(BigDecimal.ZERO)
                            .setScale(3, RoundingMode.HALF_UP);
                    ThresholdConfig threshold = row.threshold();
                    String riskLevel = riskLevel(row.currentQty(), forecastQty, threshold);
                    LocalDate estimatedStockoutDate = estimateStockoutDate(row.currentQty(), avgDailyOutQty);
                    BigDecimal suggestedReplenishQty = suggestedReplenishQty(forecastQty, threshold);
                    return new ForecastRow(
                            row.partId(),
                            row.partCode(),
                            row.partName(),
                            row.currentQty().setScale(3, RoundingMode.HALF_UP),
                            avgDailyOutQty.setScale(3, RoundingMode.HALF_UP),
                            days,
                            forecastQty,
                            estimatedStockoutDate,
                            riskLevel,
                            riskLabel(riskLevel),
                            suggestedReplenishQty,
                            threshold.critical(),
                            threshold.low(),
                            threshold.attention()
                    );
                })
                .sorted(Comparator.comparingInt(row -> riskSort(row.riskLevel())))
                .toList();
    }

    public List<AgentSuggestionDto> suggestions() {
        List<AgentSuggestionDto> persisted = latestSuggestions();
        if (!persisted.isEmpty()) {
            return persisted;
        }
        return buildSuggestions(forecast(properties.getForecastDays())).stream()
                .limit(Math.max(properties.getSuggestionLimit(), 1))
                .toList();
    }

    private List<AgentSuggestionDto> latestSuggestions() {
        return jdbcTemplate.query("""
                SELECT id, run_id, suggestion_type, risk_level, part_id, part_code, title, content,
                       action_key, target_page_key, target_business_no, status, created_at
                FROM agent_suggestion
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapSuggestion(rs), Math.max(properties.getSuggestionLimit(), 1));
    }

    @Transactional
    public AgentRunDto analyze(Integer requestedDays) {
        int days = normalizeDays(requestedDays);
        LocalDateTime startedAt = LocalDateTime.now();
        String runNo = "AGENT-" + startedAt.toLocalDate().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbcTemplate.update("""
                INSERT INTO agent_run (run_no, status, call_api, forecast_days, suggestion_count, started_at)
                VALUES (?, 'RUNNING', ?, ?, 0, ?)
                """, runNo, properties.isCallApi(), days, startedAt);
        Long runId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        try {
            List<ForecastRow> rows = forecast(days);
            List<AgentSuggestionDto> generatedSuggestions = buildSuggestions(rows);
            batchInsertForecastSnapshots(runId, rows);
            batchInsertSuggestions(runId, generatedSuggestions);
            jdbcTemplate.update("""
                    UPDATE agent_run
                    SET status = 'COMPLETED', suggestion_count = ?, finished_at = NOW(6)
                    WHERE id = ?
                    """, generatedSuggestions.size(), runId);
            clearDashboardCache();
            return new AgentRunDto(runId, runNo, "COMPLETED", properties.isCallApi(), days, generatedSuggestions.size(), startedAt, LocalDateTime.now(), null);
        } catch (Exception ex) {
            jdbcTemplate.update("""
                    UPDATE agent_run
                    SET status = 'FAILED', error_message = ?, finished_at = NOW(6)
                    WHERE id = ?
                    """, left(ex.getMessage(), 1000), runId);
            throw ex;
        }
    }

    private void batchInsertForecastSnapshots(Long runId, List<ForecastRow> rows) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO agent_forecast_snapshot
                (run_id, part_id, part_code, part_name, current_qty, avg_daily_out_qty, forecast_days,
                 forecast_qty, estimated_stockout_date, risk_level, suggested_replenish_qty, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6))
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                ForecastRow row = rows.get(index);
                ps.setLong(1, runId);
                ps.setLong(2, row.partId());
                ps.setString(3, row.partCode());
                ps.setString(4, row.partName());
                ps.setBigDecimal(5, row.currentQty());
                ps.setBigDecimal(6, row.avgDailyOutQty());
                ps.setInt(7, row.forecastDays());
                ps.setBigDecimal(8, row.forecastQty());
                if (row.estimatedStockoutDate() == null) {
                    ps.setObject(9, null);
                } else {
                    ps.setObject(9, row.estimatedStockoutDate());
                }
                ps.setString(10, row.riskLevel());
                ps.setBigDecimal(11, row.suggestedReplenishQty());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private void batchInsertSuggestions(Long runId, List<AgentSuggestionDto> suggestions) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO agent_suggestion
                (run_id, suggestion_type, risk_level, part_id, part_code, title, content,
                 action_key, target_page_key, target_business_no, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', NOW(6))
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                AgentSuggestionDto suggestion = suggestions.get(index);
                ps.setLong(1, runId);
                ps.setString(2, suggestion.suggestionType());
                ps.setString(3, suggestion.riskLevel());
                if (suggestion.partId() == null) {
                    ps.setObject(4, null);
                } else {
                    ps.setLong(4, suggestion.partId());
                }
                ps.setString(5, suggestion.partCode());
                ps.setString(6, suggestion.title());
                ps.setString(7, suggestion.content());
                ps.setString(8, suggestion.actionKey());
                ps.setString(9, suggestion.targetPageKey());
                ps.setString(10, suggestion.targetBusinessNo());
            }

            @Override
            public int getBatchSize() {
                return suggestions.size();
            }
        });
    }

    @Transactional
    public AgentAnswer ask(AgentAskRequest request) {
        String question = request.question().trim();
        String normalized = question.toLowerCase(Locale.ROOT);
        List<AgentSuggestionDto> relevantSuggestions;
        String answer;
        if (containsAny(normalized, "缺货", "低库存", "补货", "不足")) {
            relevantSuggestions = buildSuggestions(forecast(properties.getForecastDays())).stream()
                    .filter(item -> !"NORMAL".equals(item.riskLevel()))
                    .limit(5)
                    .toList();
            answer = relevantSuggestions.isEmpty()
                    ? "当前没有明显低库存或缺货风险。"
                    : "我按库存阈值和近 30 天出库速度筛出了需要优先关注的补货建议。";
        } else if (containsAny(normalized, "预测", "未来", "消耗")) {
            List<ForecastRow> rows = forecast(properties.getForecastDays());
            long riskCount = rows.stream().filter(row -> !"NORMAL".equals(row.riskLevel())).count();
            relevantSuggestions = buildSuggestions(rows).stream().limit(5).toList();
            answer = "按未来 " + properties.getForecastDays() + " 天估算，当前共有 " + rows.size() + " 个零件参与预测，其中 " + riskCount + " 个存在预警风险。";
        } else if (containsAny(normalized, "呆滞", "长期未动", "周转")) {
            relevantSuggestions = slowMovingSuggestions(forecast(properties.getForecastDays())).stream().limit(5).toList();
            answer = relevantSuggestions.isEmpty()
                    ? "近 30 天没有发现明显呆滞库存。"
                    : "以下零件近 30 天没有出库记录且仍有库存，建议复核需求或转储策略。";
        } else {
            relevantSuggestions = suggestions().stream().limit(5).toList();
            answer = "当前外部模型调用为关闭状态，我会使用本地规则回答。你可以问：哪些零件需要补货、未来库存预测、是否有呆滞库存。";
        }

        String sessionId = Optional.ofNullable(request.sessionId()).filter(value -> !value.isBlank()).orElse("default");
        jdbcTemplate.update("""
                INSERT INTO agent_chat_message (session_id, role, content, call_api, created_at)
                VALUES (?, 'USER', ?, ?, NOW(6)), (?, 'ASSISTANT', ?, ?, NOW(6))
                """,
                sessionId,
                question,
                properties.isCallApi(),
                sessionId,
                answer,
                properties.isCallApi()
        );
        return new AgentAnswer(answer, properties.isCallApi(), relevantSuggestions);
    }

    public List<RagDocumentDto> ragDocuments() {
        return jdbcTemplate.query("""
                SELECT id, doc_key, title, source_type, content, metadata_json, enabled, created_at
                FROM agent_rag_document
                ORDER BY created_at DESC, id DESC
                """, (rs, rowNum) -> new RagDocumentDto(
                rs.getLong("id"),
                rs.getString("doc_key"),
                rs.getString("title"),
                rs.getString("source_type"),
                rs.getString("content"),
                rs.getString("metadata_json"),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", LocalDateTime.class)
        ));
    }

    private List<RagDocumentDto> ragDocumentPreviews(int limit) {
        return jdbcTemplate.query("""
                SELECT id, doc_key, title, source_type, LEFT(content, 240) AS content,
                       metadata_json, enabled, created_at
                FROM agent_rag_document
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> new RagDocumentDto(
                rs.getLong("id"),
                rs.getString("doc_key"),
                rs.getString("title"),
                rs.getString("source_type"),
                rs.getString("content"),
                rs.getString("metadata_json"),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", LocalDateTime.class)
        ), limit);
    }

    @Transactional
    public RagDocumentDto createRagDocument(RagDocumentRequest request) {
        String docKey = Optional.ofNullable(request.docKey())
                .filter(value -> !value.isBlank())
                .orElse("DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        jdbcTemplate.update("""
                INSERT INTO agent_rag_document (doc_key, title, source_type, content, metadata_json, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(6))
                ON DUPLICATE KEY UPDATE
                  title = VALUES(title),
                  source_type = VALUES(source_type),
                  content = VALUES(content),
                  metadata_json = VALUES(metadata_json),
                  enabled = VALUES(enabled)
                """,
                docKey,
                request.title(),
                Optional.ofNullable(request.sourceType()).filter(value -> !value.isBlank()).orElse("MANUAL"),
                request.content(),
                Optional.ofNullable(request.metadataJson()).orElse("{}"),
                request.enabled() == null || request.enabled()
        );
        Long documentId = jdbcTemplate.queryForObject("SELECT id FROM agent_rag_document WHERE doc_key = ?", Long.class, docKey);
        jdbcTemplate.update("DELETE FROM agent_rag_chunk WHERE document_id = ?", documentId);
        List<String> chunks = chunkText(request.content(), 800);
        for (int index = 0; index < chunks.size(); index += 1) {
            jdbcTemplate.update("""
                    INSERT INTO agent_rag_chunk (document_id, chunk_index, content, embedding_json, metadata_json, created_at)
                    VALUES (?, ?, ?, NULL, ?, NOW(6))
                    """, documentId, index, chunks.get(index), Optional.ofNullable(request.metadataJson()).orElse("{}"));
        }
        clearDashboardCache();
        return ragDocuments().stream()
                .filter(item -> item.docKey().equals(docKey))
                .findFirst()
                .orElseThrow();
    }

    private void clearDashboardCache() {
        cachedDashboard = null;
    }

    private List<AgentSuggestionDto> buildSuggestions(List<ForecastRow> rows) {
        List<AgentSuggestionDto> result = new ArrayList<>();
        rows.stream()
                .filter(row -> !"NORMAL".equals(row.riskLevel()))
                .forEach(row -> result.add(new AgentSuggestionDto(
                        null,
                        null,
                        "REPLENISH",
                        row.riskLevel(),
                        row.partId(),
                        row.partCode(),
                        row.riskLabel() + "：" + row.partCode(),
                        "零件 " + row.partName() + " 当前库存 " + row.currentQty() + "，预测 " + row.forecastDays()
                                + " 天后约 " + row.forecastQty() + "，建议补货 " + row.suggestedReplenishQty() + "。",
                        "OPEN_INBOUND",
                        "inbound",
                        null,
                        "OPEN",
                        LocalDateTime.now()
                )));
        result.addAll(slowMovingSuggestions(rows));
        return result.stream()
                .sorted(Comparator.comparingInt(item -> riskSort(item.riskLevel())))
                .limit(Math.max(properties.getSuggestionLimit(), 1))
                .toList();
    }

    private List<AgentSuggestionDto> slowMovingSuggestions(List<ForecastRow> rows) {
        return rows.stream()
                .filter(row -> row.currentQty().signum() > 0)
                .filter(row -> row.avgDailyOutQty().signum() == 0)
                .sorted(Comparator.comparing(ForecastRow::currentQty).reversed())
                .limit(Math.max(properties.getSuggestionLimit(), 1))
                .map(row -> new AgentSuggestionDto(
                        null,
                        null,
                        "SLOW_MOVING",
                        "ATTENTION",
                        row.partId(),
                        row.partCode(),
                        "呆滞库存关注：" + row.partCode(),
                        "零件 " + row.partName() + " 当前库存 " + row.currentQty() + "，近 30 天未发现出库流水，建议复核需求或库位占用。",
                        "OPEN_INVENTORY_BOARD",
                        "inventoryBoard",
                        null,
                        "OPEN",
                        LocalDateTime.now()
                ))
                .toList();
    }

    private AgentSuggestionDto mapSuggestion(ResultSet rs) throws SQLException {
        return new AgentSuggestionDto(
                rs.getLong("id"),
                rs.getLong("run_id"),
                rs.getString("suggestion_type"),
                rs.getString("risk_level"),
                rs.getObject("part_id", Long.class),
                rs.getString("part_code"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("action_key"),
                rs.getString("target_page_key"),
                rs.getString("target_business_no"),
                rs.getString("status"),
                rs.getObject("created_at", LocalDateTime.class)
        );
    }

    private AgentRunDto latestRun() {
        return jdbcTemplate.query("""
                SELECT id, run_no, status, call_api, forecast_days, suggestion_count, started_at, finished_at, error_message
                FROM agent_run
                ORDER BY started_at DESC, id DESC
                LIMIT 1
                """, rs -> {
            if (!rs.next()) return null;
            return new AgentRunDto(
                    rs.getLong("id"),
                    rs.getString("run_no"),
                    rs.getString("status"),
                    rs.getBoolean("call_api"),
                    rs.getInt("forecast_days"),
                    rs.getInt("suggestion_count"),
                    rs.getObject("started_at", LocalDateTime.class),
                    rs.getObject("finished_at", LocalDateTime.class),
                    rs.getString("error_message")
            );
        });
    }

    private String riskLevel(BigDecimal currentQty, BigDecimal forecastQty, ThresholdConfig threshold) {
        BigDecimal candidate = forecastQty.min(currentQty);
        if (threshold.critical().signum() > 0 && candidate.compareTo(threshold.critical()) <= 0) return "CRITICAL";
        if (threshold.low().signum() > 0 && candidate.compareTo(threshold.low()) <= 0) return "LOW";
        if (threshold.attention().signum() > 0 && candidate.compareTo(threshold.attention()) <= 0) return "ATTENTION";
        return "NORMAL";
    }

    private String riskLabel(String riskLevel) {
        return switch (riskLevel) {
            case "CRITICAL" -> "严重不足";
            case "LOW" -> "低库存";
            case "ATTENTION" -> "关注";
            default -> "正常";
        };
    }

    private int riskSort(String riskLevel) {
        return switch (riskLevel) {
            case "CRITICAL" -> 0;
            case "LOW" -> 1;
            case "ATTENTION" -> 2;
            case "NORMAL" -> 3;
            default -> 4;
        };
    }

    private LocalDate estimateStockoutDate(BigDecimal currentQty, BigDecimal avgDailyOutQty) {
        if (avgDailyOutQty.signum() <= 0) return null;
        int daysToStockout = currentQty.divide(avgDailyOutQty, 0, RoundingMode.CEILING).intValue();
        return LocalDate.now().plusDays(Math.max(daysToStockout, 0));
    }

    private BigDecimal suggestedReplenishQty(BigDecimal forecastQty, ThresholdConfig threshold) {
        BigDecimal target = threshold.attention().signum() > 0 ? threshold.attention() : threshold.low();
        if (target.signum() <= 0 || forecastQty.compareTo(target) >= 0) return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        return target.subtract(forecastQty).setScale(3, RoundingMode.HALF_UP);
    }

    private int normalizeDays(Integer days) {
        int value = days == null ? properties.getForecastDays() : days;
        return Math.min(Math.max(value, 1), 365);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String left(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private List<String> chunkText(String text, int size) {
        List<String> chunks = new ArrayList<>();
        String source = Optional.ofNullable(text).orElse("");
        for (int index = 0; index < source.length(); index += size) {
            chunks.add(source.substring(index, Math.min(index + size, source.length())));
        }
        if (chunks.isEmpty()) chunks.add("");
        return chunks;
    }

    private record InventoryAggregate(
            Long partId,
            String partCode,
            String partName,
            BigDecimal currentQty,
            BigDecimal outboundQty,
            ThresholdConfig threshold
    ) {
    }

    private record ThresholdConfig(BigDecimal critical, BigDecimal low, BigDecimal attention) {

        static ThresholdConfig defaults() {
            return new ThresholdConfig(BigDecimal.valueOf(10), BigDecimal.valueOf(30), BigDecimal.valueOf(60));
        }

        static ThresholdConfig parse(String raw) {
            if (raw == null || raw.isBlank()) return defaults();
            return new ThresholdConfig(readNumber(raw, "critical"), readNumber(raw, "low"), readNumber(raw, "attention"));
        }

        private static BigDecimal readNumber(String raw, String key) {
            String marker = "\"" + key + "\"";
            int keyIndex = raw.indexOf(marker);
            if (keyIndex < 0) return BigDecimal.ZERO;
            int colonIndex = raw.indexOf(':', keyIndex + marker.length());
            if (colonIndex < 0) return BigDecimal.ZERO;
            int endIndex = colonIndex + 1;
            while (endIndex < raw.length() && " 0123456789.-".indexOf(raw.charAt(endIndex)) >= 0) {
                endIndex += 1;
            }
            String value = raw.substring(colonIndex + 1, endIndex).trim();
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException ex) {
                return BigDecimal.ZERO;
            }
        }
    }

    public record AgentOverview(
            boolean callApi,
            boolean ragEnabled,
            String ragProvider,
            int partCount,
            BigDecimal currentQty,
            long criticalCount,
            long lowCount,
            long attentionCount,
            List<AgentSuggestionDto> topSuggestions
    ) {
    }

    public record AgentDashboard(
            Map<String, Object> health,
            AgentOverview overview,
            List<ForecastRow> forecastRows,
            List<AgentSuggestionDto> suggestions,
            AgentRunDto latestRun,
            List<RagDocumentDto> ragDocuments
    ) {
    }

    private record CachedDashboard(int days, long createdAtMillis, AgentDashboard dashboard) {
    }

    public record ForecastRow(
            Long partId,
            String partCode,
            String partName,
            BigDecimal currentQty,
            BigDecimal avgDailyOutQty,
            int forecastDays,
            BigDecimal forecastQty,
            LocalDate estimatedStockoutDate,
            String riskLevel,
            String riskLabel,
            BigDecimal suggestedReplenishQty,
            BigDecimal criticalThreshold,
            BigDecimal lowThreshold,
            BigDecimal attentionThreshold
    ) {
    }

    public record AgentSuggestionDto(
            Long id,
            Long runId,
            String suggestionType,
            String riskLevel,
            Long partId,
            String partCode,
            String title,
            String content,
            String actionKey,
            String targetPageKey,
            String targetBusinessNo,
            String status,
            LocalDateTime createdAt
    ) {
    }

    public record AgentRunDto(
            Long id,
            String runNo,
            String status,
            boolean callApi,
            int forecastDays,
            int suggestionCount,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String errorMessage
    ) {
    }

    public record AgentAskRequest(String sessionId, @NotBlank String question) {
    }

    public record AgentAnswer(String answer, boolean callApi, List<AgentSuggestionDto> suggestions) {
    }

    public record RagDocumentRequest(
            String docKey,
            @NotBlank String title,
            String sourceType,
            @NotBlank String content,
            String metadataJson,
            Boolean enabled
    ) {
    }

    public record RagDocumentDto(
            Long id,
            String docKey,
            String title,
            String sourceType,
            String content,
            String metadataJson,
            boolean enabled,
            LocalDateTime createdAt
    ) {
    }
}
