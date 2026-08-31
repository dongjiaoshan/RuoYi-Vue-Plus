package org.dromara.djs.warehouse.demand.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 需求管理汇总列表导出 VO（admin row155「需求管理新增列表导出功能」）。
 *
 * <p>列与页面列表（{@link DemandGroupVo} 驱动的 BizTable）一一对应、同顺序，导出的是当前筛选条件下的
 * <b>全量</b>分组行（不受分页限制）。</p>
 *
 * <p>页面上由前端计算的展示值在此预先算好成文本列，避免 xlsx 里出现裸码 / 裸小数：
 * <ul>
 *   <li>需求状态：三态 {@code PENDING/ALL_CONFIRMED/PARTIAL} 无字典，后端直接落中文（与
 *       {@code demand.groupStatus.*} 前端文案一致）；</li>
 *   <li>需求确认率：后端 0~1 小数 → 百分比整数文本（与前端 {@code formatRate} 同口径）；</li>
 *   <li>需求量 / 原材料计算量：kg 单位保留 3 位小数、计数单位取整（与前端
 *       {@code formatOrderQuantity} / {@code formatQtyByKgRule} 同口径）；</li>
 *   <li>单位：白条业态显「头」（与列表一致），其余取产品单位。</li>
 * </ul>
 *
 * @author djs
 * @since admin row155
 */
@Data
@ExcelIgnoreUnannotated
public class DemandGroupExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求状态（三态中文：待确认 / 已全部确认 / 部分确认）。 */
    @ExcelProperty(value = "需求状态")
    private String demandStatusLabel;

    /** 需求确认率（百分比文本，如 {@code 67%}）。 */
    @ExcelProperty(value = "需求确认率")
    private String confirmRateLabel;

    /** 需求日期。 */
    @ExcelProperty(value = "需求日期")
    private LocalDate demandDate;

    /** 产品名称。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 产品规格。 */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /** 需求量（按单位口径格式化后的文本）。 */
    @ExcelProperty(value = "需求量")
    private String demandQuantityLabel;

    /** 单位（白条业态显「头」）。 */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /** 需求产品类型（字典 {@code djs_belong_type}）。 */
    @ExcelProperty(value = "需求产品类型", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_belong_type")
    private String belongType;

    /** 原材料名称。 */
    @ExcelProperty(value = "原材料")
    private String rawMaterialName;

    /** 原材料计算量（按原材料单位口径格式化后的文本；未配则空）。 */
    @ExcelProperty(value = "原材料计算量")
    private String materialCalcQtyLabel;

    /** 原材料单位。 */
    @ExcelProperty(value = "原材料单位")
    private String materialUnit;

    /** 需求门店数量。 */
    @ExcelProperty(value = "需求门店数量")
    private Integer storeCount;

    /** 需求最终确认时间。 */
    @ExcelProperty(value = "需求最终确认时间")
    private LocalDateTime lastConfirmTime;

    /** 原始确认率（0~1，仅内部计算用，不导出）。 */
    private BigDecimal confirmRate;
}
