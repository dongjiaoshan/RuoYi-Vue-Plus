package org.dromara.djs.breed.death.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 猪只死亡记录VO。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
public class PigDeathVo {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 猪只ID。
     */
    private Long pigId;

    /**
     * 猪只耳号。
     */
    private String earNo;

    /**
     * 死亡日期时间。
     */
    private LocalDateTime deathDate;

    /**
     * 死亡猪只类型（字典 pig_type：sow/boar/piglet/fattening）。
     */
    private String deathPigType;

    /**
     * 死亡分类（字典 death_type：normal/abnormal）。
     */
    private String deathKind;

    /**
     * 死亡原因（字典 death_reason）。
     */
    private String deathReason;

    /**
     * 死亡去向（字典 death_dest）。
     */
    private String deathDest;

    /**
     * 死亡重量（KG）。
     */
    private BigDecimal deathWeight;

    /**
     * 照片OSS IDs（数据库存储，逗号分隔字符串）。
     */
    private String ossIdsStr;

    /**
     * 照片OSS IDs列表（返回给前端）。
     */
    private List<String> ossIds;

    /**
     * 操作人ID。
     */
    private Long operatorId;

    /**
     * 栋舍名称。
     */
    private String barnName;

    /**
     * 栏位名称。
     */
    private String penName;

    /**
     * 录入人ID。
     */
    private Long createBy;

    /**
     * 备注。
     */
    private String remark;

    // ========== 关联的猪只信息 ==========

    /**
     * 猪只类型（sow/boar/piglet/fattening）。
     */
    private String pigType;

    /**
     * 性别（F/M）。
     */
    private String pigSex;

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
     * 日龄。
     */
    private Integer pigAge;

}
