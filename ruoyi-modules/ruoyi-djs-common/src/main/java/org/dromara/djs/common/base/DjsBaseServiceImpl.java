package org.dromara.djs.common.base;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * djs 业务服务通用基类。
 *
 * <p>本基类的存在动机：MyBatis-Plus 的 {@code LogicDeleteInterceptor} 在执行
 * {@code baseMapper.deleteByIds} / {@code baseMapper.deleteById} 时，把 SQL 改写为
 * {@code UPDATE ... SET del_flag='1' WHERE id IN (...)}。这条 UPDATE 由 MP 内部合成的
 * "空实体 + 仅 delFlag" MetaObject 触发 {@code updateFill}，
 * {@link org.dromara.djs.common.handler.DjsMetaObjectHandler#updateFill(org.apache.ibatis.reflection.MetaObject)}
 * 检测到 id=null 后会跳过 {@code del_unique} 同步——导致软删行 {@code del_unique=0} 未变，
 * UNIQUE(tenant_id, biz_col, del_unique) 约束在"软删后重新启用同编码"场景下被错误阻塞。</p>
 *
 * <p>另一道坑：实体上 {@code delFlag} 标了 {@code @TableLogic}，MyBatis-Plus 在
 * <strong>entity-based update</strong>（{@code updateById(entity)} / {@code update(entity, wrapper)}）路径下
 * 会把 entity 里 {@code setDelFlag} 自动从 SET 子句剥离——只在 {@code deleteById} 路径下由
 * {@code LogicDeleteInterceptor} 把 SQL 改写为 {@code UPDATE SET del_flag='1'}。
 * 直接 {@code entity.setDelFlag("1")} 在 UPDATE SQL 里看不到，DB 仍 {@code del_flag=0}。</p>
 *
 * <p>解决：本基类 {@link #softDelete(Collection, Supplier)} 用 {@code update(entity, wrapper)}：
 * <ul>
 *   <li>entity 设 {@code id} + {@code delUnique}，触发 {@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
 *       的 {@code updateFill}（写入 {@code update_by} / {@code update_time}）；</li>
 *   <li>{@link UpdateWrapper} 用 {@code setSql("del_flag = '1'")} 强写 SET 子句，
 *       绕过 {@code @TableLogic} 剥离；</li>
 *   <li>{@code eq("id", id)} 提供 WHERE 主键，避免触发 MP 全表更新保护。</li>
 * </ul></p>
 *
 * <p>使用范式（参考 {@code PersonServiceImpl} / {@code StoreServiceImpl}）：</p>
 *
 * <pre>{@code
 * public class StoreServiceImpl extends DjsBaseServiceImpl<StoreMapper, Store>
 *     implements IStoreService {
 *
 *     @Override
 *     public int deleteWithValidByIds(Collection<Long> ids) {
 *         // ... 这里做业务关联校验（被下游引用则禁止删）
 *         return softDelete(ids, Store::new);
 *     }
 * }
 * }</pre>
 *
 * <p>子类实体必须同时声明 {@code Long delUnique} + {@code String delFlag}（标 {@code @TableLogic}）。</p>
 *
 * @param <M> Mapper 类型（必须 {@code extends BaseMapperPlus<T, ?>}）
 * @param <T> 实体类型（必须 {@code extends TenantEntity}，且实现 {@link SoftDeletable}）
 * @author djs
 * @since D03 SYS-MD-002（D02 _open-issues #18 决策 b 触发抽取）
 */
public abstract class DjsBaseServiceImpl<M extends BaseMapperPlus<T, ?>, T extends TenantEntity & DjsBaseServiceImpl.SoftDeletable> {

    /**
     * 子类注入的 Mapper（与 ruoyi {@code ServiceImpl#baseMapper} 同名同语义）。
     */
    protected final M baseMapper;

    protected DjsBaseServiceImpl(M baseMapper) {
        this.baseMapper = baseMapper;
    }

    /**
     * 软删：循环 {@code update(entity, wrapper)}，entity 写 {@code id} + {@code delUnique=id}，
     * wrapper 用 {@code setSql("del_flag = '1'")} 强写 SET 子句绕过 {@code @TableLogic}。
     *
     * <p>请勿改成 {@code baseMapper.deleteByIds(ids)} 或 {@code updateById(entity)}——原因见类注释。</p>
     *
     * @param ids             待软删的主键集合（空 / null 直接返 0）
     * @param entityFactory   实体构造器，例：{@code Store::new}（子类直接传方法引用即可）
     * @return 实际受影响行数（DB 不存在的 id 不计入）
     */
    protected int softDelete(Collection<Long> ids, Supplier<T> entityFactory) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        int count = 0;
        for (Long id : ids) {
            T entity = entityFactory.get();
            entity.setId(id);
            entity.setDelUnique(id);
            UpdateWrapper<T> wrapper = Wrappers.<T>update()
                .eq("id", id)
                .setSql("del_flag = '1'");
            count += baseMapper.update(entity, wrapper);
        }
        return count;
    }

    /**
     * djs 业务实体的"软删除"契约。
     *
     * <p>子类实体（如 {@code Store} / {@code Person}）需实现本接口（用 {@code @Data} 自动生成的
     * setter 即满足）。基类 {@link #softDelete(Collection, Supplier)} 通过本接口写入
     * {@code id} / {@code delFlag} / {@code delUnique} 三个字段。</p>
     *
     * <p>{@link TenantEntity} 已提供 {@code id}，{@code delFlag} / {@code delUnique} 由本接口约束。</p>
     */
    public interface SoftDeletable {

        void setId(Long id);

        void setDelFlag(String delFlag);

        void setDelUnique(Long delUnique);
    }
}
