package org.dromara.djs.breed.event.die.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.die.domain.PigDeath;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 死亡记录视图（BRD-EVENT-004 DIE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigDeath.class)
public class PigDeathVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deathDate;

    private String deathPigType;

    /** 死亡猪只性别原始码 F/M（enrich 批查 t_farm_pig_info 取；前端 F→母 M→公）。 */
    private String pigSex;

    /**
     * 死亡时日龄（天，事件时口径 = deathDate - birth_date）。
     * <p>service enrich 批查 t_farm_pig_info 取 birth_date 算；缺 birth_date 回落 introduce_date；
     * 两者均空 → null（mp 卡片该格不渲染）。</p>
     */
    private Integer ageDays;

    /** 猪只品系中文名（t_farm_pig_info.pig_strain_code → 主数据/字典翻译；缺则 null）。 */
    private String pigStrainName;

    /** 猪只品种中文名（t_farm_pig_info.pig_breed_code → 主数据/字典翻译；缺则 null）。 */
    private String pigBreedName;

    private String deathKind;
    private String deathReason;
    private String deathDest;
    private BigDecimal deathWeight;
    private String ossIds;

    /** 死亡照片可访问 URL 列表（后端 JOIN sys_oss resolve；mp image 直接渲染，避免裸 ossId 带不了 Bearer token）。 */
    private List<String> imageUrls;

    private Long operatorId;

    /** 死亡记录人员 userId（EmployeePicker 选中）。 */
    private Long recorderId;

    /** 死亡记录人员姓名（recorderId → USER_ID_TO_NICKNAME 翻译；记录列表显名不显 ID）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "recorderId")
    private String recorderName;

    private String barnName;
    private String penName;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
