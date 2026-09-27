package org.dromara.djs.store.ledger.domain.bo;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StoreDailyLedgerBatchBo} 的服务端数量闸（row215-F4）。
 *
 * <p><b>为什么必须有这支测试</b>：前端每个量格都 {@code :min="0"}，但接口层原来完全不拦 ——
 * clean-QA 实测 {@code wh_return_qty=-3.000} 能直接落库。前端闸只挡手滑、不挡直接打接口。
 * 这支测试钉住「负数量在 BO 层就被拒」，防止哪天有人把注解删掉而没人发现。</p>
 *
 * @author djs
 * @since STR-RETURN-OPS-001 follow-up（row215-F4）
 */
@Tag("local")
@Tag("dev")
@DisplayName("当日盘点 BO 数量非负闸（row215-F4）")
class StoreDailyLedgerBatchBoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    private static StoreDailyLedgerBatchBo bo(StoreDailyLedgerBatchBo.Item item) {
        StoreDailyLedgerBatchBo bo = new StoreDailyLedgerBatchBo();
        bo.setStoreId(9001L);
        bo.setItems(List.of(item));
        return bo;
    }

    private static StoreDailyLedgerBatchBo.Item item() {
        StoreDailyLedgerBatchBo.Item item = new StoreDailyLedgerBatchBo.Item();
        item.setProductId(8001L);
        return item;
    }

    private static Set<String> violatedProperties(Object target) {
        return validator.validate(target).stream()
            .map(ConstraintViolation::getPropertyPath)
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("负退回量被拒（clean-QA 实测 wh_return_qty=-3.000 直接落库的那一格）")
    void negativeReturnWhQtyRejected() {
        StoreDailyLedgerBatchBo.Item it = item();
        it.setReturnWhQty(new BigDecimal("-3.000"));

        Set<String> bad = violatedProperties(bo(it));

        assertThat(bad).as("退回量为负必须在 BO 层被拒").anyMatch(p -> p.endsWith("returnWhQty"));
    }

    @Test
    @DisplayName("其余手填量列同样非负：入库 / 销售 / 赠送 / 顾客退货 / 期末 / 损耗")
    void otherNegativeQuantitiesRejected() {
        StoreDailyLedgerBatchBo.Item it = item();
        it.setInboundQty(new BigDecimal("-1"));
        it.setSaleQty(new BigDecimal("-1"));
        it.setGiftQty(new BigDecimal("-1"));
        it.setReturnSaleQty(new BigDecimal("-1"));
        it.setClosingQty(new BigDecimal("-1"));
        it.setLossQty(new BigDecimal("-1"));

        Set<String> bad = violatedProperties(bo(it));

        assertThat(bad).as("六个手填量列都要被拒").anyMatch(p -> p.endsWith("inboundQty"));
        assertThat(bad).anyMatch(p -> p.endsWith("saleQty"));
        assertThat(bad).anyMatch(p -> p.endsWith("giftQty"));
        assertThat(bad).anyMatch(p -> p.endsWith("returnSaleQty"));
        assertThat(bad).anyMatch(p -> p.endsWith("closingQty"));
        assertThat(bad).anyMatch(p -> p.endsWith("lossQty"));
    }

    @Test
    @DisplayName("0 与正数放行；期初刻意不设闸（服务端回显的历史结存可能为负，加了会把正常「修改」挡死）")
    void zeroAndPositivePassAndOpeningIsUnconstrained() {
        StoreDailyLedgerBatchBo.Item it = item();
        it.setOpeningQty(new BigDecimal("-5.000"));   // 历史结存回显，不该被拦
        it.setInboundQty(BigDecimal.ZERO);
        it.setSaleQty(new BigDecimal("1.250"));
        it.setGiftQty(BigDecimal.ZERO);
        it.setReturnSaleQty(BigDecimal.ZERO);
        it.setReturnWhQty(BigDecimal.ZERO);
        it.setClosingQty(new BigDecimal("2.000"));
        it.setLossQty(BigDecimal.ZERO);

        assertThat(violatedProperties(bo(it)))
            .as("合法数量和历史负期初均不该产生违规")
            .isEmpty();
    }
}
