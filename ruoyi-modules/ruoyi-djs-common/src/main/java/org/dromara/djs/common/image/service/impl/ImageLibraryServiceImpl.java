package org.dromara.djs.common.image.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.image.domain.ImageLibrary;
import org.dromara.djs.common.image.domain.bo.ImageLibraryBo;
import org.dromara.djs.common.image.domain.query.ImageLibraryQuery;
import org.dromara.djs.common.image.domain.vo.ImageLibraryVo;
import org.dromara.djs.common.image.mapper.ImageLibraryMapper;
import org.dromara.djs.common.image.service.IImageLibraryService;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公共图片库 Service 实现（IMG-LIB-001）。
 *
 * <p>{@link #match} 走内存缓存 {@code nameToOssId}（精确主名 + 别名展开）。
 * CRUD 后 {@link #reload} 重建缓存。缓存行数小（图库主数据），不上 Redisson 保持简单。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Slf4j
@Service
public class ImageLibraryServiceImpl
    extends DjsBaseServiceImpl<ImageLibraryMapper, ImageLibrary>
    implements IImageLibraryService {

    /**
     * 状态：正常（参与匹配）。
     */
    private static final String STATUS_NORMAL = "0";

    private final ImageUrlResolver imageUrlResolver;

    /**
     * name（主名 + 别名，全部 trim）→ ossId 匹配缓存。
     */
    private final Map<String, String> nameToOssId = new ConcurrentHashMap<>();

    public ImageLibraryServiceImpl(ImageLibraryMapper baseMapper, ImageUrlResolver imageUrlResolver) {
        super(baseMapper);
        this.imageUrlResolver = imageUrlResolver;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @Override
    public String match(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        return nameToOssId.get(name.trim());
    }

    @Override
    public void reload() {
        Map<String, String> fresh = new ConcurrentHashMap<>();
        try {
            List<ImageLibrary> all = baseMapper.selectList(
                new LambdaQueryWrapper<ImageLibrary>().eq(ImageLibrary::getStatus, STATUS_NORMAL));
            for (ImageLibrary img : all) {
                if (StrUtil.isBlank(img.getOssId())) {
                    continue;
                }
                // 精确主名（主名优先，别名不覆盖已有主名 → putIfAbsent）
                if (StrUtil.isNotBlank(img.getImageName())) {
                    fresh.put(img.getImageName().trim(), img.getOssId());
                }
            }
            // 别名第二轮（不覆盖主名）
            for (ImageLibrary img : all) {
                if (StrUtil.isBlank(img.getOssId()) || StrUtil.isBlank(img.getAliases())) {
                    continue;
                }
                for (String alias : img.getAliases().split(",")) {
                    String key = alias.trim();
                    if (StrUtil.isNotBlank(key)) {
                        fresh.putIfAbsent(key, img.getOssId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("图库匹配缓存重建失败（保持旧缓存）: {}", e.getMessage());
            return;
        }
        nameToOssId.clear();
        nameToOssId.putAll(fresh);
        log.info("图库匹配缓存重建完成，条目数={}", nameToOssId.size());
    }

    @Override
    public TableDataInfo<ImageLibraryVo> queryPageList(ImageLibraryQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<ImageLibrary> wrapper = buildQueryWrapper(query);
        Page<ImageLibraryVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillImageUrls(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<ImageLibraryVo> queryList(ImageLibraryQuery query) {
        List<ImageLibraryVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillImageUrls(list);
        return list;
    }

    @Override
    public ImageLibraryVo queryById(Long id) {
        ImageLibraryVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillImageUrls(List.of(vo));
        }
        return vo;
    }

    @Override
    public int insertByBo(ImageLibraryBo bo) {
        ImageLibrary entity = MapstructUtils.convert(bo, ImageLibrary.class);
        if (entity == null) {
            throw new ServiceException("图库入参转换失败");
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_NORMAL);
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        int rows = baseMapper.insert(entity);
        reload();
        return rows;
    }

    @Override
    public int updateByBo(ImageLibraryBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("图库 ID 不能为空");
        }
        ImageLibrary exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("图库记录不存在或已删除：" + bo.getId());
        }
        ImageLibrary entity = MapstructUtils.convert(bo, ImageLibrary.class);
        if (entity == null) {
            throw new ServiceException("图库入参转换失败");
        }
        int rows = baseMapper.updateById(entity);
        reload();
        return rows;
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        int rows = softDelete(ids);
        reload();
        return rows;
    }

    /**
     * 批量回填 imageUrl（resolver，禁 N+1）。
     */
    private void fillImageUrls(List<ImageLibraryVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> ossIds = new ArrayList<>(records.size());
        for (ImageLibraryVo vo : records) {
            if (StrUtil.isNotBlank(vo.getOssId())) {
                ossIds.add(vo.getOssId());
            }
        }
        Map<String, String> urlMap = imageUrlResolver.batchUrl(ossIds);
        for (ImageLibraryVo vo : records) {
            if (StrUtil.isNotBlank(vo.getOssId())) {
                vo.setImageUrl(urlMap.get(vo.getOssId()));
            }
        }
    }

    private LambdaQueryWrapper<ImageLibrary> buildQueryWrapper(ImageLibraryQuery query) {
        LambdaQueryWrapper<ImageLibrary> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByAsc(ImageLibrary::getSortOrder).orderByDesc(ImageLibrary::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getImageName()), ImageLibrary::getImageName, query.getImageName())
            .like(StringUtils.isNotBlank(query.getAliases()), ImageLibrary::getAliases, query.getAliases())
            .eq(StringUtils.isNotBlank(query.getStatus()), ImageLibrary::getStatus, query.getStatus())
            .orderByAsc(ImageLibrary::getSortOrder)
            .orderByDesc(ImageLibrary::getId);
        return wrapper;
    }

}
