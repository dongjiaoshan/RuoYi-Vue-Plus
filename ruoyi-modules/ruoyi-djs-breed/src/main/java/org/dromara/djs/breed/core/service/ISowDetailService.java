package org.dromara.djs.breed.core.service;

import org.dromara.djs.breed.core.domain.vo.FarrowByParityVo;
import org.dromara.djs.breed.core.domain.vo.FarrowRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PedigreeVo;
import org.dromara.djs.breed.core.domain.vo.PigMedRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PigTransferMpVo;
import org.dromara.djs.breed.core.domain.vo.SowPerformanceMpVo;

import java.util.List;

/**
 * mp 猪只详情聚合 Service（BRD-FIX-MP-DETAIL-SPLIT-001，read-only）。
 *
 * <p>母猪详情 6 KPI + 产仔堆叠柱 + 育肥详情谱系卡，按 pigId 实时聚合本母猪的分娩 /
 * 断奶 / 配种记录（Kevin 2026-05-30 D2 拍板本期 BE 从零建聚合）。不写表、不触状态机。
 * 所有指标缺数据源时返 null / 空集，不抛异常。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
public interface ISowDetailService {

    /**
     * 母猪性能 6 KPI（总产仔 / 健仔率 / 平均窝重 / 断奶成活率 / 当前胎次 / NPD）。
     *
     * @param pigId 母猪 ID
     * @return 6 KPI VO（每个指标缺数据 → null）；pigId 不存在抛 ServiceException
     */
    SowPerformanceMpVo querySowPerformance(Long pigId);

    /**
     * 产仔性能按胎次聚合（堆叠柱 4 系列：健仔 / 弱仔 / 死胎 / 木乃伊）。
     *
     * @param pigId 母猪 ID
     * @return 按胎次升序的聚合列表；无分娩返空集
     */
    List<FarrowByParityVo> queryFarrowByParity(Long pigId);

    /**
     * 谱系卡（母系 + 父系：耳号 / 品种 label / 日龄，母系含胎次）。
     *
     * @param pigId 本猪 ID
     * @return 谱系 VO（关联缺失字段 null）；pigId 不存在抛 ServiceException
     */
    PedigreeVo queryPedigree(Long pigId);

    /**
     * 母猪分娩记录逐条明细（DJS-FIX-6-14，原型 分娩记录 tab）。
     *
     * @param pigId 母猪 ID
     * @return 倒序 by 分娩日期的明细列表；无分娩返空集
     */
    List<FarrowRecordMpVo> queryFarrowRecords(Long pigId);

    /**
     * 本猪用药记录（DJS-FIX-6-14，原型 用药记录 tab）。原因 / 方式已翻中文 label。
     *
     * @param pigId 本猪 ID
     * @return 倒序 by 用药日期的用药列表；无用药返空集
     */
    List<PigMedRecordMpVo> queryMedRecords(Long pigId);

    /**
     * 本猪转移记录（DJS-FIX-6-14，原型 转移记录 tab）。含旧栏位驻留区间（相邻转移日期补算）。
     *
     * @param pigId 本猪 ID
     * @return 倒序 by 转移日期的转移列表；无转移返空集
     */
    List<PigTransferMpVo> queryTransferRecords(Long pigId);
}
