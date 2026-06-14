package org.dromara.djs.breed.event.growth.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigSearchVo;
import org.dromara.djs.breed.event.growth.domain.bo.GrowthBo;
import org.dromara.djs.breed.event.growth.domain.query.GrowthQuery;
import org.dromara.djs.breed.event.growth.domain.vo.PigGrowthVo;

import java.util.Collection;
import java.util.List;

/**
 * 生长记录 Service（BRD-EVENT-005 GROWTH）。
 *
 * <p>主入口：</p>
 * <ul>
 *   <li>{@link #addGrowthRecord} INSERT t_farm_pig_growth（不触发状态机）</li>
 *   <li>{@link #queryPage} 分页查询（admin 列表 / mp 历史）</li>
 *   <li>{@link #getById} 详情</li>
 *   <li>{@link #deleteByIds} 删除（3 天内可删，service 层强校验）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-005
 */
public interface IPigGrowthService {

    PigGrowthVo addGrowthRecord(GrowthBo bo);

    /**
     * 215：育肥猪生长记录「阶段驱动待办」清单（mp tab1）。
     *
     * <p>口径（Kevin 已拍）：取后台「育肥日龄阶段配置」{@code t_farm_fatten_age_stage} 中
     * {@code record_growth=1} 的日龄段；列出 {@code pig_type='fattening' AND status!=END} 且
     * 当前日龄落在某 record_growth=1 段区间内的育肥猪；再剔除「该猪在该段日龄区间内已存在任意 1 条
     * 生长记录」的猪（每阶段只录一次）。无配置段 / 无命中 → 返空列表。</p>
     *
     * @param earNoKeyword 耳号 LIKE 关键字（可选，mp 顶部搜索框）
     * @return 待录入生长记录的育肥猪（PigSearchVo 风格，含 ageDays/barn/pen）
     */
    List<PigSearchVo> listGrowthTodo(String earNoKeyword);

    TableDataInfo<PigGrowthVo> queryPage(GrowthQuery query, PageQuery pageQuery);

    PigGrowthVo getById(Long id);

    int deleteByIds(Collection<Long> ids);
}
