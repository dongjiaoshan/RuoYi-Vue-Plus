package org.dromara.djs.plant.perf.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 绩效详情导出 — sheet1「产量绩效」行（逐作物）。
 *
 * <p>数据源 = {@code queryCropRows(teamId, statMonth)}（与详情抽屉产量绩效 tab 同口径）。
 * 采摘量按公斤展示；单价为元/斤，绩效额 = 采摘量(公斤) × 单价(元/斤) × 2（公斤→斤换算）。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Data
@ExcelIgnoreUnannotated
public class PerfDetailCropExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作物名称。
     */
    @ExcelProperty(value = "作物")
    private String cropName;

    /**
     * 采摘量（公斤）。
     */
    @ExcelProperty(value = "采摘量(公斤)")
    private BigDecimal pickWeight;

    /**
     * 单价快照（元/斤）。
     */
    @ExcelProperty(value = "单价(元/斤)")
    private BigDecimal unitPriceSnapshot;

    /**
     * 绩效额（元）。
     */
    @ExcelProperty(value = "绩效额(元)")
    private BigDecimal performanceAmount;
}
