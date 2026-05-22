package org.dromara.djs.breed.production.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.production.domain.bo.ProductionCycleConfigBo;
import org.dromara.djs.breed.production.domain.query.ProductionCycleConfigQuery;
import org.dromara.djs.breed.production.domain.vo.ProductionCycleConfigVo;

import java.util.Collection;
import java.util.List;

/**
 * 生产周期配置 Service（BRD-MD-003 Tab1）。
 *
 * @author djs
 * @since BRD-MD-003
 */
public interface IProductionCycleConfigService {

    TableDataInfo<ProductionCycleConfigVo> queryPageList(ProductionCycleConfigQuery query, PageQuery pageQuery);

    List<ProductionCycleConfigVo> queryList(ProductionCycleConfigQuery query);

    ProductionCycleConfigVo queryById(Long id);

    int insertByBo(ProductionCycleConfigBo bo);

    int updateByBo(ProductionCycleConfigBo bo);

    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 给 BRD-CORE-001 状态机 / 列表过滤等下游调用：按 configKey 取生效值。
     *
     * <p>优先返回 {@code custom_value}（客户调过），无则回退到 {@code default_value}。
     * 若 key 完全不存在（seed 缺）返回 {@code null}，调用方需自行 fallback。</p>
     *
     * @param configKey 业务键（如 {@code gestation_days}）
     * @return 生效天数；不存在返回 null
     */
    Integer getValue(String configKey);

}
