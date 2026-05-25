package org.dromara.djs.breed.med.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.domain.bo.MedUsageBo;
import org.dromara.djs.breed.med.domain.query.MedUsageQuery;
import org.dromara.djs.breed.med.domain.vo.MedUsageVo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 药品领用台账 Service（BRD-MED-002）。
 *
 * @author djs
 * @since BRD-MED-002
 */
public interface IMedUsageService {

    /**
     * 分页查询。
     */
    TableDataInfo<MedUsageVo> queryPageList(MedUsageQuery query, PageQuery pageQuery);

    /**
     * 列表查询（不分页，导出用）。
     */
    List<MedUsageVo> queryList(MedUsageQuery query);

    /**
     * 详情查询。
     */
    MedUsageVo queryById(Long id);

    /**
     * 新增领用 / 退回 / 损耗。
     *
     * <p>同步扣减或归还批次库存（{@link org.dromara.djs.breed.med.domain.MedBatch#getQuantity()}）：</p>
     * <ul>
     *   <li>{@code use} / {@code loss}：扣减，余量不足抛 {@code ServiceException}；</li>
     *   <li>{@code return}：归还。</li>
     * </ul>
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(MedUsageBo bo);

    /**
     * 软删（不回滚库存——领用历史不可逆）。
     */
    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 当日汇总（按 usageType 聚合 today 一日内的 usageQty 总和）。
     *
     * <p>小程序顶部"今日已领 / 今日退回 / 今日损耗"卡片用。返回 map：
     * {@code {use: 100.5, return: 0, loss: 2.0}}（缺省类型补 0）。</p>
     */
    Map<String, BigDecimal> todayStat();

}
