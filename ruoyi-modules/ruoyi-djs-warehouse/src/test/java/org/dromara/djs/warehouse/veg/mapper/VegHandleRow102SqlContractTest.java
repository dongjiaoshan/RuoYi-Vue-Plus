package org.dromara.djs.warehouse.veg.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6 row102 毛菜处理间链路的 SQL 口径契约。
 *
 * <p>改造后有三个数一定要同源，否则工人当场看到自相矛盾的界面：</p>
 * <ol>
 *   <li>地块卡的「剩余重量」（{@link PlantingRecordMapper#selectPlotDetailByCrop}）</li>
 *   <li>分产品剩余（{@link LocationStockMapper#selectPlotProductStocks}）</li>
 *   <li>处理录入的出库上限与实际扣减（{@link LocationStockMapper#sumPlotProductStock}
 *       + {@link LocationStockMapper#selectPlotProductBaskets}）</li>
 * </ol>
 *
 * <p>三者的口径都是「<b>本条种植记录</b>（{@code source_biz_id}）在毛菜鲜品库 L0006 的库存，
 * 排除三期篮」。{@code source_biz_id} 这个条件是第二轮修复的核心：少了它，同地块的另一条种植记录 /
 * 采摘活动直送的货 / 共享同一产品的另一作物的货全会串进来 —— 实测能让收口把别人的 25kg 结成
 * 自己的损耗（{@code loss > picked}）、让采摘 60kg 的记录出库 68kg、让两条记录各显示同一份剩余。
 * 这些条件写在注解 SQL 里，单测的 mock 打不到，只能用原文断言钉死。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("毛菜处理间链路 SQL 口径契约（V6 row102）")
class VegHandleRow102SqlContractTest {

    private static String selectSql(Class<?> mapper, String methodName, Class<?>... paramTypes) throws Exception {
        Method method = mapper.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("%s#%s 应带 @Select", mapper.getSimpleName(), methodName).isNotNull();
        return normalize(String.join(" ", select.value()));
    }

    private static String updateSql(Class<?> mapper, String methodName, Class<?>... paramTypes) throws Exception {
        Method method = mapper.getMethod(methodName, paramTypes);
        Update update = method.getAnnotation(Update.class);
        assertThat(update).as("%s#%s 应带 @Update", mapper.getSimpleName(), methodName).isNotNull();
        return normalize(String.join(" ", update.value()));
    }

    private static String normalize(String raw) {
        return raw.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("地块卡剩余重量 = 本条种植记录在毛菜间的实时库存（按 source_biz_id 收窄）")
    void plotRemainWeightReadsFreshVegStock() throws Exception {
        String sql = selectSql(PlantingRecordMapper.class, "selectPlotDetailByCrop", Long.class);

        // 读库存而不是做减法：采摘即入库之后，毛菜间出库 / 盘点都会动库存，减法必然与实物漂移
        assertThat(sql)
            .as("剩余重量必须查 location_stock")
            .contains("t_warehouse_location_stock")
            .as("必须限定毛菜鲜品库 L0006（别把蔬菜保鲜库等其他库位的货也算成毛菜间剩余）")
            .contains("l0006")
            .as("三期篮不参与毛菜处理链路")
            .contains("s.third_phase = 0");
        assertThat(sql)
            .as("旧减法口径必须已删除，两套口径并存会让地块卡与出库上限打架")
            .doesNotContain("coalesce(h.picked_weight,0) - coalesce(h.handled_weight,0)");

        // 🔴 第二轮修复的核心：按「这篮是谁采的」而不是「这篮在哪块地」定范围。
        // 同一地块可以同时挂两条种植记录（两季 / 补录），按 plot 算会让两条各显示同一份库存、
        // mp 头卡 Σ remainWeight 翻倍（实测两条各显 65、真实共 65）。
        assertThat(sql)
            .as("剩余重量必须收窄到本条种植记录（source_biz_id = planting_record.id）")
            .contains("s.source_biz_id = p.id");
        assertThat(sql)
            .as("收窄到记录之后按地块聚合的旧条件必须删掉，留着就还是会串到同地块另一条记录")
            .doesNotContain("s.plot_id = p.plot_id");
    }

    @Test
    @DisplayName("FIFO 取篮：按 create_time 先进先出 + 只取本记录名下有余量的普通篮")
    void freshVegBasketsAreFifoAndExcludeThirdPhase() throws Exception {
        String sql = selectSql(LocationStockMapper.class, "selectPlotProductBaskets",
            Long.class, Long.class, Long.class, Long.class);

        assertThat(sql)
            .contains("location_id = #{locationid}")
            .contains("product_id = #{productid}")
            .contains("plot_id = #{plotid}")
            .as("不带来源标识会扣到同地块另一条记录 / 采摘活动直送的篮子上")
            .contains("source_biz_id = #{sourcebizid}")
            .as("三期篮不走毛菜处理出库")
            .contains("third_phase = 0")
            .as("空篮子不该进 FIFO 队列")
            .contains("product_stock > 0");
        // 历史迁移补建的篮子用的是 AUTO_INCREMENT id（比应用侧雪花 id 大），只按 id 排会把最老的货排到最后
        assertThat(sql)
            .as("FIFO 必须以 create_time 为主序")
            .contains("order by create_time asc, id asc");
    }

    @Test
    @DisplayName("出库上限校验与 FIFO 取篮同源：WHERE 条件必须逐条对齐（含 source_biz_id）")
    void stockSumMatchesBasketQuery() throws Exception {
        String sumSql = selectSql(LocationStockMapper.class, "sumPlotProductStock",
            Long.class, Long.class, Long.class, Long.class);

        // 校验说够、真扣时不够 → 事务走到一半抛 409，工人看到的是「刷新后重试」而不是真实原因；
        // 反过来校验范围比扣减宽（少一个 source_biz_id）→ 拿别人的货抬高自己的出库上限，
        // 实测靠这个能让采摘 60kg 的记录出库 68kg。
        assertThat(sumSql)
            .contains("location_id = #{locationid}")
            .contains("product_id = #{productid}")
            .contains("plot_id = #{plotid}")
            .contains("source_biz_id = #{sourcebizid}")
            .contains("third_phase = 0")
            .contains("del_flag = '0'");
    }

    @Test
    @DisplayName("分产品剩余与出库上限同源：同样按 source_biz_id 聚合（不是按 plot_id）")
    void plotProductStocksMatchDeductScope() throws Exception {
        String sql = selectSql(LocationStockMapper.class, "selectPlotProductStocks",
            Long.class, Collection.class);

        assertThat(sql)
            .contains("s.location_id = #{locationid}")
            .contains("s.product_id is not null")
            .contains("s.third_phase = 0")
            .as("聚合键必须是来源业务而不是地块，否则同地块两条记录各读到同一份库存")
            .contains("group by s.source_biz_id, s.product_id");
    }

    @Test
    @DisplayName("按篮扣减带 product_stock >= 行锁条件 —— 两个工人同时提交只有一个扣得动")
    void basketDeductIsAtomic() throws Exception {
        String sql = updateSql(LocationStockMapper.class, "deductStockById",
            Long.class, BigDecimal.class, Long.class);

        // 行锁 + 余量校验必须在同一条 UPDATE 里发生；拆成「先查再扣」就会超扣
        assertThat(sql)
            .contains("product_stock = product_stock - #{deductqty}")
            .contains("product_stock >= #{deductqty}")
            .contains("del_flag = '0'");
    }

    /**
     * 甲方 2026-08-19 终审口径：{@code loss = 地块入库量 − 果蔬月台 − 有机饲喂 − 出库}。
     *
     * <p>「出库」（毛菜间出库管理 {@code VegOutServiceImpl}）在这条公式里是<b>与月台、饲喂并列</b>的一项，
     * 并列项不能同时算进其中一项 —— 所以它<b>不计入</b>「果蔬处理重量」{@code handled_weight}
     * （后者只收毛菜处理录入那两个去向）。它扣的是实物库存，收口 {@code settleRemainAsLoss}
     * 按剩余库存结转损耗，这批 kg 自动从损耗里减掉，恒等式 {@code 入库 = 月台 + 饲喂 + 出库 + 损耗} 成立，
     * 且天然覆盖甲方点明的「其他地方从毛菜间出库」，不用逐条去列。</p>
     *
     * <p>这条口径已被推翻过一轮（曾经存在 {@code addHandledWeight} 回写），所以在<b>结构上</b>钉死：
     * 本 mapper 不得再出现任何写 {@code handled_weight} 的注解 SQL。毛菜处理录入侧的
     * {@code handled_weight} 走 {@code VegetableHandleServiceImpl} 的整行 {@code updateById}
     * （与 feed / sendPlatform / stockIn / loss 一起算完再写），不需要、也不该有单列原子累加的口子。</p>
     */
    @Test
    @DisplayName("🔴 毛菜间出库不计入「果蔬处理重量」：本 mapper 不得有任何写 handled_weight 的注解 SQL")
    void noMapperMethodMayWriteHandledWeight() {
        for (Method m : VegetableHandleMapper.class.getDeclaredMethods()) {
            Update update = m.getAnnotation(Update.class);
            if (update == null) {
                continue;
            }
            assertThat(normalize(String.join(" ", update.value())))
                .as("VegetableHandleMapper#%s 在写 handled_weight —— 甲方损耗公式里「出库」与「月台/饲喂」"
                    + "并列，回写会让同一批 kg 既进果蔬处理重量又已从库存扣掉（picked = handled + loss 当场不成立）。"
                    + "要加请先回看甲方 2026-08-19 口径。", m.getName())
                .doesNotContain("handled_weight");
        }
    }

    @Test
    @DisplayName("迁移 V202608310900：加列幂等 + 回填只填空值 + 补建沿用 0400 的 marker 判重")
    void fixMigrationIsIdempotent() throws Exception {
        String sql = migrationSql("V202608310900__V6-R102-fix-stock-source-scope.sql");

        // MySQL 的 ADD COLUMN / CREATE INDEX 没有 IF NOT EXISTS，裸写重跑会 1060/1061
        assertThat(sql)
            .as("加列必须 information_schema 探测后动态执行")
            .contains("information_schema.columns")
            .contains("column_name = 'source_biz_id'")
            .as("建索引同理")
            .contains("information_schema.statistics")
            .contains("index_name = 'idx_stock_source_biz'");

        // 回填幂等：只填还没填过的行
        assertThat(sql)
            .as("回填必须只命中 source_biz_id IS NULL 的行")
            .contains("s.source_biz_id is null");

        // 补建幂等 + 放宽条件：沿用 0400 的同一个 marker 判重，0400 迁过的行天然跳过
        assertThat(sql)
            .as("补建必须按 stock_flow 上的 marker NOT EXISTS 判重")
            .contains("not exists")
            .contains("v6-r102 历史迁移入毛菜保鲜库 handleid=");
        assertThat(sql)
            .as("F-6：补建条件必须放宽掉 is_weighed = 1（采摘进行中的地块同样有货在毛菜间）")
            .doesNotContain("h.is_weighed = 1");
        assertThat(sql)
            .as("补建的篮子必须当场带上 source_biz_id，否则补了也定位不到")
            .contains("source_biz_id");
    }

    @Test
    @DisplayName("收口结转损耗取篮：必须按 source_biz_id 收窄，且与两个读侧 WHERE 同构")
    void settleBasketsAreScopedBySource() throws Exception {
        String sql = selectSql(LocationStockMapper.class, "selectBasketsBySource", Long.class, Long.class);

        // 这是「读得到 / 结不掉」的唯一防线：少任何一个条件都会退回一轮那场灾难
        // （结转吃掉同库位所有记录的篮 → loss > picked、跨记录偷货）。
        assertThat(sql)
            .as("必须按来源收窄——不带它就会结掉别条种植记录、采摘活动直送篮的货")
            .contains("source_biz_id = #{sourcebizid}");
        assertThat(sql)
            .as("三期货不参与毛菜收口结转（它有自己的出口）")
            .contains("third_phase = 0");
        assertThat(sql)
            .as("只结产品篮：product_id 为空的是月台自产收货那条链路的行")
            .contains("product_id is not null");
        assertThat(sql)
            .as("单租户显式限定，与两个读侧一致")
            .contains("tenant_id = '1001'");
        assertThat(sql)
            .as("软删行不参与结转")
            .contains("del_flag = '0'");
        assertThat(sql)
            .as("按建篮时间 FIFO，历史迁移篮的 AUTO_INCREMENT id 比雪花小，只按 id 排会把最老的排到最后")
            .contains("order by create_time asc, id asc");
    }

    @Test
    @DisplayName("结转侧与读侧的收窄条件必须逐条同构（分家过一次，靠这条钉死）")
    void settleAndReadShareTheSameScope() throws Exception {
        String settle = selectSql(LocationStockMapper.class, "selectBasketsBySource", Long.class, Long.class);
        String readPerProduct = selectSql(LocationStockMapper.class, "selectPlotProductStocks",
            Long.class, Collection.class);
        String readPlotCard = selectSql(PlantingRecordMapper.class, "selectPlotDetailByCrop", Long.class);

        // 二轮事故：读侧改成只按 source_biz_id、结算侧仍按作物产品清单枚举 → 收口漏结、僵尸库存。
        // 三处必须同时具备这四个条件，任一处缺失都会让两侧范围再次分家。
        // 三处 SQL 的 `=` 空格风格不统一（del_flag='0' vs del_flag = '0'），这里二次归一后再比条件本身。
        String s1 = squeezeEquals(settle);
        String s2 = squeezeEquals(readPerProduct);
        String s3 = squeezeEquals(readPlotCard);
        for (String cond : new String[]{"source_biz_id", "third_phase=0", "product_id is not null", "del_flag='0'"}) {
            assertThat(s1).as("结转侧缺条件 %s", cond).contains(cond);
            assertThat(s2).as("分产品读侧缺条件 %s", cond).contains(cond);
            assertThat(s3).as("地块卡读侧缺条件 %s", cond).contains(cond);
        }
    }

    /** 去掉 `=` 两侧空格，抹平三处 SQL 的书写风格差异（只用于条件存在性比对）。 */
    private static String squeezeEquals(String sql) {
        return sql.replace(" = ", "=").replace("= ", "=").replace(" =", "=");
    }

    /** 读 Flyway 迁移脚本原文（归一化后小写），用于钉死幂等守卫。 */
    private static String migrationSql(String fileName) throws Exception {
        java.nio.file.Path path = java.nio.file.Path.of(
            "..", "..", "ruoyi-admin", "src", "main", "resources", "db", "migration", fileName);
        assertThat(java.nio.file.Files.exists(path))
            .as("迁移脚本 %s 应存在于 %s", fileName, path.toAbsolutePath()).isTrue();
        return normalize(java.nio.file.Files.readString(path));
    }
}
