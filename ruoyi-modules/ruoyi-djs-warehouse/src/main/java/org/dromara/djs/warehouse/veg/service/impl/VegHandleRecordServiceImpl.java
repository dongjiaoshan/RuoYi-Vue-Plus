package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.VegHandleRecordVo;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper;
import org.dromara.djs.warehouse.veg.service.IVegHandleRecordService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 毛菜间处理记录 Service 实现（FIX-ADMIN-R130，仓库-admin 行130）。
 *
 * @author djs
 * @since FIX-ADMIN-R130
 */
@Service
@RequiredArgsConstructor
public class VegHandleRecordServiceImpl implements IVegHandleRecordService {

    /** 统计来源码值 → 中文（导出列用；页面侧走前端 i18n，两处含义一致）。 */
    private static final String SOURCE_VEG_HANDLE = "1";
    private static final String SOURCE_PICK_ACTIVITY = "2";

    private final HandleRecordMapper handleRecordMapper;

    /** 填充导出用的统计来源中文列（列表接口一并带上，前端不使用、也不受影响）。 */
    private static void fillStatSourceLabel(List<VegHandleRecordVo> rows) {
        for (VegHandleRecordVo row : rows) {
            String source = row.getStatSource();
            if (SOURCE_VEG_HANDLE.equals(source)) {
                row.setStatSourceLabel("毛菜处理间");
            } else if (SOURCE_PICK_ACTIVITY.equals(source)) {
                row.setStatSourceLabel("采摘活动");
            } else {
                row.setStatSourceLabel(source);
            }
        }
    }

    @Override
    public TableDataInfo<VegHandleRecordVo> queryPage(VegHandleRecordQuery query, PageQuery pageQuery) {
        // query 可能为 null（无任何筛选项时），统一兜底空对象，避免 mapper <if> NPE
        VegHandleRecordQuery q = query != null ? query : new VegHandleRecordQuery();
        // V1 固定租户 '1001'；自定义 UNION SQL 不走拦截器自动注入，显式取当前租户传入 WHERE
        String tenantId = TenantHelper.getTenantId();
        Page<VegHandleRecordVo> page = handleRecordMapper.selectVegHandleRecordPage(pageQuery.build(), tenantId, q);
        fillStatSourceLabel(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<VegHandleRecordVo> queryList(VegHandleRecordQuery query) {
        VegHandleRecordQuery q = query != null ? query : new VegHandleRecordQuery();
        String tenantId = TenantHelper.getTenantId();
        List<VegHandleRecordVo> rows = handleRecordMapper.selectVegHandleRecordList(tenantId, q);
        fillStatSourceLabel(rows);
        return rows;
    }
}
