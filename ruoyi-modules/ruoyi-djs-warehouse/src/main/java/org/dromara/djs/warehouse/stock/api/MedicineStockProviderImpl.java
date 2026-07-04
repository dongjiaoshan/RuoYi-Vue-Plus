package org.dromara.djs.warehouse.stock.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.medicine.api.MedicineProductDto;
import org.dromara.djs.common.medicine.api.MedicineStockProvider;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 药品库存提供方仓库侧实现（药品归仓库统一 · 药品即仓库商品 {@code buy_class='medicine'}）。
 *
 * <p>药品主数据落 {@code t_warehouse_product_info}（{@code buy_class='medicine'}）、库存真值落
 * {@code t_warehouse_location_stock}（product 维）。养殖 med 域经 common 的
 * {@link MedicineStockProvider} facade 消费药品清单 + 库存，零循环依赖。</p>
 *
 * <p>扣减 / 退回落到该药品商品库存所在库位（{@link LocationStockMapper#selectDefaultLocationByProduct}
 * 取库存最多的库位），复用仓库物资领用同一 product 维原子扣减范式（{@code WHERE product_stock>=qty}
 * 行锁 + 数量校验同步）。{@code tenant_id} 由 MP 拦截器在 final SQL 阶段注入。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicineStockProviderImpl implements MedicineStockProvider {

    /** 药品图 belong_type（IMG-LIB-001 resolver L2 分类默认图键；无配置则回落全局默认/空）。 */
    private static final String MEDICINE_BELONG_TYPE = "medicine";

    private final LocationStockMapper locationStockMapper;
    private final ImageUrlResolver imageUrlResolver;

    @Override
    public List<MedicineProductDto> listMedicineProducts(String keyword) {
        return toDtoList(locationStockMapper.selectMedicineProducts(keyword));
    }

    @Override
    public List<MedicineProductDto> listMedicineProductsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return toDtoList(locationStockMapper.selectMedicineProductsByIds(ids));
    }

    /**
     * 药品商品行（id/name/unit/spec/imageId/stock）→ {@link MedicineProductDto}。
     * imageId 原为 {@code COALESCE(product_thumb, image_oss_id)} 裸 ossId，经 IMG-LIB-001 resolver
     * 批量转 OSS public URL（禁 N+1）供 mp {@code <image :src>} 直接渲染；无图返 null（mp 端占位兜底）。
     */
    private List<MedicineProductDto> toDtoList(List<Map<String, Object>> rows) {
        List<MedicineProductDto> result = new ArrayList<>(rows.size());
        List<ImageUrlResolver.Item> imgItems = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            MedicineProductDto dto = new MedicineProductDto();
            dto.setId(toLong(row.get("id")));
            dto.setName(toStr(row.get("name")));
            dto.setUnit(toStr(row.get("unit")));
            dto.setSpec(toStr(row.get("spec")));
            dto.setStock(toBigDecimal(row.get("stock")));
            result.add(dto);
            imgItems.add(new ImageUrlResolver.Item(toStr(row.get("imageId")), MEDICINE_BELONG_TYPE));
        }
        List<String> urls = imageUrlResolver.resolveList(imgItems);
        if (urls.size() == result.size()) {
            for (int i = 0; i < result.size(); i++) {
                result.get(i).setImageUrl(urls.get(i));
            }
        }
        return result;
    }

    @Override
    public void deduct(Long productId, BigDecimal qty, Long operatorId) {
        Long locationId = locationStockMapper.selectDefaultLocationByProduct(productId);
        if (locationId == null) {
            throw new ServiceException("药品无库存，无法领用：" + productId);
        }
        int aff = locationStockMapper.deductByProductLocation(locationId, productId, qty, operatorId);
        if (aff == 0) {
            throw new ServiceException("药品库存不足");
        }
    }

    @Override
    public void add(Long productId, BigDecimal qty, Long operatorId) {
        Long locationId = locationStockMapper.selectDefaultLocationByProduct(productId);
        if (locationId == null) {
            throw new ServiceException("药品无库位记录，无法退回：" + productId);
        }
        int aff = locationStockMapper.addByProductLocation(locationId, productId, qty, operatorId);
        if (aff == 0) {
            throw new ServiceException("药品退回失败：库存行不存在");
        }
    }

    @Override
    public Map<Long, BigDecimal> getStocks(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = locationStockMapper.selectProductStocks(productIds);
        Map<Long, BigDecimal> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long productId = toLong(row.get("product_id"));
            BigDecimal stock = toBigDecimal(row.get("stock"));
            if (productId != null && stock != null) {
                result.put(productId, stock);
            }
        }
        return result;
    }

    /**
     * {@code product_id} 列取值转 {@link Long}（JDBC 可能返 BigInteger / Long / Number）。
     */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.longValue();
        }
        return Long.valueOf(value.toString());
    }

    /**
     * {@code SUM(product_stock)} 列取值转 {@link BigDecimal}（MySQL SUM 返 BigDecimal，
     * 兜底处理 Number 子类）。
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal dec) {
            return dec;
        }
        if (value instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        return new BigDecimal(value.toString());
    }

    private String toStr(Object value) {
        return value == null ? null : value.toString();
    }

}
