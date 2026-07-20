package org.dromara.djs.breed.farm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;

/**
 * 栏位实体（BRD-MD-002）。
 *
 * <p>对应表 {@code t_farm_barn_pen}（每个栏位归属一个栋舍 {@code barn_id}；
 * V1 单农场只走 tenant_id，参 ADR-0001）。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}，服务层走
 * {@link DjsBaseServiceImpl#softDelete}（D03 _open-issues #18 教训）。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_barn_pen")
public class Pen extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 所属栋舍 ID（不允许跨栋舍迁移：栏位编辑时 barn_id 锁死，需迁移请删除重建）。
     */
    private Long barnId;

    /**
     * 栏位编码（业务码，新增前由 Service 校验 (tenant_id, barn_id, pen_code) 唯一）。
     */
    private String penCode;

    /**
     * 栏位名称。
     */
    private String penName;

    /**
     * 栏位类型（字典 {@code djs_pen_type}：male 公栏 / female 母栏 / stall 限位栏 / group 群栏）。
     */
    private String penType;

    /**
     * 容量（头位数）。
     */
    private Integer capacity;

    /**
     * 当前存栏（由下游业务表事件维护，admin 不直接编辑）。
     */
    private Integer currentCount;

    /**
     * 状态（{@code 1}=启用 / {@code 0}=停用，TINYINT）。
     */
    private Integer penStatus;

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
     * 软删唯一性辅助列：未删时 0；软删时由
     * {@link org.dromara.djs.common.handler.DjsMetaObjectHandler} 写入 id。
     */
    private Long delUnique;

}
