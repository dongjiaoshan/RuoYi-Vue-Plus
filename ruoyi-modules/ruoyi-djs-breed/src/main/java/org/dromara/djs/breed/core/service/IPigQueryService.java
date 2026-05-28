package org.dromara.djs.breed.core.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;

/**
 * 猪只领域只读查询接口（跨模块薄壳）。
 *
 * <p>暴露给非养殖域（如 {@code ruoyi-djs-warehouse} 燎毛 / 屠宰 / 需求指定）做最小必要的只读查询，
 * 替代之前的 native query mapper 跨表读法（如 warehouse 端的 {@code PigInfoReadMapper}）。
 * 跨模块只通过本接口，不能反查 {@code PigMapper}。</p>
 *
 * @author djs
 */
public interface IPigQueryService {

    /**
     * 查指定耳号的猪只 {@code current_status}（未软删）。
     *
     * <p>{@code tenant_id} 由 MP 多租户拦截器在 final SQL 注入，应用层无需显式 WHERE。</p>
     *
     * @param earNo 猪只耳号
     * @return {@code current_status} 字符串（{@code END}/{@code HB}/{@code PZ} ...）；耳号不存在或已软删返 {@code null}
     */
    String selectCurrentStatusByEarNo(String earNo);

    /**
     * 分页查询「可出栏」育肥猪列表（DJS-FIX-ADMIN-W22-001）。
     *
     * <p>「可出栏」语义：{@code pig_type='fattening'} AND {@code current_status != 'END'} AND 未被其它
     * 非取消态需求指定（NOT EXISTS in {@code t_warehouse_demand_pig} JOIN {@code t_warehouse_demand_manage}
     * WHERE demand_status != 'CANCELLED' AND del_flag='0'）。</p>
     *
     * <p>返回字段含耳号 / 性别 / 品种品系 label / 日龄 / 最新背膘（取 {@code t_farm_pig_growth} measure_date DESC 第一条）。
     * 仅暴露 admin 端"指定猪只"对话框使用，不要在养殖业务里复用。</p>
     *
     * @param pageQuery 分页参数
     * @return 分页 VO
     */
    TableDataInfo<PigAvailableVo> listAvailableForOutbound(PageQuery pageQuery);

    /**
     * 「可出栏」育肥猪头数 COUNT（DJS-FIX-ADMIN-W22-003 SummaryBar 用）。
     *
     * <p>过滤语义与 {@link #listAvailableForOutbound(PageQuery)} 完全一致（{@code pig_type='fattening'}
     * AND {@code current_status != 'END'} AND NOT EXISTS in 非取消态需求）；仅返头数，避免分页 page 走一次。</p>
     *
     * @return 头数（≥ 0）
     */
    int countAvailableForOutbound();

}
