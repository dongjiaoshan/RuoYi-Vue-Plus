package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 门店管理月度看板 - 一张业态卡（MGMT-MP-STORE-MONTH-001）。
 *
 * <p>最多 5 张：猪肉产品（pork + white_bar）/ 果蔬产品 / 蛋类产品 / 干货产品 / 其他产品（other）。
 * 当月三指标全无数据的卡后端<b>整卡不下发</b>（D-0045），所以下发的卡 {@code rows} 必非空，
 * 前端不必再为「空卡」出空态；一张卡都没有 = 本月整页无数据。</p>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Data
public class StoreManageCategoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业态 key：pork / vegetable / egg / dry_good / other。 */
    private String categoryKey;

    /** 业态中文名（后端给文案，mp 硬编码中文口径下前端不再拼字典）。 */
    private String categoryName;

    /** 按计量单位拆的行（可能 0 行 / 1 行 / 多行）。 */
    private List<StoreManageUnitRowVo> rows;

}
