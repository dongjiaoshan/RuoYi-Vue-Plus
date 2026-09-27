package org.dromara.djs.common.store.context;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.insert.Insert;

import java.util.List;

/**
 * 复用现有门店白名单与 SQL 解析：后台仍按当前门店等值过滤，applet 按授权门店集合过滤。
 * 集合在聚合 / 分页之前作用于源表；无绑定注入恒假条件，绝不把空集合解释为全域。
 */
public class StoreScopeInnerInterceptor extends TenantLineInnerInterceptor {

    public StoreScopeInnerInterceptor(StoreLineHandler handler) {
        super(handler);
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        List<Long> storeIds = StoreContext.getAccessibleStoreIds();
        if (storeIds == null) {
            return super.buildTableExpression(table, where, whereSegment);
        }
        if (getTenantLineHandler().ignoreTable(table.getName())) {
            return null;
        }
        if (storeIds.isEmpty()) {
            return new EqualsTo(new LongValue(1), new LongValue(0));
        }
        return new InExpression(getAliasColumn(table),
            new ParenthesedExpressionList<>(storeIds.stream().map(LongValue::new).toList()));
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        // 授权集合不能充当 INSERT 的单个 store_id。applet 写 service 显式校验目标店，
        // 本拦截器负责已有记录的 SELECT/UPDATE/DELETE 隔离；后台沿用单店补列行为。
        if (StoreContext.getAccessibleStoreIds() == null) {
            super.processInsert(insert, index, sql, obj);
        }
    }
}
