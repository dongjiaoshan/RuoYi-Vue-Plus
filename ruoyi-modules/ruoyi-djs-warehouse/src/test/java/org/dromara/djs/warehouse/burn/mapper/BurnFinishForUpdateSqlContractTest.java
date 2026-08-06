package org.dromara.djs.warehouse.burn.mapper;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 契约测试：{@code PigBurnRecordServiceImpl#finishBurn} 那句
 * {@code LambdaQueryWrapper.last("FOR UPDATE")} 必须能穿过多租户拦截器且行锁语义不丢（V6-R43）。
 *
 * <p>为什么单独立一支测试：这是本仓库<b>第一处</b>运行期 {@code SELECT ... FOR UPDATE}
 *（其余并发控制都走条件 UPDATE 乐观锁）。多租户拦截器会把 SQL 交给 JSqlParser 解析、加租户条件、
 * 再 toString 回去 —— 一旦 JSqlParser 升级后丢掉尾部的 {@code FOR UPDATE}，
 * 「处理完成 vs 后台调整入库重量」的并发保护会<b>静默</b>失效（不报错、只是白条 in_weight 与损耗偶发对不上）。
 * 这支测试把这个隐性依赖钉住。</p>
 *
 * @author djs
 * @since V6-R43
 */
@Tag("local")
@Tag("dev")
@DisplayName("燎毛「处理完成」FOR UPDATE 行锁 SQL 契约")
class BurnFinishForUpdateSqlContractTest {

    @Test
    @DisplayName("多租户拦截器改写后仍保留尾部 FOR UPDATE，且租户条件进了 WHERE")
    void testForUpdateSurvivesTenantRewrite() {
        TenantLineInnerInterceptor interceptor = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new StringValue("1001");
            }
        });
        // 形状与 finishBurn 里 LambdaQueryWrapper.eq(whiteBarId).last("FOR UPDATE") 生成的 SQL 一致
        String sql = "SELECT id,product_weight,product_id FROM t_warehouse_product_inhouse "
            + "WHERE del_flag='0' AND (white_bar_id = 5001) FOR UPDATE";

        String rewritten = interceptor.parserSingle(sql, null);

        assertThat(rewritten).endsWith("FOR UPDATE");
        assertThat(rewritten).contains("tenant_id = '1001'");
        assertThat(rewritten).contains("white_bar_id = 5001");
    }

}
