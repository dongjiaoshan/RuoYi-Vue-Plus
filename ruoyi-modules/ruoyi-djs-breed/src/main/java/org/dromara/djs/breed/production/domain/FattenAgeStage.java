package org.dromara.djs.breed.production.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;

/**
 * 育肥日龄阶段配置实体（A1 育肥生产配置 Tab2）。
 *
 * <p>对应表 {@code t_farm_fatten_age_stage}。客户维护的连续日龄段（如 26-42 / 43-70 / 71-140），
 * 每段可标记"是否记录生长记录"，给育肥猪生长台账录入的阶段划分用。</p>
 *
 * <p>表单语义为「整张表格批量保存」（admin 一次提交全部行）：service 端 batchSave
 * 走「软删旧行 + 重灌新行」事务，避免逐行 diff。</p>
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}，UNIQUE(tenant_id, start_age, del_unique)。</p>
 *
 * @author djs
 * @since A1
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_fatten_age_stage")
public class FattenAgeStage extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 起始日龄（天，含）。
     */
    private Integer startAge;

    /**
     * 截止日龄（天，含）。
     */
    private Integer endAge;

    /**
     * 是否记录生长记录（1 记录 / 0 不记录）。
     */
    private Integer recordGrowth;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
