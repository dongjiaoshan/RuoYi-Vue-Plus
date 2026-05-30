package org.dromara.djs.plant.pick.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.plant.pick.domain.PickActivity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 采摘活动 VO（PLT-PLAN-002）。
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
@AutoMapper(target = PickActivity.class)
public class PickActivityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "活动编码")
    private String activityNo;

    @ExcelProperty(value = "活动名称")
    private String activityName;

    @ExcelProperty(value = "活动日期")
    private LocalDate activityDate;

    @ExcelProperty(value = "活动状态")
    private String activityStatus;

    private Long cropId;

    @ExcelProperty(value = "作物")
    private String cropName;

    @ExcelProperty(value = "涉及地块数")
    private Integer totalPlot;

    @ExcelProperty(value = "当日采摘汇总(kg)")
    private BigDecimal totalYield;

    @ExcelProperty(value = "游客人数")
    private Integer visitorCount;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;
}
