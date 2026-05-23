package org.dromara.djs.breed.pig.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 猪只基础信息实体。
 *
 * <p>对应表 {@code t_farm_pig_info}。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_info")
public class PigInfo extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
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
     * 生命周期 ID（耳号复用次数+1）。
     */
    private Integer lifecycleId;

    /**
     * 是否可回收复用（1=是 0=否）。
     */
    private Boolean recyclable;

    /**
     * 性别（F=母 M=公）。
     */
    private String pigSex;

    /**
     * 猪只类型（字典 pig_type：sow/boar/piglet/fattening）。
     */
    private String pigType;

    /**
     * 品种编码（引用 t_farm_breed_info）。
     */
    private String pigBreedCode;

    /**
     * 品系编码（引用 t_farm_breed_info）。
     */
    private String pigStrainCode;

    /**
     * 当前状态（HB/PZ/PH/FM/DN/LC/KH/FQ/END）。
     */
    private String currentStatus;

    /**
     * 进入当前状态的时间。
     */
    private LocalDateTime statusStartedAt;

    /**
     * 终止原因（END 状态时填：DEAD/CULL/MARKET）。
     */
    private String endReason;

    /**
     * 父猪耳号（仔猪用）。
     */
    private String fatherEar;

    /**
     * 母猪耳号（仔猪用）。
     */
    private String motherEar;

    /**
     * 出生日期。
     */
    private LocalDate birthDate;

    /**
     * 引种日期。
     */
    private LocalDate introduceDate;

    /**
     * 引种方式（字典 introduce_from：internal/external）。
     */
    private String introduceType;

    /**
     * 供应商 ID（外部引种用）。
     */
    private Long supplierId;

    /**
     * 胎次（母猪用）。
     */
    private Integer parity;

    /**
     * 当前栋舍 ID。
     */
    private Long barnId;

    /**
     * 当前栏位 ID。
     */
    private Long penId;

    /**
     * 最近一次配种记录 ID。
     */
    private Long matingId;

    /**
     * 是否被预约出栏（1=是 0=否）。
     */
    private Boolean isAppointed;

    /**
     * 预约门店 ID。
     */
    private Long storeId;

    /**
     * 乐观锁版本。
     */
    private Integer version;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 品种名称（关联查询用，非数据库字段）。
     */
    @TableField(exist = false)
    private String pigBreedName;

    /**
     * 品系名称（关联查询用，非数据库字段）。
     */
    @TableField(exist = false)
    private String pigStrainName;

}
