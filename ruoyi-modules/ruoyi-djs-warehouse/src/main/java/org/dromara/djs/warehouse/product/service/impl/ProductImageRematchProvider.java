package org.dromara.djs.warehouse.product.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.image.api.ImageRematchProvider;
import org.dromara.djs.common.image.service.IImageLibraryService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 商品 / 产品图片重新匹配 provider（IMG-LIB-001）。
 *
 * <p>对所有 {@code image_source=0}（自动匹配）的产品按 productName 重跑
 * {@link IImageLibraryService#match}，写回 {@code image_oss_id}。{@code image_source=1}（手改）的行不动。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductImageRematchProvider implements ImageRematchProvider {

    private final ProductInfoMapper productInfoMapper;
    private final IImageLibraryService imageLibraryService;

    @Override
    public int rematchAll() {
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>().eq(ProductInfo::getImageSource, 0));
        int updated = 0;
        for (ProductInfo p : products) {
            String matched = imageLibraryService.match(p.getProductName());
            if (!Objects.equals(StrUtil.emptyToNull(matched), StrUtil.emptyToNull(p.getImageOssId()))) {
                productInfoMapper.update(null, Wrappers.<ProductInfo>update()
                    .eq("id", p.getId())
                    .set("image_oss_id", StrUtil.emptyToNull(matched)));
                updated++;
            }
        }
        log.info("IMG-LIB-001 商品图重新匹配：扫描 {} 行，更新 {} 行", products.size(), updated);
        return updated;
    }

    @Override
    public String domainName() {
        return "product";
    }

}
