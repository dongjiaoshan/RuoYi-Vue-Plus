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
 * 栋舍实体（BRD-MD-002）。
 *
 * <p>对应表 {@code t_farm_barn_info}（V1 单农场，{@code tenant_id='1001'}；
 * V2 多农场启用后由 ruoyi 自带 TenantLineInnerInterceptor 自动隔离，参 ADR-0001）。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}，服务层走
 * {@link DjsBaseServiceImpl#softDelete}（D03 _open-issues #18 教训）。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_barn_info")
public class Barn extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 栋舍编码（业务码，新增前由 Service 校验同 tenant 唯一）。
     */
    private String barnCode;

    /**
     * 栋舍名称。
     */
    private String barnName;

    /**
     * 栋舍类型（字典 {@code djs_barn_type}：breeding 配种舍 / pregnant 妊娠舍 / farrow 产房 /
     * nursery 保育舍 / fattening 育肥舍 / isolation 隔离舍）。
     */
    private String barnType;

    /**
     * 容量（头位数，整数；null 表示未设置）。
     */
    private Integer capacity;

    /**
     * 当前存栏（由下游业务表事件维护，admin 不直接编辑）。
     */
    private Integer currentCount;

    /**
     * 状态（{@code 1}=启用 / {@code 0}=停用，TINYINT；与字典 djs_farm_status 含义相反，
     * 字典值仅做"农场级状态"，栋舍状态保留二态语义）。
     */
    private Integer barnStatus;

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
     * {@link org.dromara.djs.common.handler.DjsMetaObjectHandler} 写入 id，
     * 保证 UNIQUE(tenant_id, barn_code, del_unique) 不阻塞同编码复用。
     */
    private Long delUnique;

}
