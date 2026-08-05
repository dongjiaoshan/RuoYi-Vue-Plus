package org.dromara.djs.store.returns.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 仓库「退回明细」导出行 VO（admin 退回明细弹窗导出）。
 *
 * <p>列与弹窗表格 1:1 对齐，且**数值一律以文本导出**：实收量 / 差异量的量纲由原材料单位决定
 * （kg → 三位小数重量，非 kg → 整数件数 + 单位），裸 BigDecimal 落进 Excel 会丢掉这层口径，
 * 让「退 1 份礼盒、仓库实收 0.080」看起来像收了 0.08 份。格式化统一在 service 里做，
 * 与前端 {@code utils/weight.ts#formatReceivedAmount} 同一套规则。</p>
 *
 * @author djs
 */
@Data
@ExcelIgnoreUnannotated
public class StoreReturnDetailExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "退回产品")
    private String productName;

    /** 退回量：产品单位为 kg → 三位小数；否则去尾零。 */
    @ExcelProperty(value = "退回量")
    private String returnQuantity;

    @ExcelProperty(value = "单位")
    private String productUnit;

    /** 仓库实收量：原材料单位为 kg → {@code X.XXXkg}；否则 {@code 整数+原材料单位}。 */
    @ExcelProperty(value = "仓库实收量")
    private String receivedAmount;

    /** 差异量 = 退回量 − 实收量，仅两边同为 kg 量纲时有值，否则 —。 */
    @ExcelProperty(value = "差异量")
    private String quantityDiff;

    /** 是否丢弃：仅已确认行有结论（是 / 否），未确认 —。 */
    @ExcelProperty(value = "是否丢弃")
    private String isDiscard;

    /** 退回状态：待仓库确认 / 已入库 / 已丢弃（丢弃行不沿用「已入库」，它没进库存）。 */
    @ExcelProperty(value = "退回状态")
    private String returnStatus;

    @ExcelProperty(value = "退回库位")
    private String locationName;

    @ExcelProperty(value = "确认时间")
    private String confirmTime;
}
