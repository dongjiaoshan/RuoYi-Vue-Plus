package org.dromara.djs.warehouse.product.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.vo.ProductPickerVo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * 产品 picker 列表。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>仅返 {@code product_status = 0}（启用；与 sys_normal_disable 字典对齐）</li>
     *   <li>{@code belongType} 精确匹配（业务页常锁某归属，如毛菜处理锁 {@code vegetable_raw}）</li>
     *   <li>{@code productType} 精确匹配（如礼盒装箱锁 {@code productType=3}）</li>
     *   <li>{@code keyword} 同时 LIKE productName / productId</li>
     *   <li>结果按 ID 倒序，最多 200 条（picker 不分页）</li>
     * </ul>
     *
     * @param belongType  字典 {@code djs_belong_type} 精确匹配
     * @param productType 字典 {@code djs_product_type}：1=自产 / 2=外购 / 3=礼盒
     * @param keyword     关键字（同时 LIKE productName / productId）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:product:list")
    @GetMapping("/list")
    public R<List<ProductPickerVo>> list(
        @RequestParam(required = false) String belongType,
        @RequestParam(required = false) Integer productType,
        @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<ProductInfo> wrapper = new LambdaQueryWrapper<ProductInfo>()
            .eq(ProductInfo::getProductStatus, 0)
            .eq(StringUtils.isNotBlank(belongType), ProductInfo::getBelongType, belongType)
            .eq(productType != null, ProductInfo::getProductType, productType)
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(ProductInfo::getProductName, keyword)
                .or()
                .like(ProductInfo::getProductId, keyword))
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

}
