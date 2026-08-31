package org.dromara.djs.plant.market.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.market.domain.query.MarketPlanQuery;
import org.dromara.djs.plant.market.domain.vo.MarketPlanVo;

import java.util.List;

/**
 * 果蔬上市计划服务（V6-R151，纯只读聚合）。
 *
 * @author djs
 */
public interface IMarketPlanService {

    /**
     * 分页查询果蔬上市计划（一行 = 一条种植计划，按上市月份降序）。
     *
     * @param query     查询条件（可空）
     * @param pageQuery 分页参数（可空，默认 10 条/页）
     * @return 分页结果
     */
    TableDataInfo<MarketPlanVo> queryPageList(MarketPlanQuery query, PageQuery pageQuery);

    /**
     * 全量查询果蔬上市计划（导出用，不分页）。
     *
     * @param query 查询条件（可空）
     * @return 全部匹配行
     */
    List<MarketPlanVo> queryList(MarketPlanQuery query);
}
