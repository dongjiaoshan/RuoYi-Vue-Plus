package org.dromara.djs.store.trace.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店猪肉现场生码入参（STORE-TRACE-ONSITE-001）。
 *
 * <p>门店零售现场：选已出栏可追溯猪只（{@link #earNo}）+ 选零售部位（{@link #cutLabel}）+ 录产品重量
 * （{@link #weight}）→ 现场生成一张 pork 追溯码并打印。</p>
 *
 * @author djs
 * @since STORE-TRACE-ONSITE-001
 */
@Data
public class StoreTraceOnsiteBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只耳号（已出栏可追溯猪，picker 选中传值）。 */
    @NotBlank(message = "猪只耳号不能为空")
    private String earNo;

    /** 零售部位中文名（字典 {@code djs_pork_cut_product}：前腿肉 / 五花肉 / 排骨 / 肘子 / 大排）。 */
    @NotBlank(message = "请选择追溯部位")
    private String cutLabel;

    /** 产品重量 kg（> 0）。 */
    @NotNull(message = "产品重量不能为空")
    @DecimalMin(value = "0.01", message = "产品重量需大于 0")
    private BigDecimal weight;
}
