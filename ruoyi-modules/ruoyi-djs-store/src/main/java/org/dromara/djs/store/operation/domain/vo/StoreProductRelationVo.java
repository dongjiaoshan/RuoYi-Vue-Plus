package org.dromara.djs.store.operation.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.store.operation.domain.StoreProductRelation;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店产品关联 VO（STR-OP-001）。
 *
 * <p>{@code productName} / {@code productUnit} / {@code productSpec} 由 service 层 JOIN
 * {@code t_warehouse_product_info} 回填，供 admin {@code el-transfer} 右侧已关联列表展示。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Data
@AutoMapper(target = StoreProductRelation.class)
public class StoreProductRelationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联记录 ID。
     */
    private Long id;

    /**
     * 门店 ID。
     */
    private Long storeId;

    /**
     * 产品 ID。
     */
    private Long productId;

    /**
     * 产品名称（service 层 JOIN 回填）。
     */
    private String productName;

    /**
     * 产品单位（service 层 JOIN 回填）。
     */
    private String productUnit;

    /**
     * 产品规格（service 层 JOIN 回填）。
     */
    private String productSpec;

    /**
     * 启用状态（字典 {@code djs_common_status}：1=启用 / 2=停用）。
     */
    private Integer isActive;

}
