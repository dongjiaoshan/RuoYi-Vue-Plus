package org.dromara.djs.warehouse.demand.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;
import java.util.List;

/**
 * 需求列表查询参数（WMS-DEMAND-001）。
 *
 * <p>4 业态列表共享本 Query；admin 端各业态 .vue 在调 API 时把 {@code productType}
 * 写死本业态字面量（如 {@code 'white_bar'}），实现"4 业态 4 个独立列表页 + 共用 API"。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DemandManageQuery extends BaseEntity {

    /** 单号 LIKE 中部匹配。 */
    private String demandNo;

    /** 产品名称 LIKE 中部匹配（门店需求列表筛选用）。 */
    private String productName;

    /** 业态过滤；admin 4 业态列表页固定值（white_bar / vegetable / gift_box / other）。 */
    private String productType;

    /** 业态多选（R70 产品类型下拉多选）。非空时按 IN 过滤，优先于单值 productType。 */
    private List<String> productTypes;

    /** 状态过滤（单选）。 */
    private String demandStatus;

    /** 状态多选（R70 需求状态下拉多选）。非空时按 IN 过滤，优先于单值 demandStatus。 */
    private List<String> demandStatuses;

    /**
     * <b>门店视角</b>派生态多选（字典 {@code djs_store_demand_status}，V6-R197）。
     *
     * <p>与 {@link #demandStatus} / {@link #demandStatuses}（仓库落库 7 态）是两个维度，可叠加：
     * 门店态是「仓库态 + 是否收货 + 到店量」算出来的，没有对应的落库列，故按
     * {@code StoreDemandStatusMapping.sqlPredicateAny} 下推成 WHERE 片段。
     * 未知态 / DELETED 由 mapping 直接报错，不静默丢弃。</p>
     */
    private List<String> storeDemandStatuses;

    /** 门店 ID。 */
    private Long storeId;

    /** 门店 ID 多选（R70 需求门店下拉多选）。非空时按 IN 过滤，优先于单值 storeId。 */
    private List<Long> storeIds;

    /** 产品 ID 精确过滤（0613-11 确认页：只看某产品某日的所有门店需求单）。 */
    private Long productId;

    /** 需求日期精确过滤（0613-11 确认页：配合 productId 锁定某日某产品）。 */
    private LocalDate demandDate;

    /** 需求日期起始。 */
    private LocalDate beginDate;

    /** 需求日期截止。 */
    private LocalDate endDate;
}
