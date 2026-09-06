package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 门店管理月度看板顶层 VO（MGMT-MP-STORE-MONTH-001）。
 *
 * <p>管理板块「门店管理」tab 的唯一数据源：顶部 3 个品类数 + 下方 4 张业态卡。</p>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Data
public class StoreManageMonthlyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月份 yyyy-MM（回显给前端，避免前后端各算一次月份口径不一致）。 */
    private String month;

    /** 统计门店；null = 全部门店合计。 */
    private Long storeId;

    /** 猪肉品类数：当月到店的 pork + white_bar 产品去重数。 */
    private Integer porkProductCount;

    /** 果蔬品类数：当月到店的 vegetable 产品去重数。 */
    private Integer vegProductCount;

    /** 其他品类数：当月到店的 egg + dry_good 产品去重数。 */
    private Integer otherProductCount;

    /** 4 张业态卡（顺序固定：猪肉 / 果蔬 / 蛋类 / 干货）。 */
    private List<StoreManageCategoryVo> categories;

}
