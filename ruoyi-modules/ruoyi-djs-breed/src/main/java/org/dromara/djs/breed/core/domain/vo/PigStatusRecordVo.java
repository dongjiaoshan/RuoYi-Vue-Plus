package org.dromara.djs.breed.core.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.core.domain.PigStatusRecord;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 状态变更记录出参 VO（BRD-CORE-001）。
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigStatusRecord.class)
public class PigStatusRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "猪只ID")
    private Long pigId;

    @ExcelProperty(value = "耳号")
    private String earNo;

    @ExcelProperty(value = "原状态")
    private String oldStatus;

    @ExcelProperty(value = "新状态")
    private String newStatus;

    @ExcelProperty(value = "事件")
    private String eventType;

    @ExcelProperty(value = "关联事件ID")
    private Long relatedEventId;

    @ExcelProperty(value = "停留天数")
    private Integer durationDays;

    @ExcelProperty(value = "变更时间")
    private LocalDateTime changeTime;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    @ExcelProperty(value = "变更人ID")
    private Long createBy;

    /** 变更人姓名（USER_ID_TO_NICKNAME 取 sys_user.nick_name 中文名；create_by 即触发事件的用户）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    @ExcelProperty(value = "变更人")
    private String createByName;
}
