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
import org.dromara.djs.common.image.api.ImageRematchProvider;
import org.dromara.djs.common.image.domain.ImageLibrary;
import org.dromara.djs.common.image.domain.bo.ImageBatchItemBo;
import org.dromara.djs.common.image.domain.bo.ImageLibraryBo;
import org.dromara.djs.common.image.domain.query.ImageLibraryQuery;
import org.dromara.djs.common.image.domain.vo.ImageBatchImportVo;
import org.dromara.djs.common.image.domain.vo.ImageLibraryVo;
import org.dromara.djs.common.image.mapper.ImageLibraryMapper;
import org.dromara.djs.common.image.service.IImageLibraryService;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * 主数据图片重新匹配 provider 集合（plant / warehouse 各注册一个，零循环依赖）。
     * 上游模块未上线时集合为空。
     */
    private final ObjectProvider<ImageRematchProvider> rematchProviders;

    /**
     * name（主名 + 别名，全部 trim）→ ossId 匹配缓存。
     */
    private final Map<String, String> nameToOssId = new ConcurrentHashMap<>();

    public ImageLibraryServiceImpl(ImageLibraryMapper baseMapper,
                                   ImageUrlResolver imageUrlResolver,
                                   ObjectProvider<ImageRematchProvider> rematchProviders) {
        super(baseMapper);
        this.imageUrlResolver = imageUrlResolver;
        this.rematchProviders = rematchProviders;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageBatchImportVo batchImport(List<ImageBatchItemBo> items) {
        ImageBatchImportVo result = new ImageBatchImportVo();
        if (items == null || items.isEmpty()) {
            return result;
        }
        int imported = 0;
        int updated = 0;
        for (ImageBatchItemBo item : items) {
            if (item == null || StrUtil.isBlank(item.getImageName()) || StrUtil.isBlank(item.getOssId())) {
                continue;
            }
            String name = item.getImageName().trim();
            String ossId = item.getOssId().trim();
            // 按 image_name upsert（软删行被 @TableLogic 自动过滤；UNIQUE 含 tenant_id 由拦截器保证）
            ImageLibrary exists = baseMapper.selectOne(
                new LambdaQueryWrapper<ImageLibrary>().eq(ImageLibrary::getImageName, name).last("limit 1"));
            if (exists != null) {
                // 重传即替换：只更新 ossId，不动已有 aliases / sort / status / remark
                ImageLibrary patch = new ImageLibrary();
                patch.setId(exists.getId());
                patch.setOssId(ossId);
                baseMapper.updateById(patch);
                updated++;
            } else {
                ImageLibrary entity = new ImageLibrary();
                entity.setImageName(name);
                entity.setOssId(ossId);
                entity.setSortOrder(0);
                entity.setStatus(STATUS_NORMAL);
                // aliases 留空；tenant_id 走 InjectionMetaObjectHandler 自动填充，不显式赋
                baseMapper.insert(entity);
                imported++;
            }
        }
        result.setImported(imported);
        result.setUpdated(updated);
        // 刷匹配缓存 → 触发各域重新匹配回填存量主数据
        reload();
        rematchProviders.forEach(p -> result.getRematched().put(p.domainName(), p.rematchAll()));
        return result;
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
