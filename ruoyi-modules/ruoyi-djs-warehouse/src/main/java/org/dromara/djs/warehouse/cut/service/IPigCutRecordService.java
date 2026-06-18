package org.dromara.djs.warehouse.cut.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutOutBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.query.PigCutRecordQuery;
import org.dromara.djs.warehouse.cut.domain.vo.BarInfoVo;
import org.dromara.djs.warehouse.cut.domain.vo.CutProductTypeVo;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;

import java.util.List;

/**
 * 分割工序记录 Service（WMS-PIG-002）。
 *
 * <p>3 阶段事务方法：</p>
 * <ol>
 *   <li>{@link #submitPickup} 白条领用（pickup）</li>
 *   <li>{@link #submitCutOut} 出库称重（多部位提交）</li>
 *   <li>{@link #submitCutDone} 出库完成</li>
 * </ol>
 *
 * @author djs
 * @since WMS-PIG-002
 */
public interface IPigCutRecordService {

    /**
     * 阶段 1：白条领用。
     *
     * @return cut_record 主键 id
     */
    Long submitPickup(PigCutPickupBo bo);

    /**
     * 阶段 2：出库称重（多部位一次提交）。
     */
    void submitCutOut(PigCutOutBo bo);

    /**
     * 阶段 3：出库完成。
     */
    void submitCutDone(PigCutDoneBo bo);

    /**
     * 分页查询。
     */
    TableDataInfo<PigCutRecordVo> queryPageList(PigCutRecordQuery query, PageQuery pageQuery);

    /**
     * 不分页（导出 / 报表用）。
     */
    List<PigCutRecordVo> queryList(PigCutRecordQuery query);

    /**
     * 详情。
     */
    PigCutRecordVo queryById(Long id);

    /**
     * mp 端"待领用白条"列表（status='in_stock'，按入库时间倒序）。
     */
    List<BarInfoVo> queryAvailableBars();

    /**
     * mp 分割车间产品类型列表（标准分割成品 5 类：精瘦肉 / 部位肉 / 骨类 / 猪皮 / 碎料）。
     *
     * <p>口径：{@code product_workshop=2}（分割车间）+ {@code belong_type='pork'} +
     * {@code product_id LIKE 'PROD-PIG-%'}，按 product_id 升序，排除 D0001 / DEMO / 测试。</p>
     */
    List<CutProductTypeVo> queryCutProductTypes();

}
