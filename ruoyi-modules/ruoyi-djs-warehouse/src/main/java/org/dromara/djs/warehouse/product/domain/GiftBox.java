package org.dromara.djs.warehouse.product.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 礼盒组件清单实体（WMS-MD-002）。
 *
 * <p>对应表 {@code t_warehouse_gift_box}：一个礼盒（{@code product_type=3}）N 个组件 SKU；
 * 与 {@link ProductInfo} 通过 {@code box_product_id} 关联（FK → product_info.id）。
 * 组件本身也指向 product_info（{@code component_product_id} FK）。</p>
 *
 * <p>D7 closing 已确认走独立关系表方案（替代 JSON 字段）：</p>
 * <ul>
 *   <li>利于查询 / JOIN / 索引（看哪些礼盒含某 SKU 一句 SQL 搞定）</li>
 *   <li>service 边界拆 {@code IGiftBoxService} 独立维护</li>
 *   <li>编辑走"覆盖式替换"：先 softDelete 老组件再 insert 新（V1 不做 diff）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_gift_box")
public class GiftBox extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * FK → t_warehouse_product_info.id（product_type=3 礼盒主体）。
     */
    private Long boxProductId;

    /**
     * FK → t_warehouse_product_info.id（组件 SKU；产品类型 ≠ 3，禁止嵌套礼盒）。
     */
    private Long componentProductId;

    /**
     * 组件数量。
     */
    private BigDecimal componentCount;

    /**
     * 组件单位（冗余，便于展示，避免 JOIN component_product 拿单位）。
     */
    private String componentUnit;

    /**
     * 排序。
     */
    private Integer componentSort;

    @TableLogic
    private String delFlag;

    private Long delUnique;

}
