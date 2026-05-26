package org.dromara.djs.breed.event.breeding.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 母猪配种记录实体（BRD-EVENT-002 BREED）。
 *
 * <p>对应表 {@code t_farm_pig_breeding}（SYS-INIT-001 DDL）。
 * 由 {@code BreedingServiceImpl} 在配种事件录入时 INSERT；触发状态机
 * {@code HB/DN/LC/KH/FQ → PZ} + 母猪 mating_count +1 + last_mating_date 更新（D5 audit a-1）。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_breeding")
public class PigBreeding extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 母猪 pig_info.id。 */
    private Long pigId;

    /** 母猪耳号。 */
    private String earNo;

    /** 配种日期。 */
    private LocalDateTime breedingDate;

    /** 配种方式（字典 djs_breeding_type；1=本场公猪 2=精液产品；本工程使用 varchar(16) 允许扩展）。 */
    private String breedingType;

    /** 公猪耳号（breedingType=1 时必填；2 时可空）。 */
    private String boarEarNo;

    /** 精液供应商（breedingType=2 时填）。 */
    private String semenSupplier;

    /** 精液批号（breedingType=2 时填）。 */
    private String semenBatchNo;

    /** 母猪本次配种时胎次（与 pig.parity 一致，service 端填）。 */
    private Integer parity;

    /** 录入人员 user_id。 */
    private Long operatorId;

    /** 栋舍名称（冗余便于查询展示）。 */
    private String barnName;

    /** 栏位名称（冗余）。 */
    private String penName;

    /** 凭证图片 OSS IDs 逗号分隔（配种现场照片，与 PigIntroduce.proofOssIds 对齐）。 */
    private String proofOssIds;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 备注。 */
    private String remark;
}
