package org.dromara.djs.plant.zone.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.plant.zone.domain.PlotZone;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 片区视图对象（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PlotZone.class)
public class PlotZoneVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 片区 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 片区业务码。
     */
    @ExcelProperty(value = "片区编码")
    private String zoneCode;

    /**
     * 片区名称。
     */
    @ExcelProperty(value = "片区名称")
    private String zoneName;

    /**
     * 片区说明。
     */
    @ExcelProperty(value = "说明")
    private String zoneDesc;

    /**
     * 所属大区。
     */
    @ExcelProperty(value = "所属大区")
    private String zoneBelong;

    /**
     * 管理地块数量（聚合）：该片区下未删除地块数（{@code t_plant_plot_info.zone_id = id AND del_flag='0'}）。
     * 由 {@code PlotZoneServiceImpl} 批量 GROUP BY 回填，避免 N+1。
     */
    @ExcelProperty(value = "管理地块数量")
    private Long plotCount;

    /**
     * 状态（字典 sys_normal_disable）。
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_normal_disable")
    private Integer zoneStatus;

    /**
     * 创建时间。
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新时间。
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 更新人 ID（{@code sys_user.user_id}），供 {@link Translation} 反射取数翻译成 updateByName。
     */
    private Long updateBy;

    /**
     * 更新人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "更新人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "updateBy")
    private String updateByName;
}
