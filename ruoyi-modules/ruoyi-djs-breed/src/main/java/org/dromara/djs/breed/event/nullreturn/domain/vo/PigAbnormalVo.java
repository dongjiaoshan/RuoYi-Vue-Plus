package org.dromara.djs.breed.event.nullreturn.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.nullreturn.domain.PigAbnormal;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 返空流记录视图（BRD-EVENT-002 NULL_RETURN）。 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigAbnormal.class)
public class PigAbnormalVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime abnormalDate;

    /** xlsx 字典码 R/A/N（前端按字典 djs_abnormal_type 翻译）。 */
    private String abnormalType;

    private Long relatedBreedingId;
    private Long operatorId;

    /**
     * 录入人员姓名（来自 {@code sys_user.nick_name}）。
     * <p>USER_ID_TO_NICKNAME 走 NicknameTranslationImpl 取 sys_user.nick_name 中文名。</p>
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /** 母猪日龄（天）= 异常日期 - pig.birthDate（缺时 fallback introduceDate）；均空 → null（queryPage enrich）。 */
    private Integer dayAge;

    /** 母猪胎次（来自 pig.parity，queryPage enrich）。 */
    private Integer parity;

    /** 当前状态持续天数（异常日期至今）；queryPage enrich。 */
    private Integer durationDays;

    /** 母猪当前 lifecycle 状态（触发状态机后；queryPage enrich 取 pig.current_status）。 */
    private String currentStatus;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
