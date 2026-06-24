package org.dromara.djs.common.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.UserService;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.domain.bo.StoreBo;
import org.dromara.djs.common.store.domain.query.StoreQuery;
import org.dromara.djs.common.store.domain.vo.StoreVo;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.dromara.djs.common.validate.BizReferenceChecker;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 门店主数据 Service 实现（SYS-MD-002 + SYS-MD-FIX-002）。
 *
 * <p>编码策略：{@code storeCode} 新增时由 {@link IBizCodeGenerator} 按
 * {@link BizCodeType#STORE_CODE} 规则生成（pattern {@code ST{seq4}}，例 {@code ST0001}），
 * 编辑端点不允许覆盖。</p>
 *
 * <p>软删通过基类 {@link DjsBaseServiceImpl#softDelete(Collection)}（单参，wrapper-only update）。</p>
 *
 * <p>店长设置（FIX-002）：{@link #updateByBo} 显式忽略 {@code bo.managerUserId}，
 * 必须走独立端点 {@link #setManager}（防越权改 manager 字段）。</p>
 *
 * @author djs
 * @since SYS-MD-002
 */
@Slf4j
@Service
public class StoreServiceImpl extends DjsBaseServiceImpl<StoreMapper, Store> implements IStoreService {

    private final IBizCodeGenerator bizCodeGenerator;
    private final UserService userService;
    private final BizReferenceChecker bizReferenceChecker;
    private final IStoreUserRelationService storeUserRelationService;

    public StoreServiceImpl(StoreMapper baseMapper,
                            IBizCodeGenerator bizCodeGenerator,
                            UserService userService,
                            BizReferenceChecker bizReferenceChecker,
                            IStoreUserRelationService storeUserRelationService) {
        super(baseMapper);
        this.bizCodeGenerator = bizCodeGenerator;
        this.userService = userService;
        this.bizReferenceChecker = bizReferenceChecker;
        this.storeUserRelationService = storeUserRelationService;
    }

    /**
     * 启动时注册门店引用关系给 {@link BizReferenceChecker}，让 {@link #deleteWithValidByIds} 的
     * {@code isReferenced} 反查真正生效（未注册时 isReferenced 恒 false → 软删空校验）。
     *
     * <p>门店被以下三类业务记录引用即不允许软删：门店产品关联（在卖哪些 SKU）/ 门店销售流水 / 门店盘点。
     * 三表均有 {@code store_id} + {@code del_flag}，符合 {@code BizReferenceCheckerImpl} 的反查口径。</p>
     */
    @PostConstruct
    public void registerReferences() {
        bizReferenceChecker.register("t_md_store", "t_store_product_relation", "store_id");
        bizReferenceChecker.register("t_md_store", "t_store_sale_record", "store_id");
        bizReferenceChecker.register("t_md_store", "t_store_check_record", "store_id");
    }

    @Override
    public TableDataInfo<StoreVo> queryPageList(StoreQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Store> wrapper = buildQueryWrapper(query);
        Page<StoreVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillEmployeeCounts(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<StoreVo> queryList(StoreQuery query) {
        List<StoreVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillEmployeeCounts(list);
        return list;
    }

    @Override
    public StoreVo queryById(Long id) {
        StoreVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            vo.setEmployeeCount(storeUserRelationService.countUsersByStore(vo.getId()));
        }
        return vo;
    }

    /**
     * 批量回填员工数（门店-人员绑定有效行数，STORE-PERM-001）。一次 GROUP BY 防分页 N+1，
     * 无绑定的门店按 0 兜底。
     */
    private void fillEmployeeCounts(List<StoreVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> storeIds = list.stream().map(StoreVo::getId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Integer> countMap = storeUserRelationService.countUsersByStores(storeIds);
        for (StoreVo vo : list) {
            vo.setEmployeeCount(countMap.getOrDefault(vo.getId(), 0));
        }
    }

    @Override
    public int insertByBo(StoreBo bo) {
        Store entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("门店入参转换失败");
        }
        entity.setStoreCode(generateStoreCode());
        if (StringUtils.isBlank(entity.getBusinessStatus())) {
            entity.setBusinessStatus("0"); // 默认 合作中
        }
        if (StringUtils.isBlank(entity.getStoreType())) {
            entity.setStoreType("direct");
        }
        // managerUserId 不允许新增端点直接设置（强制走 PUT /manager）
        entity.setManagerUserId(null);
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(StoreBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("门店 ID 不能为空");
        }
        Store exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getId());
        }
        Store entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("门店入参转换失败");
        }
        // store_code 不允许通过编辑端点修改
        entity.setStoreCode(exists.getStoreCode());
        // manager_user_id 不允许通过编辑端点修改，必须走 setManager 端点
        entity.setManagerUserId(exists.getManagerUserId());
        return baseMapper.updateById(entity);
    }

    @Override
    public int setManager(Long storeId, Long userId) {
        if (storeId == null) {
            throw new ServiceException("门店 ID 不能为空");
        }
        Store exists = baseMapper.selectById(storeId);
        if (exists == null) {
            throw new ServiceException("门店不存在或已删除：" + storeId);
        }
        String managerName = null;
        String managerPhone = null;
        if (userId != null) {
            // 校验 sys_user 存在性（已软删 / 不存在 → userName 返 null）
            String userName = userService.selectUserNameById(userId);
            if (StringUtils.isBlank(userName)) {
                throw new ServiceException("店长用户不存在或已停用：" + userId);
            }
            // 把所选人员的姓名/电话快照回写到门店文本列，列表「店长姓名/电话」直接展示
            String nickName = userService.selectNicknameById(userId);
            managerName = StringUtils.isNotBlank(nickName) ? nickName : userName;
            managerPhone = userService.selectPhonenumberById(userId);
        }
        // update manager_user_id + 同步快照 manager_name / manager_phone（清空店长时一并置 NULL）
        // wrapper-only update **不**走 MetaObjectHandler.updateFill —
        // 需显式 set update_by / update_time，与 DjsBaseServiceImpl#softDelete 同范式
        UpdateWrapper<Store> wrapper = new UpdateWrapper<Store>()
            .set("manager_user_id", userId)
            .set("manager_name", managerName)
            .set("manager_phone", managerPhone)
            .set("update_by", currentUserIdSafe())
            .set("update_time", new Date())
            .eq("id", storeId);
        return baseMapper.update(null, wrapper);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus（Spring 注入的 {@code Converter} 单例）。
     */
    protected Store toEntity(StoreBo bo) {
        return MapstructUtils.convert(bo, Store.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        for (Long id : ids) {
            if (bizReferenceChecker.isReferenced("t_md_store", id)) {
                throw new ServiceException("门店被业务记录引用，无法删除：id=" + id);
            }
        }
        return softDelete(ids);
    }

    /**
     * 构造查询条件：storeName like / storeCode eq / storeType eq / managerName like / businessStatus eq。
     */
    private LambdaQueryWrapper<Store> buildQueryWrapper(StoreQuery query) {
        LambdaQueryWrapper<Store> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(Store::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getStoreName()), Store::getStoreName, query.getStoreName())
            .eq(StringUtils.isNotBlank(query.getStoreCode()), Store::getStoreCode, query.getStoreCode())
            .eq(StringUtils.isNotBlank(query.getStoreType()), Store::getStoreType, query.getStoreType())
            .like(StringUtils.isNotBlank(query.getManagerName()), Store::getManagerName, query.getManagerName())
            .eq(StringUtils.isNotBlank(query.getBusinessStatus()), Store::getBusinessStatus, query.getBusinessStatus())
            .ge(Objects.nonNull(query.getUpdateTimeBegin()), Store::getUpdateTime, query.getUpdateTimeBegin())
            .le(Objects.nonNull(query.getUpdateTimeEnd()), Store::getUpdateTime, query.getUpdateTimeEnd())
            .eq(Objects.nonNull(query.getUpdateBy()), Store::getUpdateBy, query.getUpdateBy())
            .orderByDesc(Store::getId);
        return wrapper;
    }

    /**
     * 生成门店编码：走 {@link IBizCodeGenerator}，类型 {@link BizCodeType#STORE_CODE}。
     */
    private String generateStoreCode() {
        return bizCodeGenerator.generate(BizCodeType.STORE_CODE, Map.of());
    }

}
