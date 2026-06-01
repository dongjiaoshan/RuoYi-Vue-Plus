package org.dromara.djs.store.operation.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 门店产品关联全量同步 BO（STR-OP-001）。
 *
 * <p>admin {@code el-transfer} 保存时提交：{@code storeId} + 右侧全部已关联产品 ID 列表
 * {@code productIds}。service 按 store_id 做全量 diff（新增 INSERT / 移除 softDelete）。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Data
public class StoreProductRelationSyncBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店 ID。
     */
    @NotNull(message = "门店不能为空")
    private Long storeId;

    /**
     * 目标已关联产品 ID 全集（右侧 transfer 列表；空列表表示清空该门店全部关联）。
     */
    @NotNull(message = "产品列表不能为空")
    private List<Long> productIds;

}
