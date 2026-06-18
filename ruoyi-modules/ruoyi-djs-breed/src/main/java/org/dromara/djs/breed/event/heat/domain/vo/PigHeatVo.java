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

    /**
     * 查情时母猪状态码（字典 {@code djs_pig_lifecycle}）。近似取 pig.currentStatus —— OESTRUS 方案A 查情不切母猪态，
     * 查情后状态不变，故用当前状态近似「查情时状态」；前端走 statusLabel 翻译。pig 不存在留 null。
     */
    private String heatStatus;

    /**
     * 状态持续天数 = 查情日期 - pig.statusStartedAt（进入当前状态时间）。statusStartedAt 为 null 留 null。
     */
    private Integer statusDays;

    /** 母猪日龄（row97：查情日期 - 母猪出生日期；无出生日期留 null）。 */
    private Integer dayAge;

    /** 母猪胎次（row97：取 t_farm_pig_info.parity）。 */
    private Integer parity;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
