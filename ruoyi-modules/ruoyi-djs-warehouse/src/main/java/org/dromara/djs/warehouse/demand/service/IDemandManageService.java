package org.dromara.djs.warehouse.demand.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.AuditHistoryEntryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPigVo;
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

    /** 批量软删（仅 DRAFT/CANCELLED 态可删；其他态拒绝）。 */
    int deleteWithValidByIds(Collection<Long> ids);

    /** 白条业态：批量指定猪只（去重 + UNIQUE 兜底）。 */
    int assignPigs(Long demandId, AssignPigBo bo);

    /** 白条业态：移除某头猪的指定。 */
    int removeAssignedPig(Long demandId, String earNo);

    /** 白条业态：查询已指定的猪只列表。 */
    List<DemandPigVo> listAssignedPigs(Long demandId);

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
     * 需求管理页顶部「今日全局」KPI 横条（DJS-FIX-ADMIN-W22-007）。
     *
     * <p>一次返 6 个跨业态数字（白条需求/已调配头数 + 果蔬需求/已调配品种数 + 其他需求/已调配条数）。
     * 今日按 {@code Asia/Shanghai} 算（不依赖 DB CURDATE() 时区）；只读聚合，不变更状态。
     * 当日无 demand → 6 数全 0，不抛异常。</p>
     *
     * @return 6 字段 KPI VO
     */
    DemandTodayKpiVo getTodayKpi();
}
