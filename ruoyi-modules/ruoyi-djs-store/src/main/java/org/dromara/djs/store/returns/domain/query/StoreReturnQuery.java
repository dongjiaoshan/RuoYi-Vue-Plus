package org.dromara.djs.store.returns.domain.query;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 门店退回管理查询 query（STR-RETURN-001）。
 *
 * @author djs
 * @since STR-RETURN-001
 */
@Data
public class StoreReturnQuery {

    /** 退回单号模糊。 */
    private String returnNo;

    /** 门店精确。 */
    private Long storeId;

    /** 门店多选（R70 退回门店下拉多选）。非空时按 IN 过滤，优先于单值 storeId。 */
    private List<Long> storeIds;

    /** 产品精确。 */
    private Long productId;

    /** 产品多选（R70 退回产品下拉多选）。非空时按 IN 过滤，优先于单值 productId。 */
    private List<Long> productIds;

    /** 产品名称模糊（服务端先查 t_warehouse_product_info 得 id 集下推，保证跨页搜索/分页 total 正确）。 */
    private String productName;

    /**
     * 产品业态 tab（<b>三值</b>，row10）：
     * {@code pork}=猪肉类（pork / white_bar） · {@code vegetable}=果蔬（只认 vegetable） ·
     * {@code other}=<b>其余全部</b>（干货 / 蛋类 / 礼盒 / 其他 / belong_type 为空 / 产品已删）。
     *
     * <p>服务端按 belong_type 解析产品 id 集下推过滤，取代前端对当前页切片（分页 total 才正确）。
     * 「其余全部」而非白名单：保证任何一条退回记录都能在某个 tab 里被看到。</p>
     */
    private String belongCategory;

    /** 退回方向精确。 */
    private String returnDirection;

    /** 退货状态精确（djs_store_return_status：pending/received）。 */
    private String returnStatus;

    /** 退回日期下界（含）。 */
    private LocalDate returnDateFrom;

    /** 退回日期上界（含）。 */
    private LocalDate returnDateTo;
}
