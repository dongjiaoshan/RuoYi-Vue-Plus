package org.dromara.djs.breed.event.eartag.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.query.PigletEarTagQuery;
import org.dromara.djs.breed.event.eartag.domain.vo.EarNoPreviewVo;
import org.dromara.djs.breed.event.eartag.domain.vo.FarrowEarTagStatVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletnoVo;

import java.util.List;

/**
 * 仔猪批量耳标 Service（BRD-EVENT-003）。
 *
 * <p><b>设计要点</b>：</p>
 * <ul>
 *   <li>仔猪 <b>不参与状态机</b> — 不调 {@code IPigCoreService#fireEvent} 也不调 {@code createPig}，
 *       而是同事务 INSERT {@code t_farm_pig_info}（pig_type='piglet', current_status='HB' 默认）
 *       + INSERT {@code t_farm_pig_pigletno}（耳标日志）。</li>
 *   <li>耳号由 SYS-INFRA-004 {@code generateBatch} 一次连续生成 N 个（Redisson 分布式锁保唯一）。</li>
 *   <li>已贴头数 + 本批头数 ≤ {@code farrow.live_born} 强制校验。</li>
 *   <li>父猪耳号从 {@code farrow.breeding_id → t_farm_pig_breeding.boar_ear_no} 反查，可能为空。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
public interface IPigEarTagService {

    /**
     * 统计某次分娩的耳标进度（活产 / 已贴 / 待贴 + 已贴清单）。
     *
     * @param farrowId 分娩记录 ID
     * @return 统计 VO；farrow 不存在抛 ServiceException
     */
    FarrowEarTagStatVo statByFarrow(Long farrowId);

    /**
     * 批量贴耳标：一次 transaction 内为 N 头仔猪创建 pig_info + pigletno。
     *
     * @param bo 入参（含 farrowId + N 个 PigletEarTagItem）
     * @return 生成的 N 个 VO（耳号、pig_id、出生重等）
     */
    List<PigletEarTagVo> batchTag(PigletBatchEarTagBo bo);

    /**
     * 预览某次分娩下一批公/母连号耳号（K122，仅预览不占号）。
     *
     * <p>按 farrowId 反查母猪品系/品种 + 分娩日，分别为公组/母组按 {@code DB max 同前缀 + 1} 推算 N 个
     * 连号全号，供录入行预填「预计耳号」。真实分配仍在 {@link #batchTag} 提交时锁内做，并发下预览号可能
     * 与最终号有偏差，前端以提交回显为准。</p>
     *
     * @param farrowId    分娩记录 ID
     * @param maleCount   公组预览头数（&lt; 0 视为 0）
     * @param femaleCount 母组预览头数（&lt; 0 视为 0）
     * @return 公/母两组预计耳号串
     */
    EarNoPreviewVo previewEarNos(Long farrowId, int maleCount, int femaleCount);

    /**
     * 分页查询仔猪耳标历史（admin 只读列表）。
     */
    TableDataInfo<PigletnoVo> queryPage(PigletEarTagQuery query, PageQuery pageQuery);
}
