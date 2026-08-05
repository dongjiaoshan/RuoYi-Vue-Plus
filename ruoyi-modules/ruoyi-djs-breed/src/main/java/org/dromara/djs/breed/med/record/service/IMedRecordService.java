package org.dromara.djs.breed.med.record.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBatchBo;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBo;
import org.dromara.djs.breed.med.record.domain.query.MedRecordQuery;
import org.dromara.djs.breed.med.record.domain.vo.MedRecordVo;
import org.dromara.djs.breed.med.record.domain.vo.UsableBatchVo;

import java.util.Collection;
import java.util.List;

/**
 * 用药治疗流水 Service（BRD-MED-003）。
 *
 * <p>4 入口：</p>
 * <ul>
 *   <li>{@link #addSingle}：单只用药 —— INSERT 1 条 drug_type=1；</li>
 *   <li>{@link #addBatch}：批量用药 —— INSERT 1 master(drug_type=2) + N detail(drug_type=3)；</li>
 *   <li>{@link #queryPage} / {@link #queryById}：admin + mp 查询；</li>
 *   <li>{@link #listUsableBatches}：mp picker 数据源（N 天内已领 + quantity>0）。</li>
 * </ul>
 *
 * <p>事务边界：addSingle / addBatch 单事务包扣减库存 + INSERT 记录；
 * 任一步失败回滚整体（含批次 quantity 扣减）。</p>
 *
 * @author djs
 * @since BRD-MED-003
 */
public interface IMedRecordService {

    /**
     * 单只用药登记（drug_type=1）。
     *
     * <p>校验 → 扣 batch.quantity → INSERT record。失败抛 ServiceException。</p>
     *
     * @return 受影响行数（成功 1）
     */
    int addSingle(MedRecordBo bo);

    /**
     * 批量用药登记（drug_type=2 master + N drug_type=3 detail）。
     *
     * <p>N ≤ 200，超过返 {@code medicine.batch.too_large}。
     * 总扣减 = dosage × pigIds.size()，触发上界 {@code 0 ≤ quantity ≤ stock_qty}。</p>
     *
     * @return master.id（前端可据此查 detail）
     */
    Long addBatch(MedRecordBatchBo bo);

    /**
     * 分页查询。
     */
    TableDataInfo<MedRecordVo> queryPage(MedRecordQuery query, PageQuery pageQuery);

    /**
     * 列表查询（导出用）。
     */
    List<MedRecordVo> queryList(MedRecordQuery query);

    /**
     * 详情。
     */
    MedRecordVo queryById(Long id);

    /**
     * 批量详情（master + N detail 一次拉，admin / mp 展开 master 时用）。
     */
    List<MedRecordVo> queryByMasterId(Long masterId);

    /**
     * mp picker 数据源：当前用户 N 天内已领 + quantity>0 的批次列表。
     *
     * @param operatorId 操作人（null = 不限）
     */
    List<UsableBatchVo> listUsableBatches(Long operatorId);

    /**
     * 软删（不回滚库存——用药历史不可逆，与领用台账同语义）。
     */
    int deleteByIds(Collection<Long> ids);

}
