package org.dromara.djs.warehouse.burn.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.domain.query.PigBurnRecordQuery;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;

import java.util.List;

/**
 * 燎毛工序记录 Service（WMS-PIG-001）。
 *
 * <p>核心方法 {@link #submitBurnRecord} 在同一事务内 4 步：</p>
 * <ol>
 *   <li>校验 ear_no 存在且 current_status='END'</li>
 *   <li>生成 burn_id + 计算 lossWeight + INSERT 燎毛记录</li>
 *   <li>UPDATE location_stock 扣减白条库存（行锁 + 数量校验）</li>
 *   <li>INSERT stock_flow 出库流水</li>
 * </ol>
 *
 * @author djs
 * @since WMS-PIG-001
 */
public interface IPigBurnRecordService {

    /**
     * mp 端提交燎毛工序记录（核心方法）。
     *
     * @param bo 入参
     * @return 燎毛记录主键 id
     */
    Long submitBurnRecord(PigBurnRecordBo bo);

    /**
     * 分页查询。
     */
    TableDataInfo<PigBurnRecordVo> queryPageList(PigBurnRecordQuery query, PageQuery pageQuery);

    /**
     * 不分页（导出 / 报表用）。
     */
    List<PigBurnRecordVo> queryList(PigBurnRecordQuery query);

    /**
     * 详情。
     */
    PigBurnRecordVo queryById(Long id);

}
