package org.dromara.djs.common.image.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.image.domain.bo.ImageBatchItemBo;
import org.dromara.djs.common.image.domain.bo.ImageLibraryBo;
import org.dromara.djs.common.image.domain.query.ImageLibraryQuery;
import org.dromara.djs.common.image.domain.vo.ImageBatchImportVo;
import org.dromara.djs.common.image.domain.vo.ImageLibraryVo;

import java.util.Collection;
import java.util.List;

/**
 * 公共图片库 Service（IMG-LIB-001）。
 *
 * <p>核心能力 {@link #match}：作物 / 商品主数据 create 时按 name 自动匹配出 ossId（write-time 存字段）。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
public interface IImageLibraryService {

    /**
     * 按名称匹配图库，返回 ossId（匹配不上返 null）。
     *
     * <p>匹配顺序：精确主名 → 别名 contains → null。结果用内存缓存 Map（CRUD 后 {@link #reload}）。</p>
     *
     * @param name 作物 cropName / 商品 productName
     * @return ossId 或 null
     */
    String match(String name);

    /**
     * 重新加载匹配缓存（图库 add / edit / remove 后调用）。
     */
    void reload();

    TableDataInfo<ImageLibraryVo> queryPageList(ImageLibraryQuery query, PageQuery pageQuery);

    List<ImageLibraryVo> queryList(ImageLibraryQuery query);

    ImageLibraryVo queryById(Long id);

    int insertByBo(ImageLibraryBo bo);

    int updateByBo(ImageLibraryBo bo);

    int deleteByIds(Collection<Long> ids);

    /**
     * 批量导入 + 按文件名自动归档（IMG-LIB-001 批量上传）。
     *
     * <p>逐项按 {@code imageName} upsert：已存在则更新 ossId（重传即替换，不重复建），
     * 不存在则 insert（aliases 留空 / sort=0 / status 启用）。tenant_id 走自动填充不显式赋。
     * 全部 upsert 后 {@link #reload} 刷缓存并触发所有 {@code ImageRematchProvider} 重新匹配，
     * 让存量作物 / 商品立刻按名回填图。</p>
     *
     * @param items 导入项（imageName + ossId）
     * @return 导入结果（新建 / 更新 / 各域重新匹配条数）
     */
    ImageBatchImportVo batchImport(List<ImageBatchItemBo> items);

}
