package org.dromara.djs.breed.event.slaughter.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBatchBo;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBo;
import org.dromara.djs.breed.event.slaughter.domain.query.SlaughterQuery;
import org.dromara.djs.breed.event.slaughter.domain.vo.PigMarketingVo;
import org.dromara.djs.breed.event.slaughter.domain.vo.SlaughterBatchPigVo;

import java.util.List;

/**
 * 出栏事件 Service（BRD-EVENT-004 SLAUGHTER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
public interface ISlaughterService {

    PigMarketingVo recordSlaughter(SlaughterBo bo);

    /**
     * 批量出栏：N 头猪各自重量 + 共用出栏日期/去向/人员/照片，逐头复用单只逻辑，整批同一事务。
     *
     * @param bo 批量入参
     * @return 每头的出栏记录，顺序与 {@code bo.items} 一致
     */
    List<PigMarketingVo> recordSlaughterBatch(SlaughterBatchBo bo);

    /**
     * 按 pigId 批量换耳号（mp 批量出栏录入页逐头列重量用）。
     *
     * @param pigIds 猪只 ID 列表
     * @return 猪只 ID + 耳号，顺序与入参一致；查不到的 id 直接跳过
     */
    List<SlaughterBatchPigVo> listBatchPigs(List<Long> pigIds);

    TableDataInfo<PigMarketingVo> queryPage(SlaughterQuery query, PageQuery pageQuery);
}
