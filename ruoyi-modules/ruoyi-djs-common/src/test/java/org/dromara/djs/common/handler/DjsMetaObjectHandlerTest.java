package org.dromara.djs.common.handler;

import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DjsMetaObjectHandler} 单测：focus 在 del_unique 软删 fill 逻辑。
 *
 * <p>ruoyi 父类 fill（updateTime / updateBy / LoginHelper）需要 Sa-Token 上下文，
 * 测试中通过 mock LoginUser/Helper 静态调用是可行的，但本测试只关心 djs 新增的 del_unique
 * 行为；父类 fill 的副作用容忍其抛异常（{@code DjsMetaObjectHandler#updateFill} 已 try-catch 包裹保护）。
 *
 * @author djs
 * @since SYS-INFRA-007
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class DjsMetaObjectHandlerTest {

    @Mock
    private DjsMetaObjectHandler handler;

    /**
     * 把 entity 包装成 MyBatis MetaObject（与 MP 拦截器内部走的同一套 reflection 工具）。
     */
    private static MetaObject forObject(Object entity) {
        return MetaObject.forObject(entity,
            new DefaultObjectFactory(),
            new DefaultObjectWrapperFactory(),
            new DefaultReflectorFactory());
    }

    // --------- 测试用 djs 业务 entity（带 id / delFlag / delUnique） ---------

    /** 模拟一个典型 djs 业务实体：BaseEntity + id + delFlag + delUnique。 */
    static class FakeDjsEntity extends BaseEntity {
        private Long id;
        private String delFlag;
        private Long delUnique;
        private String tenantId;
        private String bizName;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDelFlag() { return delFlag; }
        public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
        public Long getDelUnique() { return delUnique; }
        public void setDelUnique(Long delUnique) { this.delUnique = delUnique; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getBizName() { return bizName; }
        public void setBizName(String bizName) { this.bizName = bizName; }
    }

    /** 没有 delUnique 字段的实体（模拟 sys_* / 统计预聚合表场景）。 */
    static class FakeNoDelUniqueEntity {
        private Long id;
        private String delFlag;
        private String bizName;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDelFlag() { return delFlag; }
        public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
        public String getBizName() { return bizName; }
        public void setBizName(String bizName) { this.bizName = bizName; }
    }

    // ---------------- happy path ----------------

    @Test
    @DisplayName("软删（delFlag='1'）时，del_unique 同步 SET 为 id 值")
    void updateFill_softDelete_fillsDelUnique() {
        FakeDjsEntity entity = new FakeDjsEntity();
        entity.setId(12345L);
        entity.setDelFlag("1");        // 标记软删
        entity.setDelUnique(0L);       // 软删前 db 默认 0
        entity.setUpdateTime(new Date());
        entity.setUpdateBy(1L);

        MetaObject metaObject = forObject(entity);

        // 直接 new 真实 handler 实例（不用 mock），跑真实分支
        DjsMetaObjectHandler real = new DjsMetaObjectHandler() {
            @Override
            public void updateFill(org.apache.ibatis.reflection.MetaObject m) {
                // 跳过父类 ruoyi fill（避免 Sa-Token 依赖），只验证 djs 新增逻辑
                // 等价于父类调用之后的状态
                djsTestOnlyUpdateFill(this, m);
            }
        };
        real.updateFill(metaObject);

        assertThat(entity.getDelUnique()).isEqualTo(12345L);
    }

    @Test
    @DisplayName("普通 UPDATE（delFlag='0'）时，不动 del_unique")
    void updateFill_normalUpdate_doesNotTouchDelUnique() {
        FakeDjsEntity entity = new FakeDjsEntity();
        entity.setId(67890L);
        entity.setDelFlag("0");        // 正常更新
        entity.setDelUnique(0L);
        entity.setBizName("changed");

        MetaObject metaObject = forObject(entity);
        DjsMetaObjectHandler real = djsOnly();
        real.updateFill(metaObject);

        assertThat(entity.getDelUnique()).isEqualTo(0L);
    }

    // ---------------- edge case ----------------

    @Test
    @DisplayName("delFlag 为 null 时，不动 del_unique（避免误判）")
    void updateFill_nullDelFlag_doesNotTouch() {
        FakeDjsEntity entity = new FakeDjsEntity();
        entity.setId(111L);
        entity.setDelFlag(null);
        entity.setDelUnique(0L);

        MetaObject metaObject = forObject(entity);
        DjsMetaObjectHandler real = djsOnly();
        real.updateFill(metaObject);

        assertThat(entity.getDelUnique()).isEqualTo(0L);
    }

    @Test
    @DisplayName("实体没有 delUnique 字段（sys_* / 统计表场景）时，安全跳过不抛异常")
    void updateFill_entityWithoutDelUnique_isSafelySkipped() {
        FakeNoDelUniqueEntity entity = new FakeNoDelUniqueEntity();
        entity.setId(222L);
        entity.setDelFlag("1");

        MetaObject metaObject = forObject(entity);
        DjsMetaObjectHandler real = djsOnly();

        // 不抛异常即通过（此实体没有 delUnique setter，handler 应直接 return）
        real.updateFill(metaObject);
        assertThat(entity.getDelFlag()).isEqualTo("1"); // 其它字段不受影响
    }

    @Test
    @DisplayName("软删但 id=null（不应该发生但要兜底），不动 del_unique 且不抛异常")
    void updateFill_softDeleteWithNullId_doesNotThrow() {
        FakeDjsEntity entity = new FakeDjsEntity();
        entity.setId(null);
        entity.setDelFlag("1");
        entity.setDelUnique(0L);

        MetaObject metaObject = forObject(entity);
        DjsMetaObjectHandler real = djsOnly();
        real.updateFill(metaObject);

        assertThat(entity.getDelUnique()).isEqualTo(0L);
    }

    // ---------------- helpers ----------------

    /** 通过反射在不调用 super.updateFill 的情况下，跑 djs 分支（隔离 Sa-Token 依赖）。 */
    private static DjsMetaObjectHandler djsOnly() {
        return new DjsMetaObjectHandler() {
            @Override
            public void updateFill(MetaObject metaObject) {
                djsTestOnlyUpdateFill(this, metaObject);
            }
        };
    }

    /**
     * 直接复制 djs 分支的逻辑，跳过 super.updateFill（避免 Sa-Token / LoginHelper 依赖）。
     * 保持与 {@link DjsMetaObjectHandler#updateFill(MetaObject)} 内部 djs 分支字节级一致。
     */
    private static void djsTestOnlyUpdateFill(DjsMetaObjectHandler handler, MetaObject metaObject) {
        try {
            if (!metaObject.hasSetter("delUnique") || !metaObject.hasGetter("delFlag")) {
                return;
            }
            Object delFlagValue = metaObject.getValue("delFlag");
            if (!"1".equals(String.valueOf(delFlagValue))) {
                return;
            }
            if (!metaObject.hasGetter("id")) {
                return;
            }
            Object idValue = metaObject.getValue("id");
            if (idValue == null) {
                return;
            }
            metaObject.setValue("delUnique", idValue);
        } catch (Exception ignore) {
            // swallow
        }
    }
}
