package org.dromara.djs.warehouse.shipment.returnpkg.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnConfirmBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnProductBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.query.ReturnProductQuery;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnProductVo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnStoreDailyVo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnStoreGroupVo;

import java.util.Collection;
import java.util.List;

/**
 * 退货管理 Service（WMS-SHIP-001）。
 *
 * <p>3 方向：</p>
 * <ul>
 *   <li>{@code store_to_warehouse} — admin confirm 时联动 INSERT stock_flow(return_in)</li>
 *   <li>{@code customer_to_store} / {@code warehouse_to_supplier} — 仅录入，不联动 stock_flow</li>
 * </ul>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
public interface IReturnProductService {

    TableDataInfo<ReturnProductVo> queryPageList(ReturnProductQuery query, PageQuery pageQuery);

    /**
     * admin 外层「门店 + 当日」汇总分页（图 153）：按 退货日期(apply_time 截到天) + store_id 内存聚合。
     *
     * <p>每行 = 某门店某天全部退货行的汇总（品种数 / 退货重量 / 确认重量 / 重量差异 /
     * 最近确认时间 / 最近确认人）。明细下钻复用 {@link #queryPageList} 带 storeId + applyDateFrom/To。</p>
     *
     * @return 汇总行分页（按退货日期 + 门店倒序）
     */
    TableDataInfo<ReturnStoreDailyVo> queryStoreDailyPage(ReturnProductQuery query, PageQuery pageQuery);

    List<ReturnProductVo> queryList(ReturnProductQuery query);

    ReturnProductVo queryById(Long id);

    /**
     * 新增退货记录（admin / mp 共用；mp 端 status 默认 pending，admin 端可直接传 confirmed）。
     */
    Long insertByBo(ReturnProductBo bo);

    /**
     * 编辑（仅 pending 状态可编辑）。
     */
    int updateByBo(ReturnProductBo bo);

    /**
     * 软删除（DjsBaseServiceImpl#softDelete 范式）。
     */
    int deleteByIds(Collection<Long> ids);

    /**
     * admin 确认退货：
     * <ol>
     *   <li>UPDATE is_confirm=1 / confirm_user / confirm_time / confirm_weight / return_status='confirmed'</li>
     *   <li>仅 {@code return_direction='store_to_warehouse'} 触发 INSERT stock_flow (flow_type='return_in', IN)</li>
     * </ol>
     */
    void confirmReturn(Long id, ReturnConfirmBo bo);

    /**
     * mp 退货管理（原型图 57）：按 {@code store_id + return_status} 分组的待确认 / 已确认列表。
     *
     * <p>仅取 {@code store_to_warehouse} 方向（mp 退货管理只管门店→仓库这条链；
     * 其他 2 方向是 admin 端占位录入，不进 mp 分组卡）。</p>
     *
     * <p>只含「当天退货」：按业务日期 {@code apply_time} 落在今天（Asia/Shanghai）过滤，
     * 配合「当天退货当天确认」（D-FIX-24 决策 #6a）。</p>
     *
     * @return 门店分组卡列表（门店名 + 状态 + 品种数 + 退货时间），按状态（待确认在前）+ 时间倒序
     */
    List<ReturnStoreGroupVo> listPendingGroups();

    /**
     * mp 退货确认详情（原型图 58）：拉某门店某状态下的全部退货行（逐产品）。
     *
     * @param storeId      门店 ID
     * @param returnStatus 退货状态 {@code pending} / {@code confirmed}
     * @return 该门店该状态下的退货行（含 product_name / return_weight / confirm_weight），按 apply_time 倒序
     */
    List<ReturnProductVo> listByStoreAndStatus(Long storeId, String returnStatus);
}
