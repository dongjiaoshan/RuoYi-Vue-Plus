package org.dromara.djs.warehouse.product.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.domain.vo.InhouseSourcePickerVo;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 小程序过程产品来源 picker Controller（MP-PICKERS-001）。
 *
 * <p>专给 mp {@code InhouseSourcePicker} 用，复用 {@link ProductInhouseMapper#selectList}
 * 直接查 entity（表 {@code t_warehouse_product_inhouse}）转轻量 VO。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/warehouse/inhouse/listAvailable?productId=&locationId=}
 *       返回剩余重量 > 0 的过程产品，可按 productId / locationId 过滤，按 ID 倒序最多 200 条。</li>
 * </ul>
 *
 * <h2>locationCode 来源</h2>
 * <p>inhouse 主表只存 location_id；本 controller 批量 JOIN
 * {@code t_warehouse_location_info.location_code} 填充 locationCode。</p>
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
@RequestMapping("/djs/applet/warehouse/inhouse")
public class AppletInhouseSourcePickerController {

    private final ProductInhouseMapper productInhouseMapper;

    private final LocationInfoMapper locationInfoMapper;

    /**
     * 可用过程产品列表（picker 用，轻量 VO）。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>{@code product_weight > 0}（剩余重量为正才可作来源）</li>
     *   <li>{@code productId} 精确匹配（业务页常锁某产品）</li>
     *   <li>{@code locationId} 精确匹配（业务页常锁某冻品/鲜品库位）</li>
     *   <li>结果按 ID 倒序，最多 200 条（picker 不分页）</li>
     * </ul>
     *
     * @param productId  产品 FK，可空
     * @param locationId 入库库位 FK，可空
     */
    @SaCheckLogin
    @GetMapping("/listAvailable")
    public R<List<InhouseSourcePickerVo>> listAvailable(
        @RequestParam(required = false) Long productId,
        @RequestParam(required = false) Long locationId
    ) {
        List<ProductInhouse> rows = productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
                .eq(productId != null, ProductInhouse::getProductId, productId)
                .eq(locationId != null, ProductInhouse::getLocationId, locationId)
                .orderByDesc(ProductInhouse::getId)
                .last("LIMIT 200"));
        if (rows.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        // 批量取 location_code，避免 N+1
        List<Long> locationIds = rows.stream()
            .map(ProductInhouse::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> locationCodeMap = locationIds.isEmpty()
            ? Collections.emptyMap()
            : locationInfoMapper.selectList(new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds))
                .stream()
                .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationCode, (a, b) -> a));
        List<InhouseSourcePickerVo> vos = rows.stream().map(p -> {
            InhouseSourcePickerVo vo = new InhouseSourcePickerVo();
            vo.setId(p.getId());
            vo.setProductName(p.getProductName());
            vo.setWeight(p.getProductWeight());
            vo.setUnit(p.getProductUnit());
            vo.setLocationCode(p.getLocationId() == null ? null : locationCodeMap.get(p.getLocationId()));
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

}
