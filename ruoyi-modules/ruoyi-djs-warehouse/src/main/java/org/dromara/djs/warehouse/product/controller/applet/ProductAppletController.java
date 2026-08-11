package org.dromara.djs.warehouse.product.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.common.util.LikeEscape;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.vo.ProductPickerVo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.service.IProductInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序产品 picker Controller（MP-UX-002）。
 *
 * <p>专给 mp {@code ProductPicker} 用，复用 {@link ProductInfoMapper#selectList}
 * 直接查 entity 转轻量 VO，不动 admin {@link org.dromara.djs.warehouse.product.service.IProductInfoService}。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/warehouse/product/list?belongType=&productType=&keyword=}
 *       仅返启用（productStatus=0）产品；按 keyword 同时 LIKE productName/productId。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin} + {@code @SaCheckPermission("djs:applet:warehouse:product:list")}
 * mp_role 默认含；菜单 seed 见 {@code V202606061100__MP-UX-002-applet-permission-menus.sql}。</p>
 *
 * @author djs
 * @since MP-UX-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/warehouse/product")
public class ProductAppletController {

    private final ProductInfoMapper productInfoMapper;
    private final IProductInfoService productInfoService;

    /**
     * 产品 picker 列表。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>仅返 {@code product_status = 0}（启用；与 sys_normal_disable 字典对齐）</li>
     *   <li>{@code belongType} 精确匹配（业务页常锁某归属，如毛菜处理锁 {@code vegetable_raw}、礼盒打包锁 {@code gift_box}）</li>
     *   <li>{@code productType} 精确匹配（1=自产 / 2=外购）</li>
     *   <li>{@code keyword} 同时 LIKE productName / productId</li>
     *   <li>结果按 ID 倒序，最多 200 条（picker 不分页）</li>
     * </ul>
     *
     * @param belongType      字典 {@code djs_belong_type} 精确匹配（礼盒 = {@code gift_box}）
     * @param productType     字典 {@code djs_product_type}：1=自产 / 2=外购（已废弃 3 礼盒）
     * @param keyword         关键字（同时 LIKE productName / productId）
     * @param excludeMaterial {@code true} 排除自产原料（{@code product_type=1 且 product_attr=2}），
     *                        门店下单 picker 用——原料是仓库内部流转、不可下单（doc/14 §5）。
     *                        默认 {@code false}（不排除，其余 picker 如打包/盘点不受影响）。
     * @param sellableOnly    {@code true} 只返自产产品（{@code product_type=1}，含礼盒），门店下单 picker 用——
     *                        外购商品（{@code =2}）是生产投入品（种子/药品/农药/肥料/饲料/包材/设备），走采购入库 →
     *                        物资领用，不可被门店下单（doc/14 §5）。默认 {@code false}（不限定，其余 picker 不受影响）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:product:list")
    @GetMapping("/list")
    public R<List<ProductPickerVo>> list(
        @RequestParam(required = false) String belongType,
        @RequestParam(required = false) Integer productType,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean excludeMaterial,
        @RequestParam(required = false) Boolean sellableOnly
    ) {
        boolean exMat = Boolean.TRUE.equals(excludeMaterial);
        boolean sellable = Boolean.TRUE.equals(sellableOnly);
        LambdaQueryWrapper<ProductInfo> wrapper = new LambdaQueryWrapper<ProductInfo>()
            .eq(ProductInfo::getProductStatus, 0)
            .eq(StringUtils.isNotBlank(belongType), ProductInfo::getBelongType, belongType)
            .eq(productType != null, ProductInfo::getProductType, productType)
            // 只保留自产（含礼盒）：门店下单链路专用
            .eq(sellable, ProductInfo::getProductType, 1)
            // 排除自产原料：保留「非自产 或 非原料 或 attr 未设」，即仅剔除 type=1 且 attr=2 的原料
            .and(exMat, w -> w
                .ne(ProductInfo::getProductType, 1)
                .or()
                .ne(ProductInfo::getProductAttr, 2)
                .or()
                .isNull(ProductInfo::getProductAttr))
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(ProductInfo::getProductName, LikeEscape.escape(keyword))
                .or()
                .like(ProductInfo::getProductId, LikeEscape.escape(keyword)))
            .orderByDesc(ProductInfo::getId)
            .last("LIMIT 200");
        List<ProductInfo> rows = productInfoMapper.selectList(wrapper);
        List<ProductPickerVo> vos = rows.stream().map(p -> {
            ProductPickerVo vo = new ProductPickerVo();
            vo.setId(p.getId());
            vo.setProductCode(p.getProductId());
            vo.setProductName(p.getProductName());
            vo.setProductType(p.getProductType());
            vo.setProductUnit(p.getProductUnit());
            vo.setProductSpec(p.getProductSpec());
            vo.setBelongType(p.getBelongType());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

    /**
     * 按产品取其「存储仓库」配置的启用库位（mp LocationPicker 通用）。
     *
     * <p>读 {@code product.store_location_id}（逗号分隔库位 ID 串）→ {@code IN(ids)} 且
     * {@code location_status=1} 查启用库位，按配置顺序返回。可选 {@code locationType} 精确过滤。
     * 产品未配置存储仓库（store_location_id 空）→ 返回空列表（前端回落自由选库位）。</p>
     *
     * @param productId    产品 ID
     * @param locationType 字典 {@code djs_location_type}（可空，非空时精确过滤）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:product:list")
    @GetMapping("/{productId}/storeLocations")
    public R<List<LocationPickerVo>> storeLocations(
        @PathVariable Long productId,
        @RequestParam(required = false) String locationType
    ) {
        return R.ok(productInfoService.queryStoreLocations(productId, locationType));
    }

}
