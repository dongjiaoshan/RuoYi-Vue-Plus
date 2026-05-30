package org.dromara.djs.plant.pick.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.pick.domain.bo.PickActivityBo;
import org.dromara.djs.plant.pick.domain.query.PickActivityQuery;
import org.dromara.djs.plant.pick.domain.vo.PickActivityVo;

import java.util.Collection;
import java.util.List;

/**
 * 采摘活动 Service（PLT-PLAN-002）。
 *
 * <p>游客体验采摘场景的 5 CRUD + 活动结束时汇总。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
public interface IPickActivityService {

    TableDataInfo<PickActivityVo> queryPageList(PickActivityQuery query, PageQuery pageQuery);

    List<PickActivityVo> queryList(PickActivityQuery query);

    PickActivityVo getInfo(Long id);

    Long createByBo(PickActivityBo bo);

    int updateByBo(PickActivityBo bo);

    int deleteByIds(Collection<Long> ids);

    /**
     * 活动结束后汇总：从同 cropId + activity_date 的 plant_details.actual_yield 聚合写回
     * {@code total_yield}，并把状态置为 ended。
     *
     * @return 更新后的 totalYield 值
     */
    PickActivityVo summary(Long id);
}
