package org.dromara.djs.common.store.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.dto.UserDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.UserService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.store.context.StoreContext;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.domain.StoreUserRelation;
import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.common.store.domain.vo.StoreUserVo;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.store.mapper.StoreUserRelationMapper;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 门店-人员关联 Service 实现（STORE-PERM-001 / STR-USER-REL-001）。
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}（wrapper-only update + del_unique=id）。
 * 绑定幂等：已有有效绑定跳过、曾软删的复活（del_flag 回 '0' + del_unique 回 0）、其余新增。</p>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@Slf4j
@Service
public class StoreUserRelationServiceImpl
    extends DjsBaseServiceImpl<StoreUserRelationMapper, StoreUserRelation>
    implements IStoreUserRelationService {

    private final UserService userService;
    private final StoreMapper storeMapper;

    /**
     * 门店墙开关（STORE-PERM-001 员工↔门店绑定过滤）。V1 默认 false = 关墙，
     * my-stores 返全部门店（ADR-0018 门店不做隔离、全员可跨店）；V2 多加盟商时置 true 按绑定过滤。
     */
    @Value("${djs.store.wall-enabled:false}")
    private boolean storeWallEnabled;

    public StoreUserRelationServiceImpl(StoreUserRelationMapper baseMapper,
                                        UserService userService,
                                        StoreMapper storeMapper) {
        super(baseMapper);
        this.userService = userService;
        this.storeMapper = storeMapper;
    }

    @Override
    public List<StoreUserVo> listUsersByStore(Long storeId) {
        if (storeId == null) {
            return Collections.emptyList();
        }
        List<StoreUserRelation> relations = baseMapper.selectList(
            new LambdaQueryWrapper<StoreUserRelation>()
                .eq(StoreUserRelation::getStoreId, storeId)
                .orderByDesc(StoreUserRelation::getId));
        if (CollUtil.isEmpty(relations)) {
            return Collections.emptyList();
        }
        List<Long> userIds = relations.stream().map(StoreUserRelation::getUserId).distinct().toList();
        Map<Long, UserDTO> userMap = userService.selectListByIds(userIds).stream()
            .collect(Collectors.toMap(UserDTO::getUserId, Function.identity(), (a, b) -> a));
        List<StoreUserVo> result = new ArrayList<>(relations.size());
        for (StoreUserRelation rel : relations) {
            UserDTO user = userMap.get(rel.getUserId());
            if (user == null) {
                // 人员已删除 / 不存在 → 跳过展示（绑定行保留，但右列表不显幽灵人）
                continue;
            }
            StoreUserVo vo = new StoreUserVo();
            vo.setUserId(user.getUserId());
            vo.setUserName(user.getUserName());
            vo.setNickName(user.getNickName());
            vo.setPhonenumber(user.getPhonenumber());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bindUsers(Long storeId, List<Long> userIds) {
        if (storeId == null) {
            throw new ServiceException("门店 ID 不能为空");
        }
        if (CollUtil.isEmpty(userIds)) {
            return 0;
        }
        Set<Long> distinctUserIds = new HashSet<>(userIds);

        // 已有有效绑定 → 跳过
        Set<Long> activeUserIds = baseMapper.selectList(
                new LambdaQueryWrapper<StoreUserRelation>()
                    .eq(StoreUserRelation::getStoreId, storeId)
                    .in(StoreUserRelation::getUserId, distinctUserIds))
            .stream().map(StoreUserRelation::getUserId).collect(Collectors.toSet());

        Long operator = currentUserIdSafe();
        Date now = new Date();
        int affected = 0;
        for (Long userId : distinctUserIds) {
            if (userId == null || activeUserIds.contains(userId)) {
                continue;
            }
            // 尝试复活曾软删的同 (store,user) 行（@TableLogic 默认查询已自动过滤软删行，
            // 此处用 wrapper update 直接命中物理软删行，绕过逻辑删过滤）
            UpdateWrapper<StoreUserRelation> reviveWrapper = Wrappers.<StoreUserRelation>update()
                .eq("store_id", storeId)
                .eq("user_id", userId)
                .eq("del_flag", "1")
                .set("del_flag", "0")
                .set("del_unique", 0L)
                .set("update_by", operator)
                .set("update_time", now);
            int revived = baseMapper.update(null, reviveWrapper);
            if (revived > 0) {
                affected += revived;
                continue;
            }
            // 无可复活 → 新增（tenant_id + del_unique=0 + 审计走 MetaObjectHandler / 默认值）
            StoreUserRelation rel = new StoreUserRelation();
            rel.setStoreId(storeId);
            rel.setUserId(userId);
            baseMapper.insert(rel);
            affected++;
        }
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unbind(Long storeId, Long userId) {
        if (storeId == null || userId == null) {
            throw new ServiceException("门店 ID / 人员 ID 不能为空");
        }
        StoreUserRelation rel = baseMapper.selectOne(
            new LambdaQueryWrapper<StoreUserRelation>()
                .eq(StoreUserRelation::getStoreId, storeId)
                .eq(StoreUserRelation::getUserId, userId)
                .last("LIMIT 1"));
        if (rel == null) {
            // 已无有效绑定 → 幂等返 0
            return 0;
        }
        // 走基类软删（set del_flag='1' + del_unique=id + 审计）
        return softDelete(Collections.singletonList(rel.getId()));
    }

    @Override
    public List<Long> listStoreIdsByUser(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return baseMapper.selectList(
                new LambdaQueryWrapper<StoreUserRelation>()
                    .select(StoreUserRelation::getStoreId)
                    .eq(StoreUserRelation::getUserId, userId))
            .stream().map(StoreUserRelation::getStoreId).distinct().toList();
    }

    @Override
    public List<StorePickerVo> listMyStores() {
        // 门店墙关闭（V1 默认）→ 全员返全部门店（ADR-0018 全员可跨店）；超管/租管始终 bypass。
        boolean ignoreWall;
        try {
            ignoreWall = !storeWallEnabled || LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin();
        } catch (Exception e) {
            ignoreWall = !storeWallEnabled;
        }
        List<Store> stores;
        if (ignoreWall) {
            stores = storeMapper.selectList(new LambdaQueryWrapper<Store>().orderByAsc(Store::getId));
        } else {
            Long userId = currentUserIdSafe();
            List<Long> storeIds = listStoreIdsByUser(userId);
            if (CollUtil.isEmpty(storeIds)) {
                return Collections.emptyList();
            }
            stores = storeMapper.selectList(
                new LambdaQueryWrapper<Store>()
                    .in(Store::getId, storeIds)
                    .orderByAsc(Store::getId));
        }
        return stores.stream().map(s -> {
            StorePickerVo vo = new StorePickerVo();
            vo.setId(s.getId());
            vo.setStoreName(s.getStoreName());
            vo.setStoreCode(s.getStoreCode());
            return vo;
        }).toList();
    }

    @Override
    public boolean isStoreAccessible(Long userId, Long storeId) {
        if (storeId == null) {
            return false;
        }
        // V1 门店墙关闭（ADR-0018 全员可跨店）→ 任一门店都可访问；开墙则按员工↔门店绑定校验。
        if (!storeWallEnabled) {
            return true;
        }
        return listStoreIdsByUser(userId).contains(storeId);
    }

    @Override
    public int countUsersByStore(Long storeId) {
        if (storeId == null) {
            return 0;
        }
        Long count = baseMapper.selectCount(
            new LambdaQueryWrapper<StoreUserRelation>()
                .eq(StoreUserRelation::getStoreId, storeId));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public Map<Long, Integer> countUsersByStores(List<Long> storeIds) {
        if (CollUtil.isEmpty(storeIds)) {
            return Collections.emptyMap();
        }
        // 一次 GROUP BY 取各门店人数（@TableLogic 自动附加 del_flag='0'，多租户拦截器附加 tenant_id）
        List<Map<String, Object>> rows = baseMapper.selectMaps(
            new QueryWrapper<StoreUserRelation>()
                .select("store_id AS storeId", "COUNT(*) AS cnt")
                .in("store_id", storeIds)
                .groupBy("store_id"));
        Map<Long, Integer> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object sid = row.get("storeId");
            Object cnt = row.get("cnt");
            if (sid != null && cnt != null) {
                result.put(Long.valueOf(sid.toString()), Integer.valueOf(cnt.toString()));
            }
        }
        return result;
    }
}
