package org.dromara.djs.plant.market.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上市状态中文名跨端一致性（V6-R158）。
 *
 * <p>状态中文有两处真源：页面走 plus-ui 的 {@code marketPlan.status.*}（admin 可见字符串必须走 i18n），
 * 导出走后端 {@link MarketStatusCalculator#name(String)}（FastExcel 直接把中文写进单元格）。
 * 两边逐字相同才能保证「页面看到的」和「导出里的」是同一个词——改一处忘另一处不报错，
 * 只会让甲方看到两个词。这条测试就是那个报错。</p>
 *
 * <p>甲方大概率还会来统一「下市 / 下架」用词（列头叫下架日期、状态叫即将下市，两个词都逐字来自甲方原文），
 * 那次改动正好会踩到这个坑，所以这条守的是一次很可能发生的编辑。</p>
 *
 * <p>plus-ui 是并排的另一个仓库。后端仓库单独 checkout（CI）时读不到那个文件，
 * 此时 {@link Assumptions} 跳过而不是失败——跨仓校验只在两仓都在的开发机上生效。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("上市状态中文名前后端一致")
class MarketStatusLabelSyncTest {

    /** 从 ruoyi-djs-plant 模块目录出发的 plus-ui 中文包路径。 */
    private static final String ZH_CN = "../../../plus-ui/src/lang/zh_CN.ts";

    /** 只取 marketPlan 下的 status 段，避免误命中别处同名的 status 块。 */
    private static final Pattern STATUS_BLOCK =
        Pattern.compile("marketPlan:\\s*\\{.*?status:\\s*\\{(.*?)}", Pattern.DOTALL);

    private static final Pattern ENTRY = Pattern.compile("(\\w+):\\s*'([^']*)'");

    @Test
    @DisplayName("五档中文与 plus-ui marketPlan.status.* 逐字相同")
    void backendNamesMatchFrontendI18n() throws IOException {
        Path zh = Path.of(ZH_CN);
        Assumptions.assumeTrue(Files.exists(zh), "plus-ui 不在旁边（后端仓库单独 checkout），跳过跨仓校验");

        Matcher block = STATUS_BLOCK.matcher(Files.readString(zh, StandardCharsets.UTF_8));
        assertThat(block.find()).as("plus-ui zh_CN.ts 里找不到 marketPlan.status 段").isTrue();

        Map<String, String> frontend = new LinkedHashMap<>();
        Matcher entry = ENTRY.matcher(block.group(1));
        while (entry.find()) {
            frontend.put(entry.group(1), entry.group(2));
        }

        Map<String, String> backend = new LinkedHashMap<>();
        for (String code : new String[]{
            MarketStatusCalculator.PENDING, MarketStatusCalculator.UPCOMING,
            MarketStatusCalculator.ON_SALE, MarketStatusCalculator.ENDING,
            MarketStatusCalculator.OFF_SHELF}) {
            backend.put(code, MarketStatusCalculator.name(code));
        }

        assertThat(frontend)
            .as("页面(zh_CN.ts marketPlan.status.*) 与 导出(MarketStatusCalculator.name) 的状态中文必须逐字一致，"
                + "且档位一一对应；改了一边就得改另一边")
            .containsExactlyInAnyOrderEntriesOf(backend);
    }
}
