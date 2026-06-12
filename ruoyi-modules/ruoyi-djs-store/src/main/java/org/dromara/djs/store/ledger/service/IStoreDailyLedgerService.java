package org.dromara.djs.store.ledger.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.ledger.domain.bo.StoreDailyLedgerBatchBo;
import org.dromara.djs.store.ledger.domain.query.StoreDailyLedgerQuery;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerCandidateVo;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerHeaderVo;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 门店经营流水盘点台账 Service（STORE-LEDGER-001）。
 *
 * <p>原型「门店管理>门店盘点」重建：对仓库发货到门店的产品，录期初 / 入库 / 销售 / 赠送 / 退货 / 损耗，
 * 相当于门店自己的库存盘点。期末库存 service 落库时算。</p>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
public interface IStoreDailyLedgerService {

    /** 盘点单列表（按 门店 + 盘点日期 分组的盘点单，分页）。 */
    TableDataInfo<StoreDailyLedgerHeaderVo> queryHeaderPage(StoreDailyLedgerQuery query, PageQuery pageQuery);

    /**
     * 新增当日盘点候选：列出门店关联产品全 SKU + 预填 saleQty / inboundQty / returnQty（按日聚合）。
     *
     * @param storeId 门店
     * @param ledgerDate 盘点日期（缺省今天）
     * @return 候选行列表（按产品）
     */
    List<StoreDailyLedgerCandidateVo> listCandidates(Long storeId, LocalDate ledgerDate);

    /**
     * 当日盘点整表批量提交：一次落某门店某日多产品行，service 算 closing_qty。
     * 同门店同产品同日唯一（已存在则更新该行）。
     *
     * @param bo 含门店 + 日期 + 多产品行
     * @return 落库行数
     */
    int batchSave(StoreDailyLedgerBatchBo bo);

    /** 盘点详情（按 storeId + ledgerDate 取一组明细行，只读）。 */
    List<StoreDailyLedgerVo> queryDetail(Long storeId, LocalDate ledgerDate);

    /** 按 productId + 日期范围查盘点历史（产品详情盘点历史用，按日期倒序）。 */
    List<StoreDailyLedgerVo> queryHistoryByProduct(StoreDailyLedgerQuery query);
}
