package org.dromara.djs.store.ledger.controller;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.djs.store.ledger.domain.bo.StoreDailyLedgerBatchBo;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerVo;
import org.dromara.djs.store.ledger.service.IStoreDailyLedgerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 不启动应用或数据库，验证请求绑定、嵌套校验及项目统一业务错误响应。 */
@Tag("local")
@Tag("dev")
class StoreDailyLedgerControllerTest {
    private IStoreDailyLedgerService service;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(IStoreDailyLedgerService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new StoreDailyLedgerController(service))
            .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"inboundQty", "saleQty", "giftQty", "returnSaleQty", "returnWhQty", "closingQty", "lossQty"})
    void rejectsNestedNegativeQuantitiesBeforeCallingService(String property) throws Exception {
        mvc.perform(post("/djs/store/ledger/batch").contentType("application/json")
                .content("{\"storeId\":\"9001\",\"items\":[{\"productId\":\"8001\",\"" + property + "\":-3}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg", containsString("不能为负数")));
        verifyNoInteractions(service);
    }

    @Test
    void preservesSubmittedConsumptionSnapshotAndSnowflakeIds() throws Exception {
        when(service.batchSave(any())).thenReturn(1);
        mvc.perform(post("/djs/store/ledger/batch").contentType("application/json")
                .content("{\"storeId\":\"9001\",\"items\":[{\"productId\":\"2058525064717926401\",\"saleQty\":\"3.125\"}]}"))
            .andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<StoreDailyLedgerBatchBo> cap = ArgumentCaptor.forClass(StoreDailyLedgerBatchBo.class);
        verify(service).batchSave(cap.capture());
        assertThat(cap.getValue().getItems().get(0).getProductId()).isEqualTo(2058525064717926401L);
        assertThat(cap.getValue().getItems().get(0).getSaleQty()).isEqualByComparingTo("3.125");
    }

    @Test
    void returnsReadableConflictUsingProjectBusinessResponse() throws Exception {
        when(service.batchSave(any())).thenThrow(new ServiceException("现场打包消耗量已变化，请点击「刷新打包消耗」", 409));
        mvc.perform(post("/djs/store/ledger/batch").contentType("application/json")
                .content("{\"storeId\":\"9001\",\"items\":[{\"productId\":\"8001\",\"saleQty\":0}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.msg", containsString("刷新打包消耗")));
    }

    @Test
    void consumptionReadAndHistoricalDetailUseSeparateSources() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 27);
        when(service.queryOnsiteConsumption(9001L, date)).thenReturn(Map.of(8001L, new BigDecimal("3")));
        StoreDailyLedgerVo saved = new StoreDailyLedgerVo();
        saved.setSaleQty(BigDecimal.ZERO);
        when(service.queryDetail(9001L, date)).thenReturn(List.of(saved));

        mvc.perform(get("/djs/store/ledger/onsite-consumption").param("storeId", "9001").param("ledgerDate", date.toString()))
            .andExpect(jsonPath("$.data['8001']").value(3));
        mvc.perform(get("/djs/store/ledger/detail").param("storeId", "9001").param("ledgerDate", date.toString()))
            .andExpect(jsonPath("$.data[0].saleQty").value(0));
        verify(service).queryOnsiteConsumption(9001L, date);
        verify(service).queryDetail(9001L, date);
        verify(service, never()).batchSave(any());
    }
}
