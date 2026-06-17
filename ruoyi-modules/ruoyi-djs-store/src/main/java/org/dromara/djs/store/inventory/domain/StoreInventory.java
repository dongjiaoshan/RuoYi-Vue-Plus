package org.dromara.djs.store.inventory.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 门店独立库存实体（{@code t_store_inventory}，WSA-003 阶段0 已建表，本类仅补 ORM 层）。
 *
 * <p>一门店一产品一行结存：门店盘点期初读「当前结存」{@code stockQty}，盘点完成回写「期末结存」
 * （期初 = 上一期期末，自然兼容跨日空档）。V1 不实时改库存（销售/退回靠盘点对账），与「期末实盘」语义一致。</p>
 *
 * <p>UNIQUE(tenant_id, store_id, product_id, del_unique)：同门店同产品唯一一行结存。</p>
 *
 * @author djs
 * @since WSA-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_inventory")
public class StoreInventory extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花）。 */
    @TableId
    private Long id;

    /** 门店 FK → {@code t_md_store.id}。 */
    private Long storeId;

    /** 产品 FK → {@code t_warehouse_product_info.id}（雪花主键，非业务码）。 */
    private Long productId;

    /** 门店结存数量/重量（盘点期末回写、期初读取）。 */
    private BigDecimal stockQty;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一辅助列。 */
    private Long delUnique;
}
