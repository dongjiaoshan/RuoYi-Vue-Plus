package org.dromara.djs.breed.event.breeding.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.breeding.domain.bo.BreedingBatchBo;
import org.dromara.djs.breed.event.breeding.domain.bo.BreedingBo;
import org.dromara.djs.breed.event.breeding.domain.query.BreedingQuery;
import org.dromara.djs.breed.event.breeding.domain.vo.PigBreedingVo;

import java.util.List;

/**
 * 配种事件 Service（BRD-EVENT-002 BREED）。
 *
 * <p>主入口 {@link #recordBreeding}：INSERT t_farm_pig_breeding →
 * {@code pigCoreService.fireEvent(BREED)} 推动状态机 HB/DN/LC/KH/FQ → PZ
 * 并由 {@code applyEventSideEffects + applyBreedingCounters} 更新 mating_id +
 * mating_count + last_mating_date（D5 audit a-1）。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface IBreedingService {

    PigBreedingVo recordBreeding(BreedingBo bo);

    /**
     * 批量配种（mp「批量配种」）：N 头母猪 + 同一套配种信息。
     *
     * <p>先对每头预检「母系×父系育种配置」，任一头不可与所选公猪配种 → 点名耳号抛错且一条都不落库；
     * 全部通过后逐头复用 {@link #recordBreeding}（状态机 + counters 照跑），落 N 条独立单头记录。
     * 整批同一事务，任一头失败全回滚。</p>
     */
    List<PigBreedingVo> recordBreedingBatch(BreedingBatchBo batchBo);

    TableDataInfo<PigBreedingVo> queryPage(BreedingQuery query, PageQuery pageQuery);
}
