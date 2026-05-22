package org.dromara.djs.common.store.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.store.domain.bo.StoreBo;
import org.dromara.djs.common.store.domain.query.StoreQuery;
import org.dromara.djs.common.store.domain.vo.StoreVo;

import java.util.Collection;
import java.util.List;

/**
 * 门店主数据 Service（SYS-MD-002 + SYS-MD-FIX-002）。
 *
 * @author djs
 * @since SYS-MD-002
 */
public interface IStoreService {

    /**
     * 分页查询门店列表。
     */
    TableDataInfo<StoreVo> queryPageList(StoreQuery query, PageQuery pageQuery);

    /**
     * 查询门店列表（不分页，给导出用）。
     */
    List<StoreVo> queryList(StoreQuery query);

    /**
     * 根据 ID 查询单条。
     */
    StoreVo queryById(Long id);

    /**
     * 新增。
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(StoreBo bo);

    /**
     * 修改（不允许改 storeCode / managerUserId；后者走 {@link #setManager}）。
     *
     * @return 受影响行数（成功 1）
     */
    int updateByBo(StoreBo bo);

    /**
     * 设置店长。校验 sys_user 存在 + 仅更新 manager_user_id 列（不动其他字段）。
     *
     * @param storeId 门店 ID
     * @param userId  sys_user.user_id（null 表示清空）
     * @return 受影响行数
     */
    int setManager(Long storeId, Long userId);

    /**
     * 软删除（支持批量）。
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
