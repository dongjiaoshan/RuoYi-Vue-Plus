package org.dromara.djs.common.base;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Collection;
import java.util.Date;

/**
 * djs 业务服务通用基类。
 *
 * <p>提供 djs 业务表统一软删实现。</p>
 *
 * <p><b>为什么不直接走 MP 默认 deleteByIds / updateById</b>：</p>
 * <ul>
 *   <li>{@code baseMapper.deleteByIds(ids)} 走 {@code LogicSqlInjector}：SQL 改写为
 *       {@code UPDATE SET del_flag='1' WHERE id IN(...)}。这条 UPDATE 由 MP 内部合成的
 *       "空实体 + 仅 delFlag" {@code MetaObject} 触发 {@code updateFill}，
 *       {@link org.dromara.djs.common.handler.DjsMetaObjectHandler#updateFill} 检测到 id=null 后
 *       会跳过 {@code del_unique} 同步 → 软删行 {@code del_unique=0} 不变，
 *       UNIQUE(tenant_id, biz_col, del_unique) 在"软删后重启用同编码"场景下被错误阻塞。</li>
 *   <li>{@code updateById(entity)} + entity.setDelFlag("1")：entity-based update 路径下 MP 会把
 *       {@code @TableLogic} 字段自动从 SET 子句剥离，{@code del_flag} 写不进 SQL。</li>
 *   <li>{@code update(entity, wrapper).setSql("del_flag='1'")}：setSql 注入的 SQL fragment 也会
 *       被 MP JSQLParser 字段名匹配剥离（{@code @TableLogic} 字段无差别过滤）。</li>
 * </ul>
 *
 * <p><b>本基类的解决方案</b>：纯 wrapper update（{@code entity=null}），通过
 * {@link UpdateWrapper#set(String, Object)} 显式写入 {@code del_flag} / {@code del_unique} /
 * {@code update_by} / {@code update_time}。MP 的多租户拦截器在 final SQL 阶段仍会注入
 * {@code WHERE tenant_id=?}，租户隔离保留。</p>
 *
 * <p>使用范式（参考 {@code StoreServiceImpl} / {@code SupplierServiceImpl}）：</p>
 *
 * <pre>{@code
 * public class StoreServiceImpl extends DjsBaseServiceImpl<StoreMapper, Store>
 *     implements IStoreService {
 *
 *     @Override
 *     public int deleteWithValidByIds(Collection<Long> ids) {
 *         // 这里做业务关联校验（被下游引用则禁止删）
 *         return softDelete(ids);
 *     }
 * }
 * }</pre>
 *
 * <p>子类实体必须同时声明 {@code Long delUnique} + {@code String delFlag}（标 {@code @TableLogic}）。</p>
 *
 * @param <M> Mapper 类型（必须 {@code extends BaseMapperPlus<T, ?>}）
 * @param <T> 实体类型（必须 {@code extends TenantEntity}）
 * @author djs
 * @since D03 SYS-FIX-001（D03 testing-human #1 触发重写，原 D02 _open-issues #18 实现废弃）
 */
public abstract class DjsBaseServiceImpl<M extends BaseMapperPlus<T, ?>, T extends TenantEntity> {

    /**
     * 子类注入的 Mapper（与 ruoyi {@code ServiceImpl#baseMapper} 同名同语义）。
     */
    protected final M baseMapper;

    protected DjsBaseServiceImpl(M baseMapper) {
        this.baseMapper = baseMapper;
    }

    /**
     * 软删：循环 wrapper-only update，逐个 id 显式 set
     * {@code del_flag='1'} / {@code del_unique=id} / {@code update_by} / {@code update_time}。
     *
     * <p>WHERE 子句额外 {@code eq("del_flag", "0")} 防止重复软删（同时也是 MP 多租户拦截器附加
     * {@code tenant_id} 过滤的载体表达式）。</p>
     *
     * @param ids 待软删的主键集合（空 / null 直接返 0）
     * @return 实际受影响行数（DB 不存在或已软删的 id 不计入）
     */
    protected int softDelete(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        int count = 0;
        for (Long id : ids) {
            UpdateWrapper<T> wrapper = Wrappers.<T>update()
                .eq("id", id)
                .eq("del_flag", "0")
                .set("del_flag", "1")
                .set("del_unique", id)
                .set("update_by", updateBy)
                .set("update_time", now);
            count += baseMapper.update(null, wrapper);
        }
        return count;
    }

    /**
     * 安全获取当前用户 ID：admin 业务路径下走 Sa-Token；系统初始化 / 后台 job 等
     * 无登录上下文场景下捕获异常返 0（与 ruoyi 自带 {@code MetaObjectHandler} 兜底一致）。
     *
     * <p>子类调用：wrapper-only update（不走 MetaObjectHandler.updateFill 自动审计）时显式
     * {@code .set("update_by", currentUserIdSafe()).set("update_time", new Date())}。</p>
     */
    protected Long currentUserIdSafe() {
        try {
            Long id = LoginHelper.getUserId();
            return id != null ? id : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
