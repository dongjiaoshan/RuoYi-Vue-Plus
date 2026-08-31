package org.dromara.djs.warehouse.trace.service;

import org.dromara.djs.warehouse.trace.domain.bo.TracePageConfigImageBo;
import org.dromara.djs.warehouse.trace.domain.vo.TracePageConfigVo;

import java.util.List;

/**
 * 追溯码页面配置 Service（V6-R146「追溯码配置管理」）。
 *
 * @author djs
 * @since V6-R146
 */
public interface ITracePageConfigService {

    /**
     * 全量列表（固定两行：猪肉 / 果蔬，按 id 升序，猪肉在前）。
     *
     * <p>甲方明确不要搜索条件，也就没有分页：两行配置项一次全返。</p>
     *
     * @return 配置行（图片 URL 已批量解析回填）
     */
    List<TracePageConfigVo> queryList();

    /**
     * 单行详情（上传弹窗打开时取最新值回填 OssUpload）。
     *
     * @param id 配置行主键
     * @return 配置行；id 不存在返 null
     */
    TracePageConfigVo getVoById(Long id);

    /**
     * 保存基地介绍页图片（换图 / 清空）。
     *
     * @param bo 入参（ossId 为空 = 清空配置，H5 回落内置版式）
     * @return 受影响行数
     */
    int updateImage(TracePageConfigImageBo bo);
}
