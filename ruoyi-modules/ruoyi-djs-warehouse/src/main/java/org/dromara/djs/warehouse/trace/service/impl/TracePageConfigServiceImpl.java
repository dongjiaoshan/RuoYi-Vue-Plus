package org.dromara.djs.warehouse.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.warehouse.trace.domain.TracePageConfig;
import org.dromara.djs.warehouse.trace.domain.bo.TracePageConfigImageBo;
import org.dromara.djs.warehouse.trace.domain.vo.TracePageConfigVo;
import org.dromara.djs.warehouse.trace.mapper.TracePageConfigMapper;
import org.dromara.djs.warehouse.trace.service.ITracePageConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 追溯码页面配置 Service 实现（V6-R146）。
 *
 * <p>只有「读列表 / 读单条 / 换图」三件事：两行配置由 Flyway 预置，没有新增与删除路径，
 * 因此不继承 {@code softDelete}，实体也不声明 {@code delUnique}。</p>
 *
 * @author djs
 * @since V6-R146
 */
@Service
public class TracePageConfigServiceImpl
    extends DjsBaseServiceImpl<TracePageConfigMapper, TracePageConfig>
    implements ITracePageConfigService {

    private final ImageUrlResolver imageUrlResolver;

    public TracePageConfigServiceImpl(TracePageConfigMapper baseMapper, ImageUrlResolver imageUrlResolver) {
        super(baseMapper);
        this.imageUrlResolver = imageUrlResolver;
    }

    @Override
    public List<TracePageConfigVo> queryList() {
        List<TracePageConfigVo> list = baseMapper.selectVoList(
            new LambdaQueryWrapper<TracePageConfig>().orderByAsc(TracePageConfig::getId));
        fillImageUrl(list);
        return list;
    }

    @Override
    public TracePageConfigVo getVoById(Long id) {
        if (id == null) {
            return null;
        }
        TracePageConfigVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            return null;
        }
        fillImageUrl(List.of(vo));
        return vo;
    }

    @Override
    public int updateImage(TracePageConfigImageBo bo) {
        TracePageConfig entity = baseMapper.selectById(bo.getId());
        if (entity == null) {
            throw new ServiceException("追溯码配置不存在");
        }
        String ossId = bo.getBaseIntroImageOssId();
        // 空串 / 纯空白归一成 null：DB 里「没配图」只有 NULL 一种表示，公开端与 H5 才好统一判空
        String normalized = StringUtils.isBlank(ossId) ? null : ossId.trim();
        // 必须走 wrapper-only update（entity=null）而不是 updateById(entity)：
        // MP 全局 updateStrategy=NOT_NULL 会把值为 null 的列整条从 SET 子句剥掉，
        // 于是「删图 → 确定」在 entity 路径下只刷 update_by/update_time、图片列纹丝不动 —— 撤回路径静默失效。
        // 代价：wrapper-only 不触发 MetaObjectHandler.updateFill，审计两列在此显式 set（同 DjsBaseServiceImpl.softDelete 范式）。
        LambdaUpdateWrapper<TracePageConfig> wrapper = Wrappers.<TracePageConfig>lambdaUpdate()
            .eq(TracePageConfig::getId, entity.getId())
            .set(TracePageConfig::getBaseIntroImageOssId, normalized)
            .set(TracePageConfig::getUpdateBy, currentUserIdSafe())
            .set(TracePageConfig::getUpdateTime, new Date());
        return baseMapper.update(null, wrapper);
    }

    /**
     * 批量回填图片 URL（一次查 sys_oss，禁 N+1）。
     */
    private void fillImageUrl(List<TracePageConfigVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<ImageUrlResolver.Item> items = new ArrayList<>(list.size());
        for (TracePageConfigVo vo : list) {
            items.add(new ImageUrlResolver.Item(vo.getBaseIntroImageOssId(), null));
        }
        List<String> urls = imageUrlResolver.resolveList(items);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setBaseIntroImageUrl(urls.get(i));
        }
    }
}
