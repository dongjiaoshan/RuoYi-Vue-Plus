package org.dromara.djs.warehouse.demand.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.AuditHistoryEntryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandGroupExportVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandGroupVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPigVo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandBatchConfirmBo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandProductStoreDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandSummaryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandTodayKpiVo;

import java.util.Collection;
import java.util.List;

/**
 * 需求管理服务接口（WMS-DEMAND-001）。
 *
 * <p>状态推进类方法走 {@code DemandStatusService.transition} 独立入口，本接口仅负责
 * CRUD + 业态字段校验 + 指定猪只。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
public interface IDemandManageService {

    /** 分页查询。 */
    TableDataInfo<DemandManageVo> queryPageList(DemandManageQuery query, PageQuery pageQuery);

    /**
     * 需求汇总分组列表（0613-10 需求管理列表重做）。
     *
     * <p>按「需求日期 + 需求产品」分组聚合（同日同产品 N 门店合并成一行）。三态需求状态
     * （待确认 / 已全部确认 / 部分确认）+ 确认率在 service 层按 storeCount/confirmedStoreCount 算；
     * 聚合后内存分页（分组行数小，一天产品有限）。</p>
     *
     * @param query    复用 DemandManageQuery（仅 productName / beginDate / endDate 生效）
     * @param pageQuery 分页参数
     * @return 分组聚合分页结果
     */
    TableDataInfo<DemandGroupVo> queryGroupList(DemandManageQuery query, PageQuery pageQuery);

    /**
     * 需求管理汇总列表导出行（admin row155）。
     *
     * <p>与 {@link #queryGroupList} 完全同口径（同筛选、同分组、同三态/确认率算法），但<b>不分页</b>——
     * 导出的是当前筛选下的全量分组行。列表里由前端算的展示值（状态中文 / 确认率百分比 / 按单位格式化的
     * 数量）在 service 层预先算成文本，保证 xlsx 与页面所见一致。</p>
     *
     * @param query 与列表共用的筛选条件
     * @return 导出行（顺序同列表）
     */
    List<DemandGroupExportVo> queryGroupExportList(DemandManageQuery query);

    /** 不分页查询（导出用）。 */
    List<DemandManageVo> queryList(DemandManageQuery query);

    /** 查单条详情（含 audit_history JSON）。 */
    DemandManageVo queryById(Long id);

    /**
     * 新增需求（初始 status=DRAFT；自动生成 {@code demand_no}）。
     *
     * @return 新建记录 ID
     */
    Long insertByBo(DemandManageBo bo);

    /** 编辑需求（仅 DRAFT/SUBMITTED 态可改业务字段；其他态仅允许改 remark）。 */
    int updateByBo(DemandManageBo bo);

    /** 批量软删（仅 DRAFT/SUBMITTED/CANCELLED 态可删；删除即置 DELETED 终态留痕；其他态拒绝）。 */
    int deleteWithValidByIds(Collection<Long> ids);

    /** 白条业态：批量指定猪只（去重 + UNIQUE 兜底）。 */
    int assignPigs(Long demandId, AssignPigBo bo);

    /** 白条业态：移除某头猪的指定。 */
    int removeAssignedPig(Long demandId, String earNo);

    /** 白条业态：查询已指定的猪只列表。 */
    List<DemandPigVo> listAssignedPigs(Long demandId);

    /**
     * 「指定猪只」弹框：可出栏育肥猪分页（按养殖出栏日龄配置 {@code slaughter_age_days} 过滤）。
     *
     * <p>仅列出到龄（日龄 ≥ 阈值）的待派单育肥活体猪，口径与 mp 出栏选猪一致；阈值缺省 175（test row48）。</p>
     */
    TableDataInfo<PigAvailableVo> listAvailablePigsForOutbound(PageQuery pageQuery);

    /**
     * 改需求说明 {@code demand_explain}（WMS-DEMAND-002 mp 调度员自由备注）。
     *
     * <p>不受状态机限制（所有非终态/终态都可改备注——客户实操备注随时可调）；
     * 仅 patch {@code demand_explain} 列，不触碰状态/业务字段/audit_history。</p>
     *
     * @param demandId 需求 ID
     * @param explain  说明文本（最长 500；null 视为清空）
     * @return 影响行数
     */
    int updateExplain(Long demandId, String explain);

    /**
     * 调整需求量（V6-R140「需求调整管理」）。
     *
     * <p>与 {@link #updateByBo} 分开的一条独立路径：编辑只放行 DRAFT/SUBMITTED，
     * 而甲方要的调整闸是「发货之前都能调」，含已确认（CONFIRMED）。二者的可调范围不同，
     * 不能共用一套状态白名单。</p>
     *
     * <p>每次调整往 {@code t_warehouse_demand_adjust_record} 追加一行留痕（只增不改），
     * 再 patch 需求行的 {@code demand_quantity}，不动状态 / 确认人 / audit_history。</p>
     *
     * @param demandId 需求单 ID
     * @param bo       调整后需求量 + 备注
     * @return 受影响行数
     */
    int adjustQuantity(Long demandId, DemandAdjustBo bo);

    /** 解析 audit_history JSON 列为 timeline。 */
    List<AuditHistoryEntryVo> getAuditHistory(Long demandId);

    /**
     * 业态 SummaryBar 摘要（DJS-FIX-ADMIN-W22-003）。
     *
     * <p>4 业态 union DTO；只读聚合，不变更状态。</p>
     *
     * @param productType {@code white_bar} / {@code vegetable} / {@code gift_box} / {@code other}
     * @return 按业态填字段的摘要 VO
     * @throws org.dromara.common.core.exception.ServiceException 业态非法时
     */
    DemandSummaryVo getSummary(String productType);

    /**
     * 某产品「按门店聚合需求量明细」（D-FIX-24 决策 #8 列表详情弹窗）。
     *
     * <p>返回有该产品需求的各门店及其需求量合计 / 单数（非取消单）。空集时返空 List。</p>
     *
     * @param productId 产品 FK（{@code t_warehouse_product_info.id}）
     * @return 各门店需求明细
     */
    List<DemandProductStoreDetailVo> listProductStoreDetail(Long productId);

    /**
     * 需求管理页顶部「今日全局」KPI 横条（DJS-FIX-ADMIN-W22-007）。
     *
     * <p>一次返 6 个跨业态数字（白条需求/已调配头数 + 果蔬需求/已调配品种数 + 其他需求/已调配条数）。
     * 今日按 {@code Asia/Shanghai} 算（不依赖 DB CURDATE() 时区）；只读聚合，不变更状态。
     * 当日无 demand → 6 数全 0，不抛异常。</p>
     *
     * @return 6 字段 KPI VO
     */
    DemandTodayKpiVo getTodayKpi();

    /**
     * 解析选中分组下所有 SUBMITTED 态需求 id（row41 批量确认用）。
     *
     * <p>每个分组键 =（需求日期，需求产品）；返回这些分组下当前处于 SUBMITTED（待确认）的需求单 id，
     * 供批量确认逐条走确认状态机。分组之间互不重叠（一条需求只属一个日期+产品），不去重。</p>
     *
     * @param groups 需求日期 + 产品 分组键
     * @return 待确认需求 id 列表（无匹配返回空列表）
     */
    List<Long> listSubmittedIdsByGroups(List<DemandBatchConfirmBo.GroupKey> groups);
}
