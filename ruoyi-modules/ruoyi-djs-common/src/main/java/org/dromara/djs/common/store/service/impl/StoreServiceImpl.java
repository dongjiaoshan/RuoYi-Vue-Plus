package org.dromara.djs.common.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    public StoreServiceImpl(StoreMapper baseMapper, IBizCodeGenerator bizCodeGenerator, UserService userService) {
        super(baseMapper);
        this.bizCodeGenerator = bizCodeGenerator;
        this.userService = userService;
    }

    @Override
    public TableDataInfo<StoreVo> queryPageList(StoreQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Store> wrapper = buildQueryWrapper(query);
        Page<StoreVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        page.getRecords().forEach(this::fillEmployeeCount);
        return TableDataInfo.build(page);
    }

    @Override
    public List<StoreVo> queryList(StoreQuery query) {
        List<StoreVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        list.forEach(this::fillEmployeeCount);
        return list;
    }

    @Override
    public StoreVo queryById(Long id) {
        StoreVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillEmployeeCount(vo);
        }
        return vo;
    }

    /**
     * 员工数量回填。当前 t_md_store 与 sys_user 关联模型仅有 manager_user_id（单店长），
     * 无法直接统计员工数；hotfix 先置 0，待 Kevin 定义关联规则（store_user_relation / sys_dept 复用）后改实统计。
     */
    private void fillEmployeeCount(StoreVo vo) {
        vo.setEmployeeCount(0);
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
        if (userId != null) {
            // 校验 sys_user 存在性（已软删 / 不存在 → userName 返 null）
            String userName = userService.selectUserNameById(userId);
            if (StringUtils.isBlank(userName)) {
                throw new ServiceException("店长用户不存在或已停用：" + userId);
            }
        }
        // 仅 update manager_user_id 列（wrapper-only update **不**走 MetaObjectHandler.updateFill —
        // 需显式 set update_by / update_time，与 DjsBaseServiceImpl#softDelete 同范式）
        UpdateWrapper<Store> wrapper = new UpdateWrapper<Store>()
            .set("manager_user_id", userId)
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
        // TODO SYS-MD-002 → D05+：业务表 wire 后，删除前需校验"是否被 t_store_product_relation /
        // t_store_sale_record / t_store_member 等引用"，引用存在则提示先解绑。
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
