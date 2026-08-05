package org.dromara.djs.breed.event.castrate.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.castrate.domain.bo.CastrateBo;
import org.dromara.djs.breed.event.castrate.domain.query.CastrateQuery;
import org.dromara.djs.breed.event.castrate.domain.vo.CastrateRecordVo;

import java.time.LocalDate;

/**
 * 阉割事件 Service（BRD-EVENT-004 CASTRATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
public interface ICastrateService {

    CastrateRecordVo recordCastrate(CastrateBo bo);

    /**
     * 每日跑批：把日龄超过 7 天的未阉割公仔猪自动置为已阉割（admin row186）。
     *
     * <p>命中条件：{@code pig_sex='M'} 且 {@code pig_type='piglet'} 且 {@code is_castrated=1}
     * 且 {@code DATEDIFF(targetDate, COALESCE(birth_date, introduce_date)) > 7}，排除终态 END。
     * 每头写一条阉割记录 + 一条 CASTRATE 事件台账 + 置 {@code is_castrated=2}，与人工录入同口径。</p>
     *
     * <p>跑在定时线程里（无登录上下文），故不取 {@code LoginHelper} 操作人，
     * 阉割人固定记为「系统自动」。逐头独立处理，单头失败只记日志不影响其余。</p>
     *
     * @param targetDate 计算日龄的基准日（定时传当天；手动重跑传目标日）
     * @return 实际自动阉割的头数
     */
    int autoCastrateOverAgePiglets(LocalDate targetDate);

    TableDataInfo<CastrateRecordVo> queryPage(CastrateQuery query, PageQuery pageQuery);
}
