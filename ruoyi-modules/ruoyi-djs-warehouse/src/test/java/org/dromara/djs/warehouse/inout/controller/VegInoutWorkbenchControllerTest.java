package org.dromara.djs.warehouse.inout.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.dromara.djs.warehouse.inout.domain.bo.VegInFinishBo;
import org.dromara.djs.warehouse.inout.domain.bo.VegInSubmitBo;
import org.dromara.djs.warehouse.inout.domain.bo.VegOutWorkbenchSubmitBo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutStockVo;
import org.dromara.djs.warehouse.inout.service.VegInoutWorkbenchService;
import org.dromara.djs.warehouse.inout.service.VegOutFinishService;
import org.dromara.djs.warehouse.inout.domain.bo.VegOutFinishBo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 工作台接口薄委托 + 权限与猪只 / 白条工作台同一组（查询 / 录入 / 完成）。 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class VegInoutWorkbenchControllerTest {

    @Mock VegInoutWorkbenchService service;
    @Mock VegOutFinishService finishService;
    VegInoutWorkbenchController controller;

    @BeforeEach
    void setup() {
        controller = new VegInoutWorkbenchController(service, finishService);
    }

    @Test
    void writesDelegateToTheWorkbenchService() {
        when(service.submitIn(any())).thenReturn(77L);
        when(service.finishIn(any())).thenReturn(78L);
        VegInSubmitBo in = new VegInSubmitBo();
        VegInFinishBo finish = new VegInFinishBo();
        VegOutWorkbenchSubmitBo out = new VegOutWorkbenchSubmitBo();
        assertThat(controller.submitIn(in).getData()).isEqualTo(77L);
        assertThat(controller.finishIn(finish).getData()).isEqualTo(78L);
        assertThat(controller.submitOut(out).getCode()).isEqualTo(200);
        verify(service).submitIn(in);
        verify(service).finishIn(finish);
        verify(service).submitOut(out);
    }

    @Test
    void finishOutPreservesFlowIdResponseAndDelegates() {
        VegOutFinishBo bo = new VegOutFinishBo();
        when(finishService.finish(bo)).thenReturn(List.of(926101029300000001L));
        assertThat(controller.finishOut(bo).getData()).containsExactly(926101029300000001L);
        verify(finishService).finish(bo);
    }

    @Test
    void readsPassThrough() {
        VegOutStockVo card = new VegOutStockVo();
        when(service.outStocks(5L)).thenReturn(List.of(card));
        assertThat(controller.outStocks(5L).getData()).containsExactly(card);
    }

    @Test
    void zeroKilogramFinishNeedsOnlyTheSelectedPlotWhileWeightSubmissionStillNeedsPerformance() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            VegInFinishBo finish = new VegInFinishBo();
            assertThat(validator.validate(finish)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("plantingRecordId");
            finish.setPlantingRecordId(501L);
            assertThat(validator.validate(finish)).isEmpty();

            VegInSubmitBo submit = new VegInSubmitBo();
            submit.setPlantingRecordId(501L);
            submit.setWeight(new java.math.BigDecimal("1.000"));
            assertThat(validator.validate(submit)).extracting(v -> v.getPropertyPath().toString())
                .contains("teamIds", "perfPercent");
        }
    }

    @Test
    void fractionalPerformancePercentCannotBeTruncatedDuringJsonBinding() {
        ObjectMapper mapper = new ObjectMapper();
        assertThatThrownBy(() -> mapper.readValue("{\"perfPercent\":99.9}", VegInSubmitBo.class))
            .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"perfPercent\":1.5}", VegInFinishBo.class))
            .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test
    void permissionsMatchTheSiblingWorkbenches() {
        Map<String, String> perms = Arrays.stream(VegInoutWorkbenchController.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(SaCheckPermission.class))
            .collect(Collectors.toMap(Method::getName, m -> m.getAnnotation(SaCheckPermission.class).value()[0]));
        assertThat(perms).containsEntry("submitIn", "djs:warehouse:inout:submit")
            .containsEntry("submitOut", "djs:warehouse:inout:submit")
            .containsEntry("finishIn", "djs:warehouse:inout:finish")
            .containsEntry("finishOut", "djs:warehouse:inout:outFinish")
            .containsEntry("crops", "djs:warehouse:inout:query")
            .containsEntry("plots", "djs:warehouse:inout:query")
            .containsEntry("inOptions", "djs:warehouse:inout:query")
            .containsEntry("outProducts", "djs:warehouse:inout:query")
            .containsEntry("outStocks", "djs:warehouse:inout:query")
            .containsEntry("recentOutDests", "djs:warehouse:inout:query");
    }
}
