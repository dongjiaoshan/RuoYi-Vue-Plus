package org.dromara.djs.common.image.service;

import org.dromara.djs.common.image.domain.bo.DefaultImageBo;
import org.dromara.djs.common.image.domain.vo.DefaultImageVo;

import java.util.List;

/**
 * 分类默认图 Service（IMG-LIB-001）。
 *
 * @author djs
 * @since IMG-LIB-001
 */
public interface IDefaultImageService {

    /**
     * 查全部分类默认图（7 行：6 belong_type + global），带 imageUrl 回填。
     */
    List<DefaultImageVo> queryAll();

    /**
     * 编辑某分类默认图的 ossId（categoryKey 不可改）。
     */
    int updateByBo(DefaultImageBo bo);

}
