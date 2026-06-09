package org.dromara.djs.warehouse.stockquery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stockquery.domain.query.StockQueryFlowQuery;
import org.dromara.djs.warehouse.stockquery.domain.query.StockQueryStockQuery;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryFlowVo;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryItemVo;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryStatVo;
import org.dromara.djs.warehouse.stockquery.mapper.StockQueryMapper;
import org.dromara.djs.warehouse.stockquery.service.IStockQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * mp 库存查询 hub Service 实现（FIX-WMS-MP-STOCKQUERY-001）。
 *
 * <p>全 read-only。库存 / 流水查询走独占 {@link StockQueryMapper} 注解 SQL（直接 JOIN 主数据回填名称）；
 * 产品名筛选场景先用 {@link ProductInfoMapper} 反查 product_id 集合再 IN 下推（参 StockFlowServiceImpl
 * buildWrapper matType 反查模式，避免在动态 SQL 里跨表 LIKE）。统计聚合直接走 group by SQL。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-STOCKQUERY-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockQueryServiceImpl implements IStockQueryService {

    /**
     * 退货统计 flow_type（djs_flow_type 字典权威 value）。
     */
    private static final String FLOW_TYPE_RETURN = "return_in";

    /**
     * 损耗统计 flow_type（djs_flow_type 字典权威 value）。
     */
    private static final String FLOW_TYPE_LOSS = "loss";

    private final StockQueryMapper stockQueryMapper;
    private final ProductInfoMapper productInfoMapper;

    @Override
    public TableDataInfo<StockQueryItemVo> queryStockPage(StockQueryStockQuery query, PageQuery pageQuery) {
        QueryWrapper<Object> ew = new QueryWrapper<>();
        // 固定条件（customSqlSegment 自带 WHERE，全部条件由 wrapper 承载）
        ew.eq("s.del_flag", "0")
            .eq("s.tenant_id", "1001")
            .isNotNull("s.product_id");
        if (query != null) {
            ew.eq(query.getLocationId() != null, "s.location_id", query.getLocationId());
            ew.like(StringUtils.isNotBlank(query.getProductName()), "s.product_name", query.getProductName());
        }
        ew.orderByDesc("s.id");
        Page<StockQueryItemVo> page = stockQueryMapper.selectStockPage(pageQuery.build(), ew);
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<StockQueryFlowVo> queryFlowPage(StockQueryFlowQuery query, PageQuery pageQuery) {
        QueryWrapper<Object> ew = new QueryWrapper<>();
        // 固定条件（customSqlSegment 自带 WHERE，全部条件由 wrapper 承载）
        ew.eq("f.del_flag", "0").eq("f.tenant_id", "1001");
        String inoutType = query == null ? null : query.getInoutType();
        ew.eq(StringUtils.isNotBlank(inoutType), "f.inout_type", inoutType);

        // 产品名筛选 → 反查 product_id 集合（无命中 → 兜底空集，流水必空）
        if (query != null && StringUtils.isNotBlank(query.getProductName())) {
            List<Long> productIds = productInfoMapper
                .selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .like(ProductInfo::getProductName, query.getProductName()))
                .stream()
                .map(ProductInfo::getId)
                .toList();
            if (productIds.isEmpty()) {
                // 产品名无命中 → 直接返回空分页（带正确 total=0），不触 SQL
                Page<StockQueryFlowVo> empty = pageQuery.build();
                empty.setTotal(0);
                return TableDataInfo.build(empty);
            }
            ew.in("f.product_id", productIds);
        }

        if (query != null) {
            ew.ge(query.getDateFrom() != null, "f.flow_date", query.getDateFrom());
            ew.le(query.getDateTo() != null, "f.flow_date", query.getDateTo());
        }
        ew.orderByDesc("f.flow_date").orderByDesc("f.id");
        Page<StockQueryFlowVo> page = stockQueryMapper.selectFlowPage(pageQuery.build(), ew);
        return TableDataInfo.build(page);
    }

    @Override
    public List<StockQueryStatVo> queryReturnStat() {
        return stockQueryMapper.selectStatByFlowType(FLOW_TYPE_RETURN);
    }

    @Override
    public List<StockQueryStatVo> queryLossStat() {
        return stockQueryMapper.selectStatByFlowType(FLOW_TYPE_LOSS);
    }

}
