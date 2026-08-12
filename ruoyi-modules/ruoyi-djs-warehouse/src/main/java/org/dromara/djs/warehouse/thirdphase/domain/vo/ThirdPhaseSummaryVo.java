package org.dromara.djs.warehouse.thirdphase.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 【三期】出入库合计（V6 row92，admin 三期作物入库管理页头两个数）。
 *
 * <p>甲方口径：「通过三期标识可以统计三期的总入库和总出库」——两个数都按流水行上的
 * {@code third_phase=1} 汇总，不依赖地块（三期没有真实地块，只做文案显示）。</p>
 *
 * @author djs
 * @since V6-R92
 */
@Data
public class ThirdPhaseSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 三期总入库量（{@code inout_type=IN} 的三期流水合计，kg）。
     */
    private BigDecimal totalIn;

    /**
     * 三期总出库量（{@code inout_type=OT} 的三期流水合计，kg；正数，方向已由 inout_type 表达）。
     */
    private BigDecimal totalOut;

}
