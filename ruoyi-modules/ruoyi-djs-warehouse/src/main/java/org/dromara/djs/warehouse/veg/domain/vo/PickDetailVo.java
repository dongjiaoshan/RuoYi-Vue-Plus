package org.dromara.djs.warehouse.veg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.djs.common.excel.DictOrRawConvert;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘明细行 VO（admin 只读列表 + 导出，FIX-ADMIN-0721）。
 *
 * <p>一行 = 一条采收过磅流水（{@code t_warehouse_handle_record} record_type=1）。
 * 作物 / 班组取 {@code t_warehouse_planting_record} 冗余列，地块编号 LEFT JOIN
 * {@code t_plant_plot_info.plot_code}——关联链可断（planting_record_id 可 NULL），空值展示端兜底。</p>
 *
 * @author djs
 * @since FIX-ADMIN-0721
 */
@Data
@ExcelIgnoreUnannotated
public class PickDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 采摘日期 = DATE(handle_time)。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "采摘日期")
    private LocalDate pickDate;

    @ExcelProperty(value = "作物名称")
    private String cropName;

    /**
     * 产品名称（V6 row19）= 该条采收过磅流水选定的产品（{@code handle_record.product_id}）。
     *
     * <p>采摘活动来源的行没有产品维度，恒为空 —— 活动只记作物与重量，不落到具体产品上。</p>
     */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 统计来源：1=毛菜处理间 2=采摘活动（row44）。前端按 code 显示中文；导出走 readConverterExp 映射。 */
    @ExcelProperty(value = "统计来源", converter = DictOrRawConvert.class)
    @ExcelDictFormat(readConverterExp = "1=毛菜处理间,2=采摘活动")
    private String statSource;

    @ExcelProperty(value = "地块编号")
    private String plotCode;

    /** 采摘量 = record_weight(kg)。 */
    @ExcelProperty(value = "采摘量(公斤)")
    private BigDecimal pickWeight;

    /**
     * 绩效百分比（0-100 整数，%）—— V6 row106，录入时填、与称重记录同表。
     *
     * <p>采摘活动来源的行没有这个维度，恒为空（活动量按 100% 计入绩效）。</p>
     */
    @ExcelProperty(value = "绩效百分比(%)")
    private Integer perfPercent;

    /** 录入备注（V6 row106）。采摘活动来源的行恒为空。 */
    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelIgnore
    private Long teamId;

    @ExcelProperty(value = "采摘班组")
    private String teamName;

}
