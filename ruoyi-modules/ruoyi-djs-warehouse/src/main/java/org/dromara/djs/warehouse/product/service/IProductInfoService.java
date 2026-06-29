package org.dromara.djs.warehouse.product.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo;
import org.dromara.djs.warehouse.product.domain.bo.ProductInfoBo;
import org.dromara.djs.warehouse.product.domain.query.ProductInfoQuery;
import org.dromara.djs.warehouse.product.domain.vo.ProductFlowRecordVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductProductionRecordVo;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 产品 / 商品 / 礼盒 Service（WMS-MD-002）。
 *
 * @author djs
 * @since WMS-MD-002
 */
public interface IProductInfoService {

    /**
     * 分页查询产品列表（不携带礼盒组件清单）。
     */
    TableDataInfo<ProductInfoVo> queryPageList(ProductInfoQuery query, PageQuery pageQuery);

    /**
     * 查询产品列表（不分页，给导出 / 下游下拉用）。
     */
    List<ProductInfoVo> queryList(ProductInfoQuery query);

    /**
     * 查询单条详情。
     */
    ProductInfoVo queryById(Long id);

    /**
     * 新增产品。
     *
     * <p>差异校验（djs_product_type 已废弃 3 礼盒）：</p>
     * <ul>
     *   <li>productType=1 自产 → belongType 必填；礼盒 = 自产 + belongType=gift_box（独立成品、跳过规格必填）</li>
     *   <li>productType=2 外购 → supplierId 必填</li>
     * </ul>
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(ProductInfoBo bo);

    /**
     * 修改产品（{@code productId} / {@code productType} 不允许修改）。
     *
     * @return 受影响行数（成功 1）
     */
    int updateByBo(ProductInfoBo bo);

    /**
     * 行内切换产品状态（仅更新 {@code product_status} 单字段，不碰其它字段）。
     *
     * @param id     产品 ID
     * @param status 目标状态（字典 {@code sys_normal_disable}：0=正常 / 1=停用）
     * @return 受影响行数（成功 1）
     */
    int updateStatus(Long id, Integer status);

    /**
     * 软删产品（支持批量）。
     *
     * <p>删除前校验：</p>
     * <ul>
     *   <li>{@code t_warehouse_location_stock.product_stock > 0} 的产品 → 抛 {@code product.has_stock}</li>
     *   <li>被其他产品 {@code product_material} 引用 → 抛 {@code product.referenced_as_material}</li>
     * </ul>
     *
     * <p>校验通过 → softDelete 主体。</p>
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 产品详情「生产记录」子表（DJS-FIX-WMS-RALN-B）：按 productId 查生产 + 退货记录。
     *
     * @param productId   产品 ID
     * @param produceDate 生产日期（可空，DATE 精确）
     * @param produceType 生产类型 produce / return（可空）
     */
    List<ProductProductionRecordVo> queryProductionRecords(Long productId, Date produceDate, String produceType);

    /**
     * 商品详情「业务流水」子表（DJS-FIX-WMS-RALN-B）：按 productId 查 stock_flow（入库 / 领用出库 / 后台出库）。
     *
     * @param productId 产品 ID
     * @param bizDate   业务日期（可空，DATE 精确）
     */
    List<ProductFlowRecordVo> queryFlowRecords(Long productId, Date bizDate);

    /**
     * 商品配置「产品入库」（DJS-FIX-WMS-RALN-B）：录入 产品 / 库位 / 数量 → 写入库 stock_flow + 增 location_stock。
     *
     * @param bo 入库入参
     * @return 入库流水 ID
     */
    Long inbound(ProductStockInBo bo);

    /**
     * 按产品取其「存储仓库」配置的启用库位（mp picker 通用）。
     *
     * <p>读 {@code product.store_location_id}（逗号分隔库位 ID 串）→ {@code IN(ids)}
     * 且 {@code location_status=1} 查 {@link LocationInfo} → 按配置顺序回 {@link LocationPickerVo}。
     * 与 burn 的 {@code queryProductInboundLocations} 逻辑等同，额外支持可选 {@code locationType} 过滤。</p>
     *
     * <ul>
     *   <li>{@code store_location_id} 空 / 解析后无有效 ID → 返回 {@code Collections.emptyList()}</li>
     *   <li>脏数据（非数字 ID）try/catch 跳过，不拖垮整体取数</li>
     *   <li>{@code locationType} 非空时按类型精确过滤</li>
     * </ul>
     *
     * @param productId    产品 ID
     * @param locationType 字典 {@code djs_location_type}（可空，非空时精确过滤）
     * @return 库位 picker 列表（按 store_location_id 配置顺序）
     */
    List<LocationPickerVo> queryStoreLocations(Long productId, String locationType);

}
