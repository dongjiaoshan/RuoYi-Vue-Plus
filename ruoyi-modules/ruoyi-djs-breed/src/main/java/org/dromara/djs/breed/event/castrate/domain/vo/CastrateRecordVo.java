package org.dromara.djs.breed.event.castrate.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.castrate.domain.CastrateRecord;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阉割记录视图（BRD-EVENT-004 CASTRATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CastrateRecord.class)
public class CastrateRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime castrateDate;

    private Long operatorId;

    /** 阉割人员（原型 87 字段；存所选员工 userId）。 */
    private String castrater;

    private String remark;

    /** 216：记录卡按原型显示「性别·品种·日龄·员工名」，queryPage 时 enrich 回填。 */
    /** 猪只性别 label（djs_pig_sex 翻译，公猪页恒「公猪」；翻不到回落 code）。 */
    private String pigSexLabel;
    /** 猪只品种 label（djs_pig_breed 翻译；翻不到回落 code）。 */
    private String pigBreedLabel;
    /** 日龄（天）= NOW - birthDate；birthDate 为 null 返 null。 */
    private Integer ageDays;
    /** 阉割人员姓名（castrater userId → sys_user nickname；翻不到回落 null）。 */
    private String castraterName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
