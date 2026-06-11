package org.dromara.djs.plant.crop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import cn.hutool.core.util.StrUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.image.service.IImageLibraryService;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.domain.bo.CropInfoBo;
import org.dromara.djs.plant.crop.domain.query.CropInfoQuery;
import org.dromara.djs.plant.crop.domain.vo.CropInfoVo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.crop.service.ICropInfoService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 作物 Service 实现（PLT-MD-001）。
 *
 * <p>删除前校验：D8 阶段 stub。t_plant_plant_plan / t_plant_pick_activity 在 D9+ 落地后启用。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Slf4j
@Service
public class CropInfoServiceImpl extends DjsBaseServiceImpl<CropInfoMapper, CropInfo> implements ICropInfoService {

    /**
     * 作物图 L2 兜底分类键（作物无 belong_type，统一走"蔬菜默认图"）。
     */
    private static final String CROP_BELONG_TYPE = "vegetable";

    private final IImageLibraryService imageLibraryService;
    private final ImageUrlResolver imageUrlResolver;

    public CropInfoServiceImpl(CropInfoMapper baseMapper,
                               IImageLibraryService imageLibraryService,
                               ImageUrlResolver imageUrlResolver) {
        super(baseMapper);
        this.imageLibraryService = imageLibraryService;
        this.imageUrlResolver = imageUrlResolver;
    }

    @Override
    public TableDataInfo<CropInfoVo> queryPageList(CropInfoQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<CropInfo> wrapper = buildQueryWrapper(query);
        Page<CropInfoVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillImageUrls(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<CropInfoVo> queryList(CropInfoQuery query) {
        List<CropInfoVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillImageUrls(list);
        return list;
    }

    @Override
    public CropInfoVo queryById(Long id) {
        CropInfoVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillImageUrls(List.of(vo));
        }
        return vo;
    }

    @Override
    public int insertByBo(CropInfoBo bo) {
        CropInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("作物入参转换失败");
        }
        // IMG-LIB-001：image_oss_id 空且非手动 → 按 cropName 自动匹配图库（匹配不上留 null，走 resolver 兜底）
        boolean manual = entity.getImageSource() != null && entity.getImageSource() == 1;
        if (StrUtil.isBlank(entity.getImageOssId()) && !manual) {
            String matched = imageLibraryService.match(entity.getCropName());
            entity.setImageOssId(matched);
            entity.setImageSource(0);
        } else if (StrUtil.isNotBlank(entity.getImageOssId())) {
            entity.setImageSource(1);
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(CropInfoBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("作物 ID 不能为空");
        }
        CropInfo exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("作物不存在或已删除：" + bo.getId());
        }
        CropInfo entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("作物入参转换失败");
        }
        // crop_code 不允许修改
        entity.setCropCode(exists.getCropCode());
        // IMG-LIB-001：用户在编辑里改了图 → 标记手动（image_source=1），后续 rematch 不覆盖
        if (StrUtil.isNotBlank(entity.getImageOssId())
            && !entity.getImageOssId().equals(exists.getImageOssId())) {
            entity.setImageSource(1);
        }
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // D8 阶段 stub：t_plant_plant_plan / t_plant_pick_activity / t_plant_plant_details 0 行，
        // D9+ PLT-PLAN-001 / PLT-PICK-001 落地后启用：
        //   long active = planMapper.selectCount(... crop_id IN(ids) AND del_flag=0) + ...
        //   if (active > 0) throw new ServiceException("plant.crop.has_business_data");
        return softDelete(ids);
    }

    protected CropInfo toEntity(CropInfoBo bo) {
        return MapstructUtils.convert(bo, CropInfo.class);
    }

    /**
     * 批量回填作物 imageUrl（IMG-LIB-001 4 层 resolver，禁 N+1）。作物 L2 兜底统一走 vegetable 默认图。
     */
    private void fillImageUrls(List<CropInfoVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<ImageUrlResolver.Item> items = new ArrayList<>(records.size());
        for (CropInfoVo vo : records) {
            items.add(new ImageUrlResolver.Item(vo.getImageOssId(), CROP_BELONG_TYPE));
        }
        List<String> urls = imageUrlResolver.resolveList(items);
        if (urls.size() != records.size()) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setImageUrl(urls.get(i));
        }
    }

    private LambdaQueryWrapper<CropInfo> buildQueryWrapper(CropInfoQuery query) {
        LambdaQueryWrapper<CropInfo> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(CropInfo::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getCropCode()), CropInfo::getCropCode, query.getCropCode())
            .like(StringUtils.isNotBlank(query.getCropName()), CropInfo::getCropName, query.getCropName())
            .like(StringUtils.isNotBlank(query.getVarietyName()), CropInfo::getVarietyName, query.getVarietyName())
            .eq(StringUtils.isNotBlank(query.getCropFamily()), CropInfo::getCropFamily, query.getCropFamily())
            .like(StringUtils.isNotBlank(query.getPlantingSeason()), CropInfo::getPlantingSeason, query.getPlantingSeason())
            .orderByDesc(CropInfo::getId);
        return wrapper;
    }
}
