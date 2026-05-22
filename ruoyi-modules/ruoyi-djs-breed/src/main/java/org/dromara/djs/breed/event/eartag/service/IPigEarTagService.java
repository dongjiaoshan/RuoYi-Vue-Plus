package org.dromara.djs.breed.event.eartag.service;

import org.dromara.djs.breed.event.eartag.domain.bo.PigletBatchEarTagBo;
import org.dromara.djs.breed.event.eartag.domain.vo.FarrowEarTagStatVo;
import org.dromara.djs.breed.event.eartag.domain.vo.PigletEarTagVo;

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
}
