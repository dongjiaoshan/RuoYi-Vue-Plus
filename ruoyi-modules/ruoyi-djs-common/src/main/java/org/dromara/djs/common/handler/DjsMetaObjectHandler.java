package org.dromara.djs.common.handler;

import cn.hutool.core.util.ObjectUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.dromara.common.mybatis.handler.InjectionMetaObjectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * djs 自定义元对象字段填充器。
 *
 * <p>在 ruoyi 自带 {@link InjectionMetaObjectHandler}（自动填创建/更新时间 + 创建/更新人）基础上，
 * <b>额外处理 djs 业务表的软删除唯一性辅助列 {@code del_unique}</b>。</p>
 *
 * <h3>为什么需要这层覆盖（doc/05 §6.3.0 / D01 _open-issues #3）</h3>
 * <ul>
 *   <li>doc/05 §6.3.0 原方案使用 MySQL 8 的 GENERATED VIRTUAL 列：
 *       {@code del_unique BIGINT GENERATED ALWAYS AS (IF(del_flag='0', 0, id)) VIRTUAL}。</li>
 *   <li>SYS-INIT-001 实际落地时发现 MySQL 8 不允许 GENERATED 列引用 AUTO_INCREMENT 主键
 *       （ER 3109）。最终 DDL 改为 {@code del_unique BIGINT NOT NULL DEFAULT 0}，
 *       依赖<b>应用层</b>在软删时同步 SET。</li>
 *   <li>本 Handler 在 {@link #updateFill(MetaObject)} 检测 {@code delFlag='1'} 时，
 *       把 {@code id} 字段值同步写入 {@code delUnique}，保证 UNIQUE 约束在软删行上
 *       天然不冲突（已删除行 del_unique=id 不会重复；未删除行 del_unique=0 参与唯一性校验）。</li>
 * </ul>
 *
 * <h3>覆盖 ruoyi 默认 Bean 的方式</h3>
 * <p>ruoyi {@code MybatisPlusConfig#metaObjectHandler()} 定义了
 * {@link InjectionMetaObjectHandler}（普通 {@code @Bean}）；本类同属
 * {@link com.baomidou.mybatisplus.core.handlers.MetaObjectHandler} 类型，加 {@link Primary}
 * 让 MyBatis-Plus 通过 {@code @Autowired} 注入时优先选择本实现，从而完全替代默认 Handler
 * （同时通过 {@code extends} 复用原 fill 逻辑，避免漏字段）。</p>
 *
 * <h3>业务实体使用约束</h3>
 * <ul>
 *   <li>业务实体若有软删除场景（带 {@code @TableLogic} 的 {@code delFlag}），必须同时声明
 *       {@code Long delUnique} 字段（驼峰，对应 DB 列 {@code del_unique BIGINT NOT NULL DEFAULT 0}）。</li>
 *   <li>{@code delUnique} 字段不需要 {@code @TableField(fill=...)} 注解；
 *       本 Handler 通过 {@link MetaObject#setValue(String, Object)} 直接赋值，
 *       MyBatis-Plus 因 {@code updateStrategy: NOT_NULL} 会把它写入 UPDATE 语句。</li>
 *   <li>UNIQUE 约束必须把 {@code del_unique} 放最后一列（参 doc/05 §6.3.0）：
 *       {@code UNIQUE (tenant_id, biz_col, del_unique)}。</li>
 * </ul>
 *
 * @author djs
 * @since SYS-INFRA-007
 * @see InjectionMetaObjectHandler
 */
@Primary
@Component
public class DjsMetaObjectHandler extends InjectionMetaObjectHandler {

    private static final Logger log = LoggerFactory.getLogger(DjsMetaObjectHandler.class);

    /**
     * 实体属性名：软删除标记。
     */
    private static final String FIELD_DEL_FLAG = "delFlag";

    /**
     * 实体属性名：主键 id。
     */
    private static final String FIELD_ID = "id";

    /**
     * 实体属性名：软删除唯一性辅助列。
     */
    private static final String FIELD_DEL_UNIQUE = "delUnique";

    /**
     * 软删除标记的"已删除"值（与 ruoyi {@code mybatis-plus.global-config.dbConfig.logicDeleteValue=1} 对齐）。
     */
    private static final String DEL_FLAG_DELETED = "1";

    /**
     * 更新填充：先走 ruoyi 默认（updateTime / updateBy），
     * 再处理 djs 软删 del_unique 同步。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 1) ruoyi 默认填充
        super.updateFill(metaObject);

        // 2) djs 软删 del_unique 同步：当 delFlag='1' 且 delUnique 字段存在时，
        //    把 id 写入 delUnique，保证 UNIQUE 约束不阻塞新数据插入
        try {
            if (!metaObject.hasSetter(FIELD_DEL_UNIQUE) || !metaObject.hasGetter(FIELD_DEL_FLAG)) {
                // 实体不带 del_unique 字段（如 sys_* / 统计预聚合表），跳过
                return;
            }

            Object delFlagValue = metaObject.getValue(FIELD_DEL_FLAG);
            if (!DEL_FLAG_DELETED.equals(String.valueOf(delFlagValue))) {
                // 非软删 UPDATE（普通业务更新），不动 delUnique
                return;
            }

            if (!metaObject.hasGetter(FIELD_ID)) {
                log.warn("[djs-tenant] 软删 fill 失败：实体 {} 没有 id getter",
                    metaObject.getOriginalObject().getClass().getSimpleName());
                return;
            }

            Object idValue = metaObject.getValue(FIELD_ID);
            if (ObjectUtil.isNull(idValue)) {
                log.warn("[djs-tenant] 软删 fill 失败：实体 {} id 为 null",
                    metaObject.getOriginalObject().getClass().getSimpleName());
                return;
            }

            // 写入 delUnique = id；MyBatis-Plus updateStrategy=NOT_NULL 会把它带入 SQL SET 子句
            metaObject.setValue(FIELD_DEL_UNIQUE, idValue);
            if (log.isDebugEnabled()) {
                log.debug("[djs-tenant] 软删 del_unique fill: entity={} id={} -> del_unique",
                    metaObject.getOriginalObject().getClass().getSimpleName(), idValue);
            }
        } catch (Exception e) {
            // 不抛异常打断主流程，仅 warn —— 上游 UPDATE 失败更应优先暴露
            log.warn("[djs-tenant] del_unique fill 异常（不阻断 UPDATE）：{}", e.getMessage(), e);
        }
    }
}
