package org.dromara.djs.warehouse.stock.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.djs.warehouse.vegreceive.mapper.VegReceiveMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 【三期】分篮口径的 SQL 契约（V6 row92）。
 *
 * <p>甲方口径：三期<b>没有真实地块</b>，只做文案显示。所以三期篮的
 * {@code plot_id / ear_no / white_bar_no} 全 NULL（或在「作物恰好 1 块在种地块」时带一个真实 plot_id），
 * 形状与普通产品级篮 / 退货篮 / 采摘地块篮<b>完全相同</b> —— {@code third_phase} 是与那三者<b>同级的
 * 第四个分篮维度</b>，不是 plot_id 的一个取值。</p>
 *
 * <p>因此凡是「按 (库位, 产品) 或 (库位, 地块) 批量命中行」的 UPDATE，都必须显式站队：
 * 要么只打普通货（{@code third_phase = 0}），要么只打三期货（{@code third_phase = 1}）。
 * 漏一条就会把两本账并在一起，而且是<b>静默</b>的 —— 库存数字看着正常，只是普通货被记成三期
 * （或反过来），等甲方对「三期总入库 / 总出库」的账才发现，那时已经分不开了。</p>
 *
 * <p>这些 WHERE 条件容易在后续改动里被顺手删掉（看起来像"多余的过滤"），故用 SQL 原文断言钉死。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("三期分篮 SQL 口径契约（V6 row92）")
class ThirdPhasePartitionSqlContractTest {

    /** 取 {@code @Update} 注解 SQL 原文并归一（压平空白 + 小写）。 */
    private static String updateSql(Class<?> mapper, String methodName, Class<?>... paramTypes) throws Exception {
        Method method = mapper.getMethod(methodName, paramTypes);
        Update update = method.getAnnotation(Update.class);
        assertThat(update).as("%s#%s 应带 @Update", mapper.getSimpleName(), methodName).isNotNull();
        return normalize(String.join(" ", update.value()));
    }

    /** 取 {@code @Select} 注解 SQL 原文并归一（压平空白 + 小写 + 还原 XML 实体）。 */
    private static String selectSql(Class<?> mapper, String methodName, Class<?>... paramTypes) throws Exception {
        Method method = mapper.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("%s#%s 应带 @Select", mapper.getSimpleName(), methodName).isNotNull();
        return normalize(String.join(" ", select.value()));
    }

    private static String normalize(String raw) {
        return raw.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ 盘点

    @Test
    @DisplayName("盘点回写 setStockAfterCheck 只 SET 普通非篮子行 —— 绝不命中 third_phase=1")
    void setStockAfterCheckExcludesThirdPhase() throws Exception {
        String sql = updateSql(LocationStockMapper.class, "setStockAfterCheck",
            Long.class, Long.class, BigDecimal.class, Integer.class, Long.class);

        // 三源标签全 NULL 是老口径；third_phase = 0 是第四个维度，缺了它三期篮会被当成非篮子行 SET
        assertThat(sql)
            .contains("plot_id is null")
            .contains("ear_no is null")
            .contains("white_bar_no is null")
            .contains("third_phase = 0");
        // 这条 UPDATE 没有 LIMIT：一旦同 (库位,产品) 下普通产品级篮与三期篮并存，
        // 少了 third_phase 条件就会把两行一起 SET 成同一个数 → 组合计翻倍
        assertThat(sql).doesNotContain("limit");
    }

    @Test
    @DisplayName("盘点「篮子合计」sumBasketStockByProductLocation 把 third_phase=1 也算进篮子")
    void basketSumCountsThirdPhaseAsBasket() throws Exception {
        String sql = selectSql(LocationStockMapper.class, "sumBasketStockByProductLocation",
            Long.class, Long.class);

        // 与 setStockAfterCheck 的 third_phase = 0 严格互补：
        // 三期库存必须从「实盘量」里被减掉（算篮子），否则非篮目标量虚高一个三期篮的量
        assertThat(sql).contains("third_phase = 1");
        assertThat(sql)
            .contains("plot_id is not null")
            .contains("ear_no is not null")
            .contains("white_bar_no is not null");
    }

    // ------------------------------------------------ product / plot 维度扣加

    @Test
    @DisplayName("product 维度扣减 / 累加一律只动普通货（third_phase = 0）")
    void productDimensionMutationsStayOnNormalBaskets() throws Exception {
        assertThat(updateSql(LocationStockMapper.class, "deductByProductLocation",
            Long.class, Long.class, BigDecimal.class, Long.class)).contains("third_phase = 0");
        assertThat(updateSql(LocationStockMapper.class, "addByProductLocation",
            Long.class, Long.class, BigDecimal.class, Long.class)).contains("third_phase = 0");
        // 退货篮与三期篮的形状撞得最狠（三源标签都全空），漏了这条一笔门店退货会被记成三期入库
        assertThat(updateSql(LocationStockMapper.class, "addByProductLocationReturn",
            Long.class, Long.class, BigDecimal.class, Long.class)).contains("third_phase = 0");
    }

    @Test
    @DisplayName("三期专属累加只动三期篮（third_phase = 1），与普通累加逐字同构")
    void thirdPhaseUpsertStaysOnThirdPhaseBaskets() throws Exception {
        String sql = updateSql(LocationStockMapper.class, "addByProductLocationThirdPhase",
            Long.class, Long.class, BigDecimal.class, Long.class);
        assertThat(sql).contains("third_phase = 1");
        assertThat(sql).doesNotContain("third_phase = 0");
    }

    @Test
    @DisplayName("按地块扣减 deductByPlotLocation 不跨三期 —— 三期在种地块篮与采摘地块篮同 (库位,地块)")
    void deductByPlotLocationExcludesThirdPhase() throws Exception {
        String sql = updateSql(LocationStockMapper.class, "deductByPlotLocation",
            Long.class, Long.class, BigDecimal.class, Long.class);

        assertThat(sql)
            .contains("plot_id = #{plotid}")
            .contains("third_phase = 0");
    }

    @Test
    @DisplayName("自产收货 UPSERT addStockByPlotLocation 不并进三期篮（同落 L0006、同形状）")
    void vegReceiveUpsertExcludesThirdPhase() throws Exception {
        String sql = updateSql(VegReceiveMapper.class, "addStockByPlotLocation",
            Long.class, Long.class, Long.class, BigDecimal.class, Long.class);

        assertThat(sql).contains("third_phase = 0");
        // row55 的 product 维度不能被顺手改掉（一块地先后收红薯 / 红薯杆要分篮）
        assertThat(sql).contains("product_id <=> #{productid}");
    }
}
