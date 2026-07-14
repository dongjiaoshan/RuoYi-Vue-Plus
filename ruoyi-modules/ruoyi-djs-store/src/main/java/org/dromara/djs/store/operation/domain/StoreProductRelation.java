package org.dromara.djs.store.operation.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 门店产品关联实体（STR-OP-001）。
 *
 * <p>对应表 {@code t_store_product_relation}（V202606141210 重建）。配置「门店能卖哪些 SKU」，
 * admin 用 {@code <el-transfer>} 多对多选产品全量 diff 同步：右侧选中 = INSERT（{@code is_active=1}），
 * 移除 = 软删（{@link org.dromara.djs.common.base.DjsBaseServiceImpl#softDelete}）。</p>
 *
 * <p>软删走 {@code delFlag} + {@code delUnique}；UNIQUE(tenant_id, store_id, product_id, del_unique)
 * 保证「软删后同门店同产品重新关联」不冲突。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_product_relation")
public class StoreProductRelation extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 门店 ID（FK → {@code t_md_store.id}）。
     */
    private Long storeId;

    /**
     * 产品 ID（FK → {@code t_warehouse_product_info.id}）。
     */
    private Long productId;

    /**
     * 启用状态（字典 {@code djs_common_status}：1=启用 / 2=停用）。
     */
    private Integer isActive;

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
