package org.dromara.djs.warehouse.veg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 毛菜间处理记录列表 VO（FIX-ADMIN-R130，仓库-admin 行130「毛菜间处理记录」只读菜单）。
 *
 * <p>两类来源 UNION 成同一张列表：</p>
 * <ul>
 *   <li>毛菜处理间（statSource=1）：{@code t_warehouse_handle_record} record_type=2 处理流水
 *       （handle_target 1/2/3 → veg_fresh / platform / feed）+ {@code t_warehouse_vegetable_handle.loss_weight}
 *       结算损耗行（→ loss）。</li>
 *   <li>采摘活动（statSource=2）：{@code t_plant_plant_activity} per-event 行，pick_dest 直接就是处理方式。</li>
 * </ul>
 *
 * <p>「处理方式」两来源统一到字典 {@code djs_pick_dest}（sale / veg_fresh / platform / loss / feed）。</p>
 *
 * @author djs
 * @since FIX-ADMIN-R130
 */
@Data
@ExcelIgnoreUnannotated
public class VegHandleRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处理日期（毛菜处理间取 handle_time / 损耗行取结算时间；采摘活动取 activity_date）。
     *
     * <p>{@link DateTimeFormat} 与 {@link JsonFormat} 保持一致：不写死格式时导出走全局默认
     * {@code yyyy-MM-dd HH:mm:ss}，会带出 00:00:00，与页面显示的纯日期不一致。</p>
     */
    @ExcelProperty(value = "处理日期")
    @DateTimeFormat("yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date handleDate;

    /**
     * 作物名称。
     */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    /**
     * 统计来源：1=毛菜处理间 / 2=采摘活动（与「采摘明细」页 statSource 同码值，前端 i18n 映射中文）。
     *
     * <p>不标 {@link ExcelProperty}：导出列走 {@link #statSourceLabel}，否则 xlsx 里是裸码 1/2。</p>
     */
    private String statSource;

    /**
     * 统计来源中文（导出列，由 service 从 {@link #statSource} 派生填充）。
     *
     * <p>该来源没有独立字典（沿用「采摘明细」页的裸码 + 前端 i18n 约定），用不了 ExcelDictConvert；
     * 为一列导出单开字典还要配 Flyway + 刷 Redis 缓存，代价不成比例，故就地派生。</p>
     */
    @ExcelProperty(value = "统计来源")
    private String statSourceLabel;

    /**
     * 处理方式原始值（字典 {@code djs_pick_dest}）。
     */
    @ExcelProperty(value = "处理方式", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_pick_dest")
    private String handleMethod;

    /**
     * 地块编号（{@code t_plant_plot_info.plot_code}；采摘活动销售去向不记地块，为空）。
     */
    @ExcelProperty(value = "地块编号")
    private String plotCode;

    /**
     * 处理量(kg)。
     */
    @ExcelProperty(value = "处理量(kg)")
    private BigDecimal handleWeight;

    /**
     * 记录人姓名（SQL JOIN {@code sys_user} 取 nick_name，便于按姓名模糊搜索）。
     */
    @ExcelProperty(value = "记录人")
    private String recorderName;
}
