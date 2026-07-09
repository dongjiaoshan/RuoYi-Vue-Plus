package org.dromara.djs.common.store.service;

import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.common.store.domain.vo.StoreUserVo;

import java.util.List;
import java.util.Map;

/**
 * 门店-人员关联 Service（STORE-PERM-001 / STR-USER-REL-001）。
 *
 * @author djs
 * @since STORE-PERM-001
 */
public interface IStoreUserRelationService {

    /**
     * 查门店已绑人员列表（关联 sys_user 取账号 / 昵称 / 电话）。
     *
     * @param storeId 门店 ID
     * @return 已绑人员 VO 列表
     */
    List<StoreUserVo> listUsersByStore(Long storeId);

    /**
     * 绑定人员到门店（幂等）：已存在有效绑定的跳过；曾软删的复活（恢复 del_flag='0'）；其余新增。
     *
     * @param storeId 门店 ID
     * @param userIds 人员 ID 集合
     * @return 受影响（新增 + 复活）的人员数
     */
    int bindUsers(Long storeId, List<Long> userIds);

    /**
     * 解绑（软删）门店-人员关联。数据不删，仅 del_flag='1' + del_unique=id；
     * 该员工视角下从此不可见此门店及历史单据，管理员 / {@code StoreContext.ignore} 仍可查。
     *
     * @param storeId 门店 ID
     * @param userId  人员 ID
     * @return 受影响行数
     */
    int unbind(Long storeId, Long userId);

    /**
     * 查某人有权限的门店 ID 列表（仅有效绑定 del_flag='0'）。驱动全局门店选择器数据源。
     *
     * @param userId 人员 ID
     * @return 门店 ID 列表
     */
    List<Long> listStoreIdsByUser(Long userId);

    /**
     * 查当前登录人有权限的门店精简列表（{@link #listStoreIdsByUser} 反查 t_md_store）。
     * 超管 / 租管返全部门店。
     *
     * @return 门店精简 VO 列表
     */
    List<StorePickerVo> listMyStores();

    /**
     * 判断某人是否可操作某门店（门店墙 djs.store.wall-enabled 感知）。
     * V1 关墙 → 任一门店可访问（ADR-0018 全员可跨店）；开墙 → 按 {@link #listStoreIdsByUser} 绑定校验。
     *
     * @param userId  人员 ID
     * @param storeId 门店 ID
     * @return 是否可访问
     */
    boolean isStoreAccessible(Long userId, Long storeId);

    /**
     * 统计门店已绑有效人员数（用于 t_md_store 列表「员工数」列，防 N+1 批量场景见实现）。
     *
     * @param storeId 门店 ID
     * @return 人数
     */
    int countUsersByStore(Long storeId);

    /**
     * 批量统计多门店已绑有效人员数（一次 GROUP BY，防分页 N+1）。
     *
     * @param storeIds 门店 ID 集合
     * @return storeId → 人数（无绑定的门店不在 map 中，调用方按 0 兜底）
     */
    Map<Long, Integer> countUsersByStores(List<Long> storeIds);
}
