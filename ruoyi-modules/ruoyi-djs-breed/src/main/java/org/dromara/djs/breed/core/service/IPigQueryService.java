package org.dromara.djs.breed.core.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;

import java.util.Collection;
import java.util.List;

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

    /**
     * 分页查「可追溯猪只」列表（STORE-TRACE-ONSITE-001 门店现场生码选猪）。
     *
     * <p>语义：对<b>已出栏 / 可追溯</b>育肥猪生码，故<b>不</b>过滤 {@code current_status='END'}、
     * <b>不</b>过滤需求占用（与 {@link #listAvailableForOutbound(PageQuery)} 的「可出栏 = 待派单活体」
     * 语义相反）。返回字段含耳号 / 性别 / 品种品系 label / 日龄，供门店现场生码页 PigChip 选择器
     * + 猪只信息只读面板展示。仅暴露门店现场生码使用，不要在养殖业务里复用。</p>
     *
     * @param pageQuery 分页参数
     * @return 分页 VO（复用 {@link PigAvailableVo}）
     */
    TableDataInfo<PigAvailableVo> listTraceablePigs(PageQuery pageQuery);

    /**
     * 按耳号批量查猪只展示信息（FIX-STORE-TRACE-BAR-001 门店追溯白条 enrich 用）。
     *
     * <p>additive 跨域只读：门店「猪肉追溯码管理」picker 改为「当天确认收货白条」口径后，
     * 先按白条过滤、再按白条耳号 enrich 猪只信息（性别 / 品种品系 / 日龄）。
     * 不复用也不污染分页选猪 {@link #listTraceablePigs(PageQuery)}。</p>
     *
     * @param earNos 耳号集合（可空 / 空时返空 list）
     * @return 命中档案的猪只信息（earNo / pigSex / pigBreedLabel / ageDays）；未命中耳号不返回
     */
    List<PigAvailableVo> listPigInfoByEarNos(Collection<String> earNos);

}
