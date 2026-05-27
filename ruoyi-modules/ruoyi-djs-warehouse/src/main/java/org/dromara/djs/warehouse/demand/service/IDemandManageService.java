package org.dromara.djs.warehouse.demand.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.AuditHistoryEntryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPigVo;

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

    /** 解析 audit_history JSON 列为 timeline。 */
    List<AuditHistoryEntryVo> getAuditHistory(Long demandId);
}
