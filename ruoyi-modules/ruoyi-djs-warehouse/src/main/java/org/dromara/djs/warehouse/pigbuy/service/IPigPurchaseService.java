package org.dromara.djs.warehouse.pigbuy.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.pigbuy.domain.bo.PigPurchaseBo;
import org.dromara.djs.warehouse.pigbuy.domain.query.PigPurchaseQuery;
import org.dromara.djs.warehouse.pigbuy.domain.vo.PigPurchaseVo;

import java.util.List;

/**
 * 外购猪只到货登记 Service（FIX-WMS-MP-PIGBUY-001）。
 *
 * <p>外购活猪 / 白条独立到货登记，作为燎毛 / 分割的另一来源（与自养出栏并列）；
 * 口径 D-FIX-7 #4.3 不进养殖 BRD 引种。本卡范围：登记 + 列表查询。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-PIGBUY-001
 */
public interface IPigPurchaseService {

    /**
     * mp 端提交一笔外购猪只到货登记（生成 purchaseNo + 置 pending + INSERT）。
     *
     * @param bo 入参（来源 / 数量 / 重量 / 供应商 / 到货时间 / 凭证）
     * @return 到货记录主键 id
     */
    Long submitPurchase(PigPurchaseBo bo);

    /**
     * 分页查询。
     */
    TableDataInfo<PigPurchaseVo> queryPageList(PigPurchaseQuery query, PageQuery pageQuery);

    /**
     * 不分页（导出 / 报表用）。
     */
    List<PigPurchaseVo> queryList(PigPurchaseQuery query);

    /**
     * 详情。
     */
    PigPurchaseVo queryById(Long id);

}
