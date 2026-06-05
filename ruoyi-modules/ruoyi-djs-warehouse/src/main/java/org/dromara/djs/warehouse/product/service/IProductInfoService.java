package org.dromara.djs.warehouse.product.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.product.domain.bo.ProductInfoBo;
import org.dromara.djs.warehouse.product.domain.query.ProductInfoQuery;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;

import java.util.Collection;
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
     * 查询单条详情（{@code productType=3} 时自动附加 {@code giftComponents}）。
     */
    ProductInfoVo queryById(Long id);

    /**
     * 新增产品。
     *
     * <p>差异校验：</p>
     * <ul>
     *   <li>productType=1 自产 → belongType 必填</li>
     *   <li>productType=2 外购 → supplierId 必填</li>
     *   <li>productType=3 礼盒 → giftComponents 至少 1 条；service 自动 set belongType=gift_box；
     *       组件 SKU 不允许指向另一个礼盒（不嵌套）</li>
     * </ul>
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(ProductInfoBo bo);

    /**
     * 修改产品（{@code productId} / {@code productType} 不允许修改）。
     *
     * <p>礼盒组件"覆盖式 replace"：先 softDelete 老组件再批量 insert 新组件，V1 不做 diff。</p>
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
     * <p>校验通过 → softDelete 主体 + 同步软删 {@code t_warehouse_gift_box.box_product_id=?} 的所有组件。</p>
     *
     * @param ids 主键集合
     * @return 受影响行数（仅主表，组件软删不计）
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
