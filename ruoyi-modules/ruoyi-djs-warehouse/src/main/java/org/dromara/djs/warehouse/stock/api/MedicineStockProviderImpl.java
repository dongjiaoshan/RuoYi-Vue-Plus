package org.dromara.djs.warehouse.stock.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.common.medicine.api.MedicineStockProvider;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 药品库存提供方仓库侧实现（ADR-0012 药品归仓库库位统一 · FIX-MED-MODEL-004/005）。
 *
 * <p>库存真值落仓库 {@code t_warehouse_location_stock}（medicine 维），统一落到
 * {@code location_type='medicine'} 的默认药品库位（V1 单药品库位 L0012）。养殖 med 域
 * 经 common 的 {@link MedicineStockProvider} facade 消费，零循环依赖。</p>
 *
 * <p>并发安全复用仓库现有原子扣减范式（{@code WHERE product_stock>=qty} 行锁 + 数量校验同步）。
 * {@code tenant_id} 由 MP 拦截器在 final SQL 阶段注入，应用层不显式赋值（INSERT 走
 * {@code InjectionMetaObjectHandler}，原子 UPDATE 无显式 tenant WHERE）。</p>
 *
 * @author djs
 * @since FIX-MED-MODEL-005
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicineStockProviderImpl implements MedicineStockProvider {

    private final LocationStockMapper locationStockMapper;
    private final MedicineMapper medicineMapper;

    @Override
    public void deduct(Long medicineId, BigDecimal qty, Long operatorId) {
        Long locationId = locationStockMapper.selectDefaultMedicineLocationId();
        if (locationId == null) {
            throw new ServiceException("未配置药品库位");
        }
        int aff = locationStockMapper.deductByMedicineLocation(locationId, medicineId, qty, operatorId);
        if (aff == 0) {
            throw new ServiceException("药品库存不足或库存行不存在");
        }
    }

    @Override
    public void add(Long medicineId, BigDecimal qty, Long operatorId) {
        Long locationId = locationStockMapper.selectDefaultMedicineLocationId();
        if (locationId == null) {
            throw new ServiceException("未配置药品库位");
        }
        int aff = locationStockMapper.addByMedicineLocation(locationId, medicineId, qty, operatorId);
        if (aff == 0) {
            // 无库存行 → 新建一行（product_name / product_unit 取药品主数据）
            Medicine medicine = medicineMapper.selectById(medicineId);
            if (medicine == null) {
                throw new ServiceException("药品不存在或已删除：" + medicineId);
            }
            LocationStock row = new LocationStock();
            row.setLocationId(locationId);
            row.setMedicineId(medicineId);
            row.setProductStock(qty);
            row.setProductName(medicine.getMedicineName());
            row.setProductUnit(medicine.getUnit());
            row.setIsEnd(0);
            row.setOperatorId(operatorId);
            // 不显式赋 tenant_id，走 InjectionMetaObjectHandler.insertFill
            locationStockMapper.insert(row);
        }
    }

    @Override
    public Map<Long, BigDecimal> getStocks(Collection<Long> medicineIds) {
        if (medicineIds == null || medicineIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = locationStockMapper.selectMedicineStocks(medicineIds);
        Map<Long, BigDecimal> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long medicineId = toLong(row.get("medicine_id"));
            BigDecimal stock = toBigDecimal(row.get("stock"));
            if (medicineId != null && stock != null) {
                result.put(medicineId, stock);
            }
        }
        return result;
    }

    /**
     * {@code medicine_id} 列取值转 {@link Long}（JDBC 可能返 BigInteger / Long / Number）。
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
            return null;
        }
        if (value instanceof BigDecimal dec) {
            return dec;
        }
        if (value instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        return new BigDecimal(value.toString());
    }

}
