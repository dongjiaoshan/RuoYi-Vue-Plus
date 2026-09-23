package org.dromara.djs.plant.common.util;

import org.dromara.djs.plant.market.util.MarketStatusLabel;
import org.dromara.djs.plant.pick.util.PickStatusLabel;
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
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 五档状态中文名跨端一致性（果蔬上市计划 + 采摘计划）。
 *
 * <p>状态中文有两处真源：页面走 plus-ui 的 {@code <页面>.status.*}（admin 可见字符串必须走 i18n），
 * 导出走后端的 Label 类（FastExcel 直接把中文写进单元格）。两边逐字相同才能保证「页面看到的」
 * 和「导出里的」是同一个词——改一处忘另一处不报错，只会让甲方看到两个词。这条测试就是那个报错。</p>
 *
 * <p>两页共用 {@link DateWindowStatusCalculator} 的同一套状态码，但中文各一套
 * （上市：待上市…；采摘：未到采摘期…），所以两组各校一遍。</p>
 *
 * <p>plus-ui 是并排的另一个仓库。后端仓库单独 checkout（CI）时读不到那个文件，
 * 此时 {@link Assumptions} 跳过而不是失败——跨仓校验只在两仓都在的开发机上生效。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@DisplayName("五档状态中文名前后端一致")
class StatusLabelI18nSyncTest {

    /** 从 ruoyi-djs-plant 模块目录出发的 plus-ui 中文包路径。 */
    private static final String ZH_CN = "../../../plus-ui/src/lang/zh_CN.ts";

    private static final Pattern ENTRY = Pattern.compile("(\\w+):\\s*'([^']*)'");

    @Test
    @DisplayName("果蔬上市计划：五档中文与 plus-ui marketPlan.status.* 逐字相同")
    void marketNamesMatchFrontendI18n() throws IOException {
        assertLabelsMatch("marketPlan", MarketStatusLabel::name);
    }

    @Test
    @DisplayName("采摘计划：五档中文与 plus-ui pickPlan.status.* 逐字相同")
    void pickNamesMatchFrontendI18n() throws IOException {
        assertLabelsMatch("pickPlan", PickStatusLabel::name);
    }

    /**
     * 校验某一页的 i18n status 段与后端 Label 逐字一致。
     *
     * @param pageKey   zh_CN.ts 里的页面 key（{@code marketPlan} / {@code pickPlan}）
     * @param backendFn 该页的后端状态码 → 中文名函数
     * @throws IOException 读 zh_CN.ts 失败
     */
    private void assertLabelsMatch(String pageKey, UnaryOperator<String> backendFn) throws IOException {
        Path zh = Path.of(ZH_CN);
        Assumptions.assumeTrue(Files.exists(zh), "plus-ui 不在旁边（后端仓库单独 checkout），跳过跨仓校验");

        // 只取该页 key 下的第一个 status 段，避免误命中别处同名的 status 块
        Pattern statusBlock = Pattern.compile(
            Pattern.quote(pageKey) + ":\\s*\\{.*?status:\\s*\\{(.*?)}", Pattern.DOTALL);
        Matcher block = statusBlock.matcher(Files.readString(zh, StandardCharsets.UTF_8));
        assertThat(block.find()).as("plus-ui zh_CN.ts 里找不到 %s.status 段", pageKey).isTrue();

        Map<String, String> frontend = new LinkedHashMap<>();
        Matcher entry = ENTRY.matcher(block.group(1));
        while (entry.find()) {
            frontend.put(entry.group(1), entry.group(2));
        }

        Map<String, String> backend = new LinkedHashMap<>();
        for (String code : DateWindowStatusCalculator.CODES) {
            backend.put(code, backendFn.apply(code));
        }

        assertThat(frontend)
            .as("页面(zh_CN.ts %s.status.*) 与 导出(后端 Label) 的状态中文必须逐字一致，"
                + "且五档一一对应；改了一边就得改另一边", pageKey)
            .containsExactlyInAnyOrderEntriesOf(backend);
    }
}
