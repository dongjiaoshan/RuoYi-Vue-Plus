package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.service.DictService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.flow.constant.FlowDisplayScope;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatDetailQuery;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInDetailVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutDetailVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutVo;
import org.dromara.djs.warehouse.flow.mapper.InoutStatMapper;
import org.dromara.djs.warehouse.flow.service.IInoutStatService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 出入库统计实现（V6-R167）。
 *
 * <p>聚合全在 {@link InoutStatMapper} 的 SQL 里；本层只做三件事：
 * 取租户、翻字典 label、空值兜底。</p>
 *
 * <p><b>字典翻译放后端</b>（不走前端 dict-tag + Excel 双通道）：「无供应商」「未指定」这类
 * 非字典兜底值只写一处，导出与列表必然逐列一致（甲方 row167 第 5 点）。</p>
 *
 * <p>展示口径排除清单与「入库记录」「出库记录」「出入库月汇总」共用 {@link FlowDisplayScope}
 * ——甲方拿本页去对明细页，两边行集必须同集合。</p>
 *
 * <p>行内「查看详情」（V6-R186）走 mapper 里与汇总共用的 FROM / WHERE，本层同样只做翻字典 /
 * 拼单位 / 兜空：明细的入出库量与汇总同一列 {@code change_quantity}，
 * 空供应商与空出库去向套同一份兜底文案，甲方逐条加起来必然等于汇总行。</p>
 *
 * @author djs
 * @since V6-R167
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InoutStatServiceImpl implements IInoutStatService {

    /** V1 单农场固定租户。 */
    private static final String DEFAULT_TENANT = "1001";

    private static final String DICT_PRODUCT_TYPE = "djs_product_type";
    private static final String DICT_FLOW_TYPE = "djs_flow_type";
    private static final String DICT_STOCK_OUT_DEST = "djs_stock_out_dest";

    /** 甲方「供应商为空的统计到一起」——那一行显示成这个，而不是空白。 */
    private static final String NO_SUPPLIER = "无供应商";
    /** 出库去向为空 / 字典未命中时的显示值。 */
    private static final String NO_OUT_DEST = "未指定";
    /** 规格等可空文本的占位。 */
    private static final String EMPTY_TEXT = "-";
    /** 计重单位：落在这里（或单位缺失）的量按 3 位小数 + kg 展示，其余按「数值 空格 单位」。 */
    private static final String KG_UNIT = "kg";

    private final InoutStatMapper inoutStatMapper;
    private final DictService dictService;

    @Override
    public TableDataInfo<InoutStatInVo> queryInPage(InoutStatQuery query, PageQuery pageQuery) {
        IPage<InoutStatInVo> page = inoutStatMapper.selectInStatPage(
            pageQuery.build(), currentTenant(), nullSafe(query), FlowDisplayScope.IN_EXCLUDED);
        decorateIn(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<InoutStatInVo> queryInList(InoutStatQuery query) {
        List<InoutStatInVo> list = inoutStatMapper.selectInStatList(
            currentTenant(), nullSafe(query), FlowDisplayScope.IN_EXCLUDED);
        decorateIn(list);
        return list == null ? List.of() : list;
    }

    @Override
    public TableDataInfo<InoutStatOutVo> queryOutPage(InoutStatQuery query, PageQuery pageQuery) {
        IPage<InoutStatOutVo> page = inoutStatMapper.selectOutStatPage(
            pageQuery.build(), currentTenant(), nullSafe(query), FlowDisplayScope.OUT_EXCLUDED);
        decorateOut(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<InoutStatOutVo> queryOutList(InoutStatQuery query) {
        List<InoutStatOutVo> list = inoutStatMapper.selectOutStatList(
            currentTenant(), nullSafe(query), FlowDisplayScope.OUT_EXCLUDED);
        decorateOut(list);
        return list == null ? List.of() : list;
    }

    @Override
    public TableDataInfo<InoutStatInDetailVo> queryInDetailPage(InoutStatDetailQuery query, PageQuery pageQuery) {
        IPage<InoutStatInDetailVo> page = inoutStatMapper.selectInDetailPage(
            pageQuery.build(), currentTenant(), query, FlowDisplayScope.IN_EXCLUDED);
        decorateInDetail(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<InoutStatInDetailVo> queryInDetailList(InoutStatDetailQuery query) {
        List<InoutStatInDetailVo> list = inoutStatMapper.selectInDetailList(
            currentTenant(), query, FlowDisplayScope.IN_EXCLUDED);
        decorateInDetail(list);
        return list == null ? List.of() : list;
    }

    @Override
    public TableDataInfo<InoutStatOutDetailVo> queryOutDetailPage(InoutStatDetailQuery query, PageQuery pageQuery) {
        IPage<InoutStatOutDetailVo> page = inoutStatMapper.selectOutDetailPage(
            pageQuery.build(), currentTenant(), query, FlowDisplayScope.OUT_EXCLUDED);
        decorateOutDetail(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<InoutStatOutDetailVo> queryOutDetailList(InoutStatDetailQuery query) {
        List<InoutStatOutDetailVo> list = inoutStatMapper.selectOutDetailList(
            currentTenant(), query, FlowDisplayScope.OUT_EXCLUDED);
        decorateOutDetail(list);
        return list == null ? List.of() : list;
    }

    /**
     * 入库明细行加工（弹窗与明细导出共用）：拼带单位的入库量 + 空值兜底。
     *
     * <p>兜底文案与汇总行同一批常量：明细里那条流水的供应商为空时显示「无供应商」，
     * 与它所属汇总行的供应商列一字不差，否则甲方会以为点错了行。</p>
     *
     * @param list 待加工的行（可为 null / 空）
     */
    private void decorateInDetail(List<InoutStatInDetailVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (InoutStatInDetailVo vo : list) {
            vo.setInQtyLabel(qtyLabel(vo.getInboundQty(), vo.getProductUnit()));
            if (isBlank(vo.getSupplierName())) {
                vo.setSupplierName(NO_SUPPLIER);
            }
            if (isBlank(vo.getProductSpec())) {
                vo.setProductSpec(EMPTY_TEXT);
            }
            if (isBlank(vo.getOperatorName())) {
                vo.setOperatorName(EMPTY_TEXT);
            }
        }
    }

    /**
     * 出库明细行加工（弹窗与明细导出共用）：拼带单位的出库量 + 翻出库去向 + 空值兜底。
     *
     * @param list 待加工的行（可为 null / 空）
     */
    private void decorateOutDetail(List<InoutStatOutDetailVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (InoutStatOutDetailVo vo : list) {
            vo.setOutQtyLabel(qtyLabel(vo.getOutboundQty(), vo.getProductUnit()));
            vo.setOutDestName(dictLabel(DICT_STOCK_OUT_DEST, vo.getStockOutDest(), NO_OUT_DEST));
            if (isBlank(vo.getProductSpec())) {
                vo.setProductSpec(EMPTY_TEXT);
            }
            if (isBlank(vo.getOperatorName())) {
                vo.setOperatorName(EMPTY_TEXT);
            }
        }
    }

    /**
     * 量的带单位展示串 —— 与「毛菜间出库明细」的 {@code outQtyLabel} 逐字对齐。
     *
     * <p>kg 或单位缺失 → 3 位小数 + {@code kg}（如 {@code 12.000kg}）；
     * 其余计件单位 → 去尾零的数值 + 空格 + 单位（如 {@code 3 袋}）。</p>
     *
     * <p>页面与 Excel 读的都是这一个字段（单通道），所以甲方拿导出核对弹窗不会出现格式差。</p>
     *
     * @param qty  量（可为 null）
     * @param unit 产品单位（可为 null / 空）
     * @return 展示串；量为空时返回 "-"
     */
    private static String qtyLabel(BigDecimal qty, String unit) {
        if (qty == null) {
            return EMPTY_TEXT;
        }
        String u = unit != null ? unit.trim() : "";
        if (u.isEmpty() || KG_UNIT.equalsIgnoreCase(u)) {
            return qty.setScale(3, RoundingMode.HALF_UP).toPlainString() + KG_UNIT;
        }
        return qty.stripTrailingZeros().toPlainString() + " " + u;
    }

    /**
     * 入库行的字典翻译 + 空值兜底（列表与导出共用，保证两边逐列一致）。
     *
     * @param list 待加工的行（可为 null / 空）
     */
    private void decorateIn(List<InoutStatInVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (InoutStatInVo vo : list) {
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
    }

    /**
     * 出库行的字典翻译 + 空值兜底。
     *
     * @param list 待加工的行（可为 null / 空）
     */
    private void decorateOut(List<InoutStatOutVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (InoutStatOutVo vo : list) {
            vo.setProductTypeName(productTypeName(vo.getProductType()));
            vo.setOutDestName(dictLabel(DICT_STOCK_OUT_DEST, vo.getStockOutDest(), NO_OUT_DEST));
            if (isBlank(vo.getProductSpec())) {
                vo.setProductSpec(EMPTY_TEXT);
            }
            if (vo.getOutboundQty() == null) {
                vo.setOutboundQty(BigDecimal.ZERO);
            }
        }
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

    /**
     * query 兜空对象：前端一个筛选都不填时 Spring 仍会给命令对象，但 mp / 定时调用可能传 null，
     * mapper 的 {@code <if test="query.xxx">} 撞 null 会直接 OGNL 异常。
     *
     * @param query 原始查询条件
     * @return 非 null 的查询条件
     */
    private InoutStatQuery nullSafe(InoutStatQuery query) {
        return query != null ? query : new InoutStatQuery();
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
