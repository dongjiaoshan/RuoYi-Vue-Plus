package org.dromara.djs.warehouse.cutout.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyDetailQuery;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyQuery;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyDetailVo;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyVo;
import org.dromara.djs.warehouse.cutout.mapper.CutOutDailyMapper;
import org.dromara.djs.warehouse.cutout.service.ICutOutDailyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 猪肉日分割出库统计实现（V7-R6）。
 *
 * <p>统计范围与判别条件全在 {@link CutOutDailyMapper} 的 WHERE 常量里；本层只做：
 * 按筛选决定开哪个分支、取租户、翻出库去向 label、量统一 3 位小数、空值兜底。</p>
 *
 * <p><b>出库去向 label 放后端翻</b>：「门店需求」「未指定」这两个非字典展示值只写一处，
 * 列表与前端不会各写一份对不上。</p>
 *
 * @author djs
 * @since V7-R6
 */
@Service
@RequiredArgsConstructor
public class CutOutDailyServiceImpl implements ICutOutDailyService {

    /** V1 单农场固定租户。 */
    private static final String DEFAULT_TENANT = "1001";

    private static final String DICT_STOCK_OUT_DEST = "djs_stock_out_dest";

    /** 门店需求行的出库去向展示值（甲方原文「门店需求」）。 */
    private static final String LABEL_STORE_DEMAND = "门店需求";
    /** 出库去向为空 / 字典未命中时的展示值（与「出入库统计」同一兜底文案）。 */
    private static final String NO_OUT_DEST = "未指定";
    /** 可空文本占位。 */
    private static final String EMPTY_TEXT = "-";
    /** 本报表统计的都是称重出库（分割产出入篮称重 / 产出记录实称），单位恒 kg。 */
    private static final String KG_UNIT = "kg";

    private final CutOutDailyMapper cutOutDailyMapper;
    private final DictService dictService;

    @Override
    public TableDataInfo<CutOutDailyVo> queryPage(CutOutDailyQuery query, PageQuery pageQuery) {
        CutOutDailyQuery q = query != null ? query : new CutOutDailyQuery();
        if (isBlank(q.getOutDest())) {
            q.setOutDest(null);
        }
        boolean storeDemand = CutOutDailyQuery.OUT_DEST_STORE_DEMAND.equals(q.getOutDest());
        // 选了「门店需求」或选了某个门店 → 只看门店需求；选了某个字典去向 → 只看仓库出库。
        boolean withWarehouse = !storeDemand && q.getStoreId() == null;
        boolean withStore = q.getOutDest() == null || storeDemand;
        if (!withWarehouse && !withStore) {
            // 字典去向 + 门店同时选：门店只属于门店需求，两个条件按定义交集为空
            Page<CutOutDailyVo> empty = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
            return TableDataInfo.build(empty);
        }
        IPage<CutOutDailyVo> page = cutOutDailyMapper.selectDailyPage(
            pageQuery.build(), currentTenant(), q, withWarehouse, withStore);
        decorate(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<CutOutDailyDetailVo> queryDetail(CutOutDailyDetailQuery query) {
        List<CutOutDailyDetailVo> list;
        if (CutOutDailyDetailQuery.DEST_TYPE_STORE.equals(query.getDestType())) {
            if (query.getStoreId() == null) {
                throw new ServiceException("查看门店需求明细必须指定门店");
            }
            list = cutOutDailyMapper.selectStoreDetail(currentTenant(), query);
        } else if (CutOutDailyDetailQuery.DEST_TYPE_WAREHOUSE.equals(query.getDestType())) {
            list = cutOutDailyMapper.selectWarehouseDetail(currentTenant(), query);
        } else {
            throw new ServiceException("出库类别只能是 warehouse 或 store");
        }
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        for (CutOutDailyDetailVo vo : list) {
            vo.setQuantity(scale3(vo.getQuantity()));
            vo.setUnit(KG_UNIT);
            if (isBlank(vo.getProductCode())) {
                vo.setProductCode(EMPTY_TEXT);
            }
            if (isBlank(vo.getProductName())) {
                vo.setProductName(EMPTY_TEXT);
            }
        }
        return list;
    }

    /**
     * 列表行加工：出库去向 label + 重量 3 位小数。
     *
     * @param list 待加工的行（可为 null / 空）
     */
    private void decorate(List<CutOutDailyVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (CutOutDailyVo vo : list) {
            if (CutOutDailyDetailQuery.DEST_TYPE_STORE.equals(vo.getDestType())) {
                vo.setOutDestLabel(LABEL_STORE_DEMAND);
            } else {
                vo.setOutDestLabel(dictLabel(vo.getOutDest()));
                vo.setStoreId(null);
                vo.setStoreName(null);
            }
            vo.setTotalWeight(scale3(vo.getTotalWeight()));
        }
    }

    /**
     * 出库去向字典 label；原始值为空 / 字典查不到时兜「未指定」。
     *
     * @param value djs_stock_out_dest 原始值
     * @return 展示文案
     */
    private String dictLabel(String value) {
        if (isBlank(value)) {
            return NO_OUT_DEST;
        }
        String label = dictService.getDictLabel(DICT_STOCK_OUT_DEST, value);
        return isBlank(label) ? NO_OUT_DEST : label;
    }

    private static BigDecimal scale3(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(3, RoundingMode.HALF_UP);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 当前租户（V1 固定 '1001'）。
     *
     * @return 租户 id
     */
    private String currentTenant() {
        String t = TenantHelper.getTenantId();
        return isBlank(t) ? DEFAULT_TENANT : t;
    }
}
