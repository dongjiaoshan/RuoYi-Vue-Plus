package org.dromara.djs.warehouse.flow.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.service.DictService;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.flow.constant.FlowDisplayScope;
import org.dromara.djs.warehouse.flow.domain.query.InoutSummaryQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutMonthVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryOutVo;
import org.dromara.djs.warehouse.flow.mapper.InoutMonthlyMapper;
import org.dromara.djs.warehouse.flow.service.IInoutMonthlyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 出入库月汇总实现（V6-R154 / R155 / R156）。
 *
 * <p>聚合全在 {@link InoutMonthlyMapper} 的 SQL 里；本层只做三件事：
 * 取租户、翻字典 label、空值兜底。</p>
 *
 * <p><b>字典翻译放后端</b>（不走前端 dict-tag + Excel 双通道）：「无供应商」「未指定」这类
 * 非字典兜底值只写一处，导出与列表必然逐列一致（甲方 row155/156 第 4 点）。</p>
 *
 * @author djs
 * @since V6-R154
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InoutMonthlyServiceImpl implements IInoutMonthlyService {

    /** V1 单农场固定租户。 */
    private static final String DEFAULT_TENANT = "1001";

    private static final String DICT_PRODUCT_TYPE = "djs_product_type";
    private static final String DICT_FLOW_TYPE = "djs_flow_type";
    private static final String DICT_STOCK_OUT_DEST = "djs_stock_out_dest";

    /** 甲方「供应商字段为空的，就统计到一起」——那一行显示成这个，而不是空白。 */
    private static final String NO_SUPPLIER = "无供应商";
    /** 出库去向为空 / 字典未命中时的显示值。 */
    private static final String NO_OUT_DEST = "未指定";
    /** 规格等可空文本的占位。 */
    private static final String EMPTY_TEXT = "-";

    private final InoutMonthlyMapper inoutMonthlyMapper;
    private final DictService dictService;

    @Override
    public List<InoutMonthVo> queryMonths(String statMonth) {
        List<InoutMonthVo> list = inoutMonthlyMapper.selectMonths(
            currentTenant(),
            blankToNull(statMonth),
            FlowDisplayScope.IN_EXCLUDED,
            FlowDisplayScope.OUT_EXCLUDED);
        return list == null ? List.of() : list;
    }

    @Override
    public List<InoutSummaryInVo> queryInSummary(InoutSummaryQuery query) {
        List<InoutSummaryInVo> list = inoutMonthlyMapper.selectInSummary(
            currentTenant(), query, FlowDisplayScope.IN_EXCLUDED);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        for (InoutSummaryInVo vo : list) {
            vo.setProductTypeName(productTypeName(vo.getProductType()));
            vo.setInModeName(dictLabel(DICT_FLOW_TYPE, vo.getFlowType(), vo.getFlowType()));
            if (isBlank(vo.getSupplierName())) {
                vo.setSupplierName(NO_SUPPLIER);
            }
            if (isBlank(vo.getProductSpec())) {
                vo.setProductSpec(EMPTY_TEXT);
            }
            if (vo.getInboundQty() == null) {
                vo.setInboundQty(BigDecimal.ZERO);
            }
        }
        return list;
    }

    @Override
    public List<InoutSummaryOutVo> queryOutSummary(InoutSummaryQuery query) {
        List<InoutSummaryOutVo> list = inoutMonthlyMapper.selectOutSummary(
            currentTenant(), query, FlowDisplayScope.OUT_EXCLUDED);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        for (InoutSummaryOutVo vo : list) {
            vo.setProductTypeName(productTypeName(vo.getProductType()));
            vo.setOutDestName(dictLabel(DICT_STOCK_OUT_DEST, vo.getStockOutDest(), NO_OUT_DEST));
            if (isBlank(vo.getProductSpec())) {
                vo.setProductSpec(EMPTY_TEXT);
            }
            if (vo.getOutboundQty() == null) {
                vo.setOutboundQty(BigDecimal.ZERO);
            }
        }
        return list;
    }

    /**
     * 产品类型展示文案：djs_product_type 字典 label；字典缺项时退回原始数字，产品类型为空时退回 "-"。
     *
     * @param productType 产品类型原始值（1 自产 / 2 外购）
     * @return 展示文案
     */
    private String productTypeName(Integer productType) {
        if (productType == null) {
            return EMPTY_TEXT;
        }
        String raw = String.valueOf(productType);
        return dictLabel(DICT_PRODUCT_TYPE, raw, raw);
    }

    /**
     * 翻字典 label；原始值为空或字典查不到时回落到 fallback。
     *
     * @param dictType 字典类型
     * @param value    原始值
     * @param fallback 查不到时的兜底展示
     * @return 展示文案
     */
    private String dictLabel(String dictType, String value, String fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        String label = dictService.getDictLabel(dictType, value);
        return isBlank(label) ? fallback : label;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s;
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
