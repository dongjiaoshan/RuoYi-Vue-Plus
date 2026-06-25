package org.dromara.djs.store.returns.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBatchBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnAppletItemVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnGroupVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnPorkCandidateVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnStoreDailyVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVegCandidateVo;

import java.util.Collection;
import java.util.List;

/**
 * 门店退回管理 Service（STR-RETURN-001，门店域薄实现）。
 *
 * <p>V1 仅录入：三方向退回登记（主场景 customer_to_store）+ 会员/追溯码字段，
 * 不做库存联动 / 不做状态机（仓库侧退货由 WMS-SHIP-001 负责，避免双写库存）。</p>
 *
 * @author djs
 * @since STR-RETURN-001
 */
public interface IStoreReturnService {

    /** 分页列表（VO 批量填 storeName/productName）。 */
    TableDataInfo<StoreReturnVo> queryPageList(StoreReturnQuery query, PageQuery pageQuery);

    /** 导出用列表（VO 批量填 storeName/productName）。 */
    List<StoreReturnVo> queryList(StoreReturnQuery query);

    /** 详情。 */
    StoreReturnVo queryById(Long id);

    /**
     * 新增退回记录：returnNo 服务端生成（BizCodeType.RETURN_NO）、operatorId 注入、
     * 校验 product 存在（storeId 非空才校验 store）、returnDate 缺省 now。
     */
    Long insertByBo(StoreReturnBo bo);

    /** 编辑（不允许改 returnNo）。 */
    int updateByBo(StoreReturnBo bo);

    /**
     * 退回操作批量录入（STORE-RETURN-REALIGN-001 原型「退回操作」）：一次建多条
     * {@code pending}（待仓库确认）退回，<b>不联动入库</b>，库存联动延后到 {@link #confirm}。
     *
     * @param bo 含门店 + 多条 {productId, returnWeight}
     * @return 成功建条数
     */
    int batchCreate(StoreReturnBatchBo bo);

    /**
     * 退回操作「猪肉产品」tab 固定候选列表（对齐原型「可退回商品列表配置在字典项中，固定展示」）。
     *
     * <p>取 {@code t_warehouse_product_info} 中 {@code belong_type IN ('pork','white_bar')} 的产品，
     * 与门店关联无关——猪肉为中央生产、各门店共用同一可退回清单，故不按门店过滤。每行带真实
     * 产品雪花 {@code productId} 供 {@link #batchCreate} 提交（提交时仍走产品 FK 校验）。</p>
     *
     * @return 猪肉/白条产品候选（productId + productName + productUnit）
     */
    List<StoreReturnPorkCandidateVo> listPorkCandidates();

    /**
     * 退回操作「果蔬产品」tab 候选列表（对齐原型「可退回 = 当天已确认到店的需求产品」）。
     *
     * <p>取该门店<b>当天</b>（Asia/Shanghai）果蔬业态、已确认（{@code CONFIRMED}）且已门店收货
     * （{@code received_time IS NOT NULL}）的需求，按 {@code product_id} 去重。猪肉为另一固定 tab
     * （{@link #listPorkCandidates}），果蔬 tab 仅按当天到店需求动态出，不再靠门店关联产品全量。</p>
     *
     * @param storeId 门店 ID（必填；空返空列表）
     * @return 果蔬候选（productId + productName + productUnit）
     */
    List<StoreReturnVegCandidateVo> listVegCandidates(Long storeId);

    /**
     * 仓库确认实收（STORE-RETURN-REALIGN-001 原型「退回记录」仓库确认入库）：
     * 校验记录为 {@code pending}，填实收量/实收重量/库位 + 确认人/时间，状态转 {@code received}，
     * 同事务联动外购入库（{@code location_stock} + {@code stock_flow(return_in)}）。
     *
     * @param bo 含退回记录 ID + 库位 + 实收量/实收重量
     * @return 受影响行数
     */
    int confirm(StoreReturnConfirmBo bo);

    /**
     * 仓库「退货记录」外层「门店 + 当日」汇总分页（仅 {@code store_to_warehouse} 方向）。
     * 供 admin 仓库退货页主视图，点行下钻明细复用 {@link #queryPageList}（带 storeId + 日期）。
     */
    TableDataInfo<StoreReturnStoreDailyVo> queryStoreDailyPage(StoreReturnQuery query, PageQuery pageQuery);

    /**
     * mp 退货管理：当天门店→仓库退回按门店分组卡（状态派生 pending/confirmed，mp 词表）。
     */
    List<StoreReturnGroupVo> listPendingGroups();

    /**
     * mp 退货确认详情：某门店某状态（mp 词表 pending/confirmed）下的退回清单（字段对齐 mp ReturnItem）。
     *
     * @param storeId  门店 ID（必填）
     * @param mpStatus mp 词表状态 pending / confirmed
     */
    List<StoreReturnAppletItemVo> listAppletItemsByStoreAndStatus(Long storeId, String mpStatus);

    /** 软删除（DjsBaseServiceImpl#softDelete 范式）。 */
    int deleteByIds(Collection<Long> ids);
}
