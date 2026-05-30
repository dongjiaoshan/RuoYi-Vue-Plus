package org.dromara.djs.warehouse.demand.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPickerVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 小程序需求单 picker Controller（MP-PICKERS-001）。
 *
 * <p>专给 mp {@code DemandPicker}（调度发货页）用，复用 {@link DemandManageMapper#selectList}
 * 直接查 entity 转轻量 VO，不动 admin {@code IDemandManageService}。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/warehouse/demand/listForDispatch}
 *       返回可调度发货的需求单（status IN CONFIRMED / IN_PRODUCTION），按 ID 倒序，最多 200 条。</li>
 * </ul>
 *
 * <h2>customerName 来源</h2>
 * <p>demand 主表只存 store_id；本 controller 批量 JOIN {@code t_md_store.store_name}
 * （Store 实体在 ruoyi-djs-common，warehouse 已依赖该模块）填充 customerName。</p>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin}（worker 角色可用，不加 @SaCheckPermission）。</p>
 *
 * @author djs
 * @since MP-PICKERS-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/warehouse/demand")
public class AppletDemandPickerController {

    private final DemandManageMapper demandManageMapper;

    private final StoreMapper storeMapper;

    /**
     * 调度发货可选需求单（picker 用，轻量 VO）。
     *
     * <p>固定过滤 {@code demand_status IN ('CONFIRMED','IN_PRODUCTION')}——只有这两态的需求
     * 才允许排产/发货调度；草稿/已提交不可发，已完成/已取消不必发。</p>
     */
    @SaCheckLogin
    @GetMapping("/listForDispatch")
    public R<List<DemandPickerVo>> listForDispatch() {
        List<DemandManage> rows = demandManageMapper.selectList(
            new LambdaQueryWrapper<DemandManage>()
                .in(DemandManage::getDemandStatus, List.of("CONFIRMED", "IN_PRODUCTION"))
                .orderByDesc(DemandManage::getId)
                .last("LIMIT 200"));
        if (rows.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        // 批量取 store_name，避免 N+1
        List<Long> storeIds = rows.stream()
            .map(DemandManage::getStoreId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> storeNameMap = storeIds.isEmpty()
            ? Collections.emptyMap()
            : storeMapper.selectList(new LambdaQueryWrapper<Store>().in(Store::getId, storeIds))
                .stream()
                .collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a));
        List<DemandPickerVo> vos = rows.stream().map(d -> {
            DemandPickerVo vo = new DemandPickerVo();
            vo.setId(d.getId());
            vo.setDemandNo(d.getDemandNo());
            vo.setCustomerName(d.getStoreId() == null ? null : storeNameMap.get(d.getStoreId()));
            vo.setProductName(d.getProductName());
            vo.setStatus(d.getDemandStatus());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

}
