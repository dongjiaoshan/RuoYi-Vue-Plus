package org.dromara.djs.store.returns.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBatchBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnDetailExportVo;
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
     * 退回操作「猪肉产品」tab 候选列表（{@code pork} + {@code white_bar} 两子类，保序并跨子类去重）。
     *
     * <p>与果蔬 / 其他产品 tab <b>完全同口径</b>：取门店当日盘点台账里
     * 「期初 + 入库 − 销售 − 赠送 &gt; 0」的产品（甲方 row205；不减损坏），按业态白名单分到本 tab。
     * 白条按重量退、猪肉成品按份退，单位取产品自身单位。每行带真实产品雪花 {@code productId} 供
     * {@link #batchCreate} 提交（提交时仍走产品 FK 校验 + 同一台账闸）。</p>
     *
     * @param storeId 门店 ID（必填；空 / 当日未盘点 → 空列表）
     * @return 猪肉/白条产品候选（productId + productName + productUnit + arrivedQuantity 可退量）
     */
    List<StoreReturnPorkCandidateVo> listPorkCandidates(Long storeId);

    /**
     * 退回操作「果蔬产品」tab 候选列表。
     *
     * <p>与猪肉 / 其他产品 tab <b>完全同口径</b>：取该门店<b>当天</b>（Asia/Shanghai）盘点台账里
     * 「期初 + 入库 − 销售 − 赠送 &gt; 0」的果蔬业态产品（甲方 row205；不减损坏）。
     * 配置「原材料外售」的成品会折叠回其原材料后再列。</p>
     *
     * @param storeId 门店 ID（必填；空 / 当日未盘点 → 空列表）
     * @return 果蔬候选（productId + productName + productUnit + arrivedQuantity 可退量）
     */
    List<StoreReturnVegCandidateVo> listVegCandidates(Long storeId);

    /**
     * 「其他产品」退回候选（admin row202：非猪肉 / 非白条 / 非果蔬 / 非礼盒 的产品）。
     *
     * <p>业态白名单 {@code {dry_good, egg, other}}，与项目已有的「其他产品打包入口」同口径。
     * 取数与猪肉 / 果蔬 tab 完全一致：门店当日盘点台账 <b>期初 + 入库 − 销售 − 赠送 &gt; 0</b>
     * （row205；不减损坏）。</p>
     *
     * @param storeId 门店 ID（必填；空返空列表）
     * @return 其他产品候选（结构复用果蔬候选 VO）
     */
    List<StoreReturnVegCandidateVo> listOtherCandidates(Long storeId);

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
     * 仓库「退货记录」外层「门店 + 当日」汇总列表（不分页，导出用，与 {@link #queryStoreDailyPage} 同口径）。
     */
    List<StoreReturnStoreDailyVo> queryStoreDailyList(StoreReturnQuery query);

    /**
     * 仓库「退回明细」弹窗导出（与弹窗表格同列同口径，逐条明细）。
     *
     * <p>与 {@link #queryList} 的差别只在展示层：这里把实收量 / 差异量按原材料单位口径格式化成文本，
     * 免得 Excel 里 30 枚鸡蛋的实收写成裸数字 30、跟「份」的退回量混为一谈。</p>
     */
    List<StoreReturnDetailExportVo> queryDetailExportList(StoreReturnQuery query);

    /**
     * mp 退货管理：当天门店→仓库退回按门店分组卡（状态派生 pending/confirmed，mp 词表）。
     */
    List<StoreReturnGroupVo> listPendingGroups();

    /**
     * mp 退货确认详情：某门店某状态（mp 词表 pending/confirmed）下的退回清单（字段对齐 mp ReturnItem）。
     *
     * @param storeId    门店 ID（必填）
     * @param mpStatus   mp 词表状态 pending / confirmed
     * @param returnDate 退回日期（{@code yyyy-MM-dd}，row174：分组卡携带；非空则只取该门店该天的退回行，
     *                   能翻历史某天；空则不限日期，向后兼容）
     */
    List<StoreReturnAppletItemVo> listAppletItemsByStoreAndStatus(Long storeId, String mpStatus, String returnDate);

    /** 软删除（DjsBaseServiceImpl#softDelete 范式）。 */
    int deleteByIds(Collection<Long> ids);
}
