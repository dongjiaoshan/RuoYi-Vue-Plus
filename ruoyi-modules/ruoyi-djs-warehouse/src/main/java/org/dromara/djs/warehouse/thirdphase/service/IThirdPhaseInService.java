package org.dromara.djs.warehouse.thirdphase.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.thirdphase.domain.bo.ThirdPhaseInBo;
import org.dromara.djs.warehouse.thirdphase.domain.query.ThirdPhaseInQuery;
import org.dromara.djs.warehouse.thirdphase.domain.vo.ThirdPhaseInVo;
import org.dromara.djs.warehouse.thirdphase.domain.vo.ThirdPhaseSummaryVo;

import java.util.List;

/**
 * 三期作物入库 Service（V6 row88）。
 *
 * @author djs
 * @since V6-R88
 */
public interface IThirdPhaseInService {

    /** 分页列表（日期区间 + 作物名模糊，按入库时间倒序）。 */
    TableDataInfo<ThirdPhaseInVo> queryPage(ThirdPhaseInQuery query, PageQuery pageQuery);

    /** 导出用全量列表（同筛选条件，不分页）。 */
    List<ThirdPhaseInVo> queryList(ThirdPhaseInQuery query);

    /**
     * 新增入库（单事务）：校验 → 入库台账 → 地块打【三期】标识 → 落本模块记录。
     *
     * @param bo 入库入参
     * @return 新记录 id
     */
    Long submit(ThirdPhaseInBo bo);

    /**
     * 【三期】总入库 / 总出库合计（只读，按流水行 {@code third_phase=1} 聚合）。
     *
     * @return 两个合计量（无记录时各为 0，不返 null）
     */
    ThirdPhaseSummaryVo querySummary();
}
