package org.dromara.djs.warehouse.veg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

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

    @ExcelProperty(value = "地块编号")
    private String plotCode;

    /** 采摘量 = record_weight(kg)。 */
    @ExcelProperty(value = "采摘量(公斤)")
    private BigDecimal pickWeight;

    @ExcelIgnore
    private Long teamId;

    @ExcelProperty(value = "采摘班组")
    private String teamName;

}
