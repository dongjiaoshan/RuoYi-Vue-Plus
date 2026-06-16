package org.dromara.djs.breed.event.breeding.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.breeding.domain.PigBreeding;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配种记录视图（BRD-EVENT-002 BREED）。
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigBreeding.class)
public class PigBreedingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime breedingDate;

    private String breedingType;
    private String boarEarNo;
    /** 配种精液字典 code（djs_semen；FIX-BREEDING-001 #21）。 */
    private String semenCode;
    private Integer parity;
    private Long operatorId;
    /** 配种人员姓名（operatorId → USER_ID_TO_NICKNAME 翻译；记录卡展示；翻不到回落 null）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;
    private String barnName;
    private String penName;
    /** 母猪当前 lifecycle code（JOIN t_farm_pig_info.current_status；记录卡实时状态）。 */
    private String sowStatus;
    /** 母猪进入当前状态的天数（DATEDIFF(NOW, status_started_at)；记录卡状态天数）。 */
    private Integer statusDays;
    /** 母猪日龄（DATEDIFF(NOW(), p.birth_date)；记录卡最后一列「日龄」格；缺出生日期 → null）。 */
    private Integer ageDays;
    /**
     * 上一次状态 lifecycle code（该母猪当前状态之前的状态）。
     * 取 t_farm_status_record 该 pig_id 最新一条的 old_status（前端按 lifecycle 字典翻中文）。
     * 无状态流水 → null（前端该段不渲染）。
     */
    private String previousStatus;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
