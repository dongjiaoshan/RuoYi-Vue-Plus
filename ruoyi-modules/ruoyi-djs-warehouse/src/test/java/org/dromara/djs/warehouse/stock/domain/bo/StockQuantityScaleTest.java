package org.dromara.djs.warehouse.stock.domain.bo;

import jakarta.validation.Validation;
import jakarta.validation.constraints.Digits;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存查询两个弹框的数量精度闸（V6 row143）。
 *
 * <p>{@code t_warehouse_location_stock.product_stock} 与 {@code t_warehouse_stock_flow.change_quantity}
 * 都是 {@code DECIMAL(12,3)}。小数位多于 3 位时 MySQL 会**静默四舍五入**（实测 2.2225 落库成 2.223），
 * 用户看不到任何提示、账也对不上。所以三位以内放行、第四位当场拒绝，让错误停在接口层。</p>
 *
 * <p>前端 {@code StockOutDialog.vue} / {@code PigTransferDialog.vue} 给的是同一口径
 * （计量类单位 precision=3、计数类单位整数），这份测试锁死后端这一半。</p>
 *
 * <p>本注解只管**小数位**；计数类单位「只能整数」是另一道闸，装在 Controller 入参层
 * （{@code LocationStockServiceImpl#assertManualOutQuantity} / {@code #assertManualTransferQuantity}），
 * 见 {@code QuantityUnitRuleTest}。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("产品出库 / 猪肉库位转移：数量最多三位小数（V6 row143）")
class StockQuantityScaleTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private static StockOutBo outBo(String quantity) {
        StockOutBo bo = new StockOutBo();
        bo.setId(1L);
        bo.setOutDate(new Date());
        bo.setStockOutDest("kitchen");
        bo.setQuantity(new BigDecimal(quantity));
        return bo;
    }

    private static StockTransferBo transferBo(String quantity) {
        StockTransferBo bo = new StockTransferBo();
        bo.setId(1L);
        bo.setTransferDate(new Date());
        bo.setQuantity(new BigDecimal(quantity));
        return bo;
    }

    @Test
    @DisplayName("出库量三位小数放行 —— 甲方原话就是要能填三位")
    void outAllowsThreeDecimals() {
        assertThat(VALIDATOR.validate(outBo("2.222"))).isEmpty();
        assertThat(VALIDATOR.validate(outBo("0.001"))).isEmpty();
        assertThat(VALIDATOR.validate(outBo("65.880"))).isEmpty();
    }

    @Test
    @DisplayName("出库量第四位小数被拒 —— 否则 MySQL 把 2.2225 悄悄记成 2.223")
    void outRejectsFourthDecimal() {
        // 断言注解类型而不是文案：message 走 i18n key（{stock.out.quantity.scale}），
        // 纯单测没有 Spring 的 MessageSource，拿不到中文/英文成品串。
        assertThat(VALIDATOR.validate(outBo("2.2225")))
            .anyMatch(v -> v.getConstraintDescriptor().getAnnotation() instanceof Digits);
    }

    @Test
    @DisplayName("转移量同一口径：三位放行、四位被拒")
    void transferSharesTheSameScale() {
        assertThat(VALIDATOR.validate(transferBo("1.234"))).isEmpty();
        assertThat(VALIDATOR.validate(transferBo("1.2345")))
            .anyMatch(v -> v.getConstraintDescriptor().getAnnotation() instanceof Digits);
    }

    @Test
    @DisplayName("0 与负数仍被下界闸拦住（本次改动不该放松它）")
    void positiveLowerBoundStillHolds() {
        assertThat(VALIDATOR.validate(outBo("0"))).isNotEmpty();
        assertThat(VALIDATOR.validate(outBo("-1"))).isNotEmpty();
        assertThat(VALIDATOR.validate(transferBo("0"))).isNotEmpty();
    }
}
