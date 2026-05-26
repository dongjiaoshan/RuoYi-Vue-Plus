package org.dromara.djs.warehouse.location.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 库位信息实体（WMS-MD-001）。
 *
 * <p>对应表 {@code t_warehouse_location_info}（V202605290900 建）：</p>
 * <ul>
 *   <li>下游 {@code t_warehouse_location_stock} 通过 {@code location_id} 关联（本 ticket 同建）</li>
 *   <li>删除前校验：库位若存在 {@code product_stock > 0} 的库存记录，应用层拒绝（service 抛 {@code location.has_stock}）</li>
 * </ul>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（{@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
 * 在 delFlag='1' 时把 id 写入 delUnique，保证 UNIQUE(tenant_id, location_code, del_unique) 不阻塞同编码复用）。
 * 服务层走 {@link DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_location_info")
public class LocationInfo extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 库位业务码（用户手填；UNIQUE(tenant_id, location_code, del_unique)）。
     */
    private String locationCode;

    /**
     * 库位名称（例 冻品库 / 蔬菜鲜品库）。
     */
    private String locationName;

    /**
     * 库位类型（字典 {@code djs_location_type}：frozen / fresh / dry / ambient / medicine / feed）。
     *
     * <p>⏳ 客户未明示该字典枚举范围，先 seed 业内典型 6 类；客户回复后可在 admin 字典页自由增删。</p>
     */
    private String locationType;

    /**
     * 缩略图（OSS objectName，单个）。
     */
    private String locationThumb;

    /**
     * 原图（OSS objectName 逗号分隔，多张）。
     */
    private String locationImg;

    /**
     * 库位状态（字典 {@code djs_location_status}：1=启用 / 2=停用）。
     */
    private Integer locationStatus;

    /**
     * 容量（kg / m³，单位由 {@link #locationType} 决定）。
     */
    private BigDecimal capacity;

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
     * 软删唯一性辅助列：未删时 0；软删时由 {@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
     * 写入 id，保证 UNIQUE(tenant_id, location_code, del_unique) 不阻塞重新启用同编码。
     */
    private Long delUnique;

}
