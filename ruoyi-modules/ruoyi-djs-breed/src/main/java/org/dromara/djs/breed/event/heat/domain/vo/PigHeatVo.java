package org.dromara.djs.breed.event.heat.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.heat.domain.PigHeat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 查情记录视图（BRD-EVENT-002 OESTRUS）。 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigHeat.class)
public class PigHeatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime heatDate;

    private String heatResult;
    private Integer isPregnantConfirmed;
    private Long operatorId;

    /** 录入人姓名（row97：operator_id → USER_ID_TO_NICKNAME 翻译；记录列表显名不显 ID）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /** 断奶后天数（row97：查情日期 - 该母猪最近一条断奶记录 weaning_date；无断奶记录留 null）。 */
    private Integer daysAfterWeaning;

    /** 母猪日龄（row97：查情日期 - 母猪出生日期；无出生日期留 null）。 */
    private Integer dayAge;

    /** 母猪胎次（row97：取 t_farm_pig_info.parity）。 */
    private Integer parity;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
