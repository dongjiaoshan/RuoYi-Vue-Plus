package org.dromara.djs.plant.perf.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 绩效详情导出 — sheet2「农事记录」行。
 *
 * <p>数据源 = 该班组（farm_by = teamId）该月（farm_date ∈ statMonth 整月）的
 * {@code t_plant_farm_records}，与详情抽屉农事记录 tab 同口径，全量不分页。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Data
@ExcelIgnoreUnannotated
public class PerfDetailFarmExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录编号。
     */
    @ExcelProperty(value = "记录编号")
    private String recordNo;

    /**
     * 农事类型（字典 djs_farm_work_type，导出转中文标签）。
     */
    @ExcelProperty(value = "农事类型", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_farm_work_type")
    private String farmType;

    /**
     * 地块名称。
     */
    @ExcelProperty(value = "地块")
    private String plotName;

    /**
     * 农事日期。
     */
    @ExcelProperty(value = "农事日期")
    private LocalDate farmDate;
}
