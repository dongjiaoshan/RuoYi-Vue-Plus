package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 门店管理月度看板 - 一张业态卡（MGMT-MP-STORE-MONTH-001）。
 *
 * <p>固定 4 张：猪肉产品（pork + white_bar）/ 果蔬产品 / 蛋类产品 / 干货产品。
 * {@code rows} 为空表示该业态本月与上月都没有任何需求 / 销售 / 退回，前端出空态。</p>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Data
public class StoreManageCategoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业态 key：pork / vegetable / egg / dry_good。 */
    private String categoryKey;

    /** 业态中文名（后端给文案，mp 硬编码中文口径下前端不再拼字典）。 */
    private String categoryName;

    /** 按计量单位拆的行（可能 0 行 / 1 行 / 多行）。 */
    private List<StoreManageUnitRowVo> rows;

}
