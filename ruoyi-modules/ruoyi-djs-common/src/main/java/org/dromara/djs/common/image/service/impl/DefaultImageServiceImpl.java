package org.dromara.djs.common.image.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.image.domain.DefaultImage;
import org.dromara.djs.common.image.domain.bo.DefaultImageBo;
import org.dromara.djs.common.image.domain.vo.DefaultImageVo;
import org.dromara.djs.common.image.mapper.DefaultImageMapper;
import org.dromara.djs.common.image.service.IDefaultImageService;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分类默认图 Service 实现（IMG-LIB-001）。
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultImageServiceImpl implements IDefaultImageService {

    private final DefaultImageMapper defaultImageMapper;
    private final ImageUrlResolver imageUrlResolver;

    @Override
    public List<DefaultImageVo> queryAll() {
        List<DefaultImageVo> list = defaultImageMapper.selectVoList(
            new LambdaQueryWrapper<DefaultImage>().orderByAsc(DefaultImage::getId));
        // 批量回填 imageUrl（禁 N+1）
        List<String> ossIds = new ArrayList<>();
        for (DefaultImageVo vo : list) {
            if (StrUtil.isNotBlank(vo.getOssId())) {
                ossIds.add(vo.getOssId());
            }
        }
        Map<String, String> urlMap = imageUrlResolver.batchUrl(ossIds);
        for (DefaultImageVo vo : list) {
            if (StrUtil.isNotBlank(vo.getOssId())) {
                vo.setImageUrl(urlMap.get(vo.getOssId()));
            }
        }
        return list;
    }

    @Override
    public int updateByBo(DefaultImageBo bo) {
        DefaultImage exists = defaultImageMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("默认图配置不存在：" + bo.getId());
        }
        // categoryKey / is_global 不允许编辑端点修改，只更新 ossId
        DefaultImage entity = new DefaultImage();
        entity.setId(bo.getId());
        // ossId 允许清空（移除默认图）→ 用 UpdateWrapper 显式 set 才能写 null
        return defaultImageMapper.update(null,
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<DefaultImage>update()
                .eq("id", bo.getId())
                .set("oss_id", StrUtil.blankToDefault(bo.getOssId(), null)));
    }

}
