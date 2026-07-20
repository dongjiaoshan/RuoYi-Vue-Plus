package org.dromara.djs.warehouse.pack.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 果蔬日损耗（V4，果疏产品全流程处理.docx · compute-on-read）。
 *
 * <p>按自然日（{@code statDate}）对 {@code belong_type='vegetable'} 的出入库流水聚合，
 * 不建汇总表。公式：</p>
 * <pre>
 *   日损耗 = 领用出库总重(pickedWeight, pick_out) − 打包入库总重(packedWeight, pack_in) − 退回(returnedWeight, return_in) − 饲喂(feedWeight, V1 恒 0)
 * </pre>
 *
 * <p>各分量按 stock_flow 中实际存在的果蔬流水类型聚合；对应类型当日无流水则该分量为 0
 * （优雅降级，不阻塞），客户在毛菜处理间录入后数据自然完整。负损耗（领用 &lt; 打包+退回+饲喂，
 * 多为录入未配齐）归零并保留口径说明，不视为异常。</p>
 *
 * @author djs
 * @since V4 果疏全流程 Part II
 */
@Data
public class VegDailyLossVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计自然日（yyyy-MM-dd）。 */
    private String statDate;

    /** 领用出库总重 kg（物资领用出库流水 pick_out，MatFlowServiceImpl.pick 写）。 */
    private BigDecimal pickedWeight;

    /** 打包入库总重 kg（果蔬打包入库流水 pack_in，submitVegPack 写）。 */
    private BigDecimal packedWeight;

    /** 退回总重 kg（退回流水 return_in）。 */
    private BigDecimal returnedWeight;

    /** 饲喂总重 kg（物资领用 V1 无果蔬饲喂操作，该项恒 0；毛菜处理间饲喂属步9 另一阶段，不在此口径重复扣）。 */
    private BigDecimal feedWeight;

    /** 日损耗 kg（= picked − packed − returned − feed；负值归零）。 */
    private BigDecimal lossWeight;
}
