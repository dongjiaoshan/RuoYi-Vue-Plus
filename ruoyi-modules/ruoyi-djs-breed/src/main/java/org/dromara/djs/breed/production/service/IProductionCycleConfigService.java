package org.dromara.djs.breed.production.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.production.domain.bo.ProductionCycleConfigBo;
import org.dromara.djs.breed.production.domain.query.ProductionCycleConfigQuery;
import org.dromara.djs.breed.production.domain.vo.ProductionCycleConfigVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    /**
     * 批量按 key 取生效值（表单语义：母猪生产配置 / 出栏配置一次拉一组 key）。
     *
     * <p>每个 key 返回 {@code custom_value}（客户改过）优先，无则 {@code default_value}；
     * 完全不存在的 key 不入 map（调用方自行 fallback 默认值）。</p>
     *
     * @param configKeys 业务键集合
     * @return {@code configKey → 生效天数}（仅含 DB 有的 key）
     */
    Map<String, Integer> getValuesByKeys(Collection<String> configKeys);

    /**
     * 批量保存表单配置（母猪生产配置 6 项 / 出栏配置 1 项）：按 key UPSERT 各项 {@code custom_value}。
     *
     * <p>key 已存在 → 更新 {@code custom_value}；不存在 → 插入新行（{@code default_value} 取入参兜底）。
     * 表单语义非 list-CRUD，admin 改的是 {@code custom_value}。</p>
     *
     * @param values        {@code configKey → 客户值}
     * @param defaultValues {@code configKey → 业内默认值}（key 首次落库时填入 default_value）
     * @param descriptions  {@code configKey → 说明}（key 首次落库时填入 description）
     * @return 受影响行数（插入 + 更新）
     */
    int batchSaveValues(Map<String, Integer> values,
                        Map<String, Integer> defaultValues,
                        Map<String, String> descriptions);

}
