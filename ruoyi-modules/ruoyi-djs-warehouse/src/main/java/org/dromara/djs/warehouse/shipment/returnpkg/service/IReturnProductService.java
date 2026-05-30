package org.dromara.djs.warehouse.shipment.returnpkg.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnConfirmBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnProductBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.query.ReturnProductQuery;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnProductVo;

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
}
