package org.dromara.djs.warehouse.loss.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.mapper.LossFlowMapper;
import org.dromara.djs.warehouse.loss.mapper.ProductionLossAggregateMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductionLossServiceImpl")
class ProductionLossServiceImplTest {

    @Mock private ProductionLossAggregateMapper aggregateMapper;
    @Mock private LossFlowMapper lossFlowMapper;
    @Mock private ILossFlowService lossFlowService;

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, LossFlow.class);
    }

    @Test
    @DisplayName("admin row109：目标自然日损耗时间落在当日23:59:59")
    void aggregateWritesAtEndOfTargetDay() {
        when(aggregateMapper.selectProductFlowAgg("1001", "2026-07-26"))
            .thenReturn(List.of(Map.of("productId", 1L, "pickOut", new BigDecimal("2"))));
        when(aggregateMapper.selectProductManualLoss("1001", "2026-07-26")).thenReturn(List.of());
        when(aggregateMapper.selectProductPackUsage("1001", "2026-07-26")).thenReturn(List.of());
        when(lossFlowService.record(any(LossFlow.class))).thenReturn(10L);

        try (MockedStatic<TenantHelper> tenant = Mockito.mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("1001");
            new ProductionLossServiceImpl(aggregateMapper, lossFlowMapper, lossFlowService)
                .aggregate(LocalDate.of(2026, 7, 26));
        }

        ArgumentCaptor<LossFlow> captor = ArgumentCaptor.forClass(LossFlow.class);
        Mockito.verify(lossFlowService).record(captor.capture());
        assertThat(captor.getValue().getLossDate().toInstant()
            .atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime().toString())
            .isEqualTo("2026-07-26T23:59:59");
    }
}
