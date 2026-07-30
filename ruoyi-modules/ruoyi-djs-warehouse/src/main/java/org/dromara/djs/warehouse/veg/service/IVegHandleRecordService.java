package org.dromara.djs.warehouse.veg.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.VegHandleRecordVo;

import java.util.List;

/**
 * 毛菜间处理记录 Service（FIX-ADMIN-R130，仓库-admin 行130「毛菜间处理记录」只读菜单）。
 *
 * <p>毛菜处理间处理流水 + 毛菜处理结算损耗 + 采摘活动流水三支 UNION 的只读列表。</p>
 *
 * @author djs
 * @since FIX-ADMIN-R130
 */
public interface IVegHandleRecordService {

    /**
     * 毛菜间处理记录分页列表。
     *
     * @param query     查询条件（日期范围 / 作物名 / 统计来源 / 处理方式 / 地块编号 / 记录人）
     * @param pageQuery 分页参数
     * @return 分页结果（按处理日期倒序）
     */
    TableDataInfo<VegHandleRecordVo> queryPage(VegHandleRecordQuery query, PageQuery pageQuery);

    /**
     * 毛菜间处理记录不分页列表（导出用）。
     *
     * @param query 查询条件
     * @return 全量列表（按处理日期倒序）
     */
    List<VegHandleRecordVo> queryList(VegHandleRecordQuery query);
}
