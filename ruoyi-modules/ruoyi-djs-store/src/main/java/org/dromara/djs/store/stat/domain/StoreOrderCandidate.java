package org.dromara.djs.store.stat.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public record StoreOrderCandidate(Long storeId, LocalDate demandDate, String demandStatus,
                                  LocalDateTime createTime, String auditHistory) {

    private static final ObjectMapper AUDIT_READER = new ObjectMapper();

    public LocalDateTime firstSubmittedAt() {
        if ("DRAFT".equals(demandStatus)) {
            return null;
        }
        if (auditHistory == null) {
            return createTime;
        }
        try {
            JsonNode history = AUDIT_READER.readTree(auditHistory);
            if (!history.isArray()) {
                return null;
            }
            for (JsonNode event : history) {
                if ("SUBMIT".equals(event.path("event").asText())) {
                    return LocalDateTime.parse(event.path("ts").asText());
                }
                if ("DRAFT".equals(event.path("from").asText())) {
                    return null; // 旧草稿可直接取消，没有下单动作。
                }
            }
            return createTime;
        } catch (JsonProcessingException | DateTimeParseException e) {
            return null; // 不用创建时间猜测损坏审计记录里的首次提交。
        }
    }
}
