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

    /**
     * 生成门店打包生产编码：{@code <生产标识码>YYMMDD####}（每个门店当日各自从 0001 起）。
     *
     * <p>规则 {@link org.dromara.djs.common.encoder.BizCodeType#STORE_PRODUCE_NO}，prefix = 门店
     * {@code production_mark_code}。门店未设置生产标识码 → 抛业务异常（前端提示先在门店管理配置）。</p>
     *
     * @param storeId 门店 ID（不能为空）
     * @return 门店打包生产编码 {@code <生产标识码>YYMMDD####}
     */
    String generateStoreProduceCode(Long storeId);

    /**
     * 断言门店处于「合作中」状态，否则拒绝业务操作。
     *
     * <p>门店合作状态字典 {@code djs_store_status}：{@code '0'}=合作中 / {@code '1'}=已终止 / {@code '2'}=装修中。
     * 已终止（{@code '1'}）门店禁止一切业务写操作（需求下单 / 盘点 / 退回 / 拆单 / 销售录入等），
     * 在各 store 业务 service 写入口处调用本方法拦截。</p>
     *
     * <p>{@code storeId} 为空时直接放行（部分业务方向门店字段可空，非「已终止门店」场景，交由各自的存在性校验处理）。</p>
     *
     * @param storeId 门店 ID（可空 → 放行）
     * @throws ServiceException 门店不存在（404）或已终止合作（409）
     */
    void assertStoreActive(Long storeId);

}
