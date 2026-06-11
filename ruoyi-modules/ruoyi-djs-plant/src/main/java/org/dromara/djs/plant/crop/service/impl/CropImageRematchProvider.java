package org.dromara.djs.plant.crop.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.image.api.ImageRematchProvider;
import org.dromara.djs.common.image.service.IImageLibraryService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 作物图片重新匹配 provider（IMG-LIB-001）。
 *
 * <p>对所有 {@code image_source=0}（自动匹配）的作物按 cropName 重跑
 * {@link IImageLibraryService#match}，把结果写回 {@code image_oss_id}（含写成 null —— 图库删图后回退兜底）。
 * {@code image_source=1}（用户手改）的行不动。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CropImageRematchProvider implements ImageRematchProvider {

    private final CropInfoMapper cropInfoMapper;
    private final IImageLibraryService imageLibraryService;

    @Override
    public int rematchAll() {
        List<CropInfo> crops = cropInfoMapper.selectList(
            new LambdaQueryWrapper<CropInfo>().eq(CropInfo::getImageSource, 0));
        int updated = 0;
        for (CropInfo crop : crops) {
            String matched = imageLibraryService.match(crop.getCropName());
            // 仅当匹配结果与当前值不同才更新（含 null↔有值 双向）
            if (!Objects.equals(StrUtil.emptyToNull(matched), StrUtil.emptyToNull(crop.getImageOssId()))) {
                cropInfoMapper.update(null, Wrappers.<CropInfo>update()
                    .eq("id", crop.getId())
                    .set("image_oss_id", StrUtil.emptyToNull(matched)));
                updated++;
            }
        }
        log.info("IMG-LIB-001 作物图重新匹配：扫描 {} 行，更新 {} 行", crops.size(), updated);
        return updated;
    }

    @Override
    public String domainName() {
        return "crop";
    }

}
