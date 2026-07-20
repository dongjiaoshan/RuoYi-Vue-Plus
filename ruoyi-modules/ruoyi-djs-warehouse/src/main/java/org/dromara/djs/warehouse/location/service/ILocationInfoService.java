package org.dromara.djs.warehouse.location.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.location.domain.bo.LocationInfoBo;
import org.dromara.djs.warehouse.location.domain.query.LocationInfoQuery;
import org.dromara.djs.warehouse.location.domain.vo.LocationCardSummaryVo;
import org.dromara.djs.warehouse.location.domain.vo.LocationInfoVo;

import java.util.Collection;
import java.util.List;

/**
 * 库位 Service（WMS-MD-001）。
 *
 * @author djs
 * @since WMS-MD-001
 */
public interface ILocationInfoService {

    /**
     * 分页查询库位列表。
     */
    TableDataInfo<LocationInfoVo> queryPageList(LocationInfoQuery query, PageQuery pageQuery);

    /**
     * 查询库位列表（不分页，给导出 / 下游下拉用）。
     */
    List<LocationInfoVo> queryList(LocationInfoQuery query);

    /**
     * 根据 ID 查询单条。
     */
    LocationInfoVo queryById(Long id);

    /**
     * 新增库位。
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(LocationInfoBo bo);

    /**
     * 修改库位（{@code locationCode} 不允许修改）。
     *
     * @return 受影响行数（成功 1）
     */
    int updateByBo(LocationInfoBo bo);

    /**
     * 行内切换库位状态（仅更新 {@code location_status} 单字段，不碰其它字段）。
     *
     * @param id     库位 ID
     * @param status 目标状态（字典 {@code djs_common_status}：1=启用 / 2=停用）
     * @return 受影响行数（成功 1）
     */
    int updateStatus(Long id, Integer status);

    /**
     * 软删除库位（支持批量）。
     *
     * <p>删除前校验：若库位下仍有 {@code product_stock > 0} 的库存明细，抛
     * {@code ServiceException("location.has_stock")}。</p>
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 库位类型卡片汇总（库位一览页顶部 2×4 网格）。
     *
     * <p>以 {@code djs_location_type} 8 类字典 value 作为 base，按类聚合库位数 /
     * 在库产品数 / 当前库存 / 今日入出库量 / 最近盘点；某类无数据也返 1 行（量为 0），
     * 保证返回固定 8 行供前端固定布局。</p>
     *
     * @return 8 行卡片汇总（顺序按字典 dict_sort）
     */
    List<LocationCardSummaryVo> getCardSummary();

}
