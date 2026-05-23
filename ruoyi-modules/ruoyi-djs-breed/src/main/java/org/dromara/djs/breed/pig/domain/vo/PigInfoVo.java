package org.dromara.djs.breed.pig.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 猪只基础信息VO。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
public class PigInfoVo {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 耳号全版。
     */
    private String earTag;

    /**
     * 耳号简版。
     */
    private String earNo;

    /**
     * 性别（F=母 M=公）。
     */
    private String pigSex;

    /**
     * 猪只类型（字典 pig_type：sow/boar/piglet/fattening）。
     */
    private String pigType;

    /**
     * 品种编码。
     */
    private String pigBreedCode;

    /**
     * 品种名称。
     */
    private String pigBreedName;

    /**
     * 品系编码。
     */
    private String pigStrainCode;

    /**
     * 品系名称。
     */
    private String pigStrainName;

    /**
     * 日龄（根据出生日期计算）。
     */
    private Integer pigAge;

    /**
     * 当前状态（HB/PZ/PH/FM/DN/LC/KH/FQ/END）。
     */
    private String currentStatus;

    /**
     * 出生日期。
     */
    private LocalDate birthDate;

    /**
     * 引种日期。
     */
    private LocalDate introduceDate;

    /**
     * 栋舍ID。
     */
    private Long barnId;

    /**
     * 栏位ID。
     */
    private Long penId;

    /**
     * 进入当前状态的时间。
     */
    private LocalDateTime statusStartedAt;

    /**
     * 胎次（母猪用）。
     */
    private Integer parity;

    /**
     * 是否可回收复用。
     */
    private Boolean recyclable;

}
