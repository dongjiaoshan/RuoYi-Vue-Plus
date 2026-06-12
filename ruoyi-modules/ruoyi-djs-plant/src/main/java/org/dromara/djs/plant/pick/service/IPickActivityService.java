package org.dromara.djs.plant.pick.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.pick.domain.query.PickActivityQuery;
import org.dromara.djs.plant.pick.domain.vo.PickActivityVo;

import java.util.List;

/**
 * 采摘活动只读聚合报表 Service。
 *
 * <p>按「作物 + 活动日期」聚合 {@code t_plant_plant_details} 的每日采摘统计。
 * 仅查询 + 导出，无任何写操作。</p>
 *
 * @author djs
 */
public interface IPickActivityService {

    /** 分页查询每日采摘聚合报表。 */
    TableDataInfo<PickActivityVo> queryPageList(PickActivityQuery query, PageQuery pageQuery);

    /** 全量查询（导出用）。 */
    List<PickActivityVo> queryList(PickActivityQuery query);
}
