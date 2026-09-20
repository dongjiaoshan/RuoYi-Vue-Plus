package org.dromara.djs.breed;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全模块扫描：凡是被 {@code <script>} 包着的注解 SQL，必须是良构 XML。
 *
 * <p><b>为什么非要有这一支</b>：{@code <script>} 里的 SQL 会被 MyBatis 走 {@code XMLLanguageDriver}
 * → {@code XPathParser} 真当 XML 解析，而这件事发生在 <b>Spring 启动期建 mapper bean 的时候</b>，
 * 不是执行 SQL 的时候。所以：mock 掉 mapper 的单测测不到它、不连数据库也测不到它，
 * 整个模块 451 个单测可以全绿，一部署容器起不来。</p>
 *
 * <p>2026-09-20 就这么炸过一次：{@code cub.pig_type <> 'piglet'} 里的 {@code <} 被当成标签起始，
 * staging 整个后端 SAXParseException 崩溃重启循环。SQL 里要写不等于，在 {@code <script>} 中一律用
 * {@code !=}；确实要用 {@code <} 比大小时写 {@code &lt;} 或包 {@code <![CDATA[ ]]>}。</p>
 *
 * <p>扫 {@code target/classes} 下所有 mapper，不写死类名 —— 写死的话新加的 mapper 天然在保护之外。</p>
 *
 * @author djs
 */
@Tag("dev")
class MapperScriptXmlWellFormedTest {

    private static final String CLASSES_DIR = "target/classes";
    private static final String PKG_PREFIX = "org.dromara.djs.breed.";

    @Test
    @DisplayName("所有 <script> 注解 SQL 都是良构 XML —— 破了就是启动期崩容器，单测全绿也救不了")
    void allScriptAnnotationsAreWellFormedXml() throws Exception {
        List<String> mappers = scanMapperClassNames();
        assertThat(mappers)
            .as("一个 mapper 都没扫到 = 扫描路径失效，这支测试等于没跑")
            .isNotEmpty();

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

        List<String> broken = new ArrayList<>();
        int checked = 0;
        for (String fqcn : mappers) {
            Class<?> clazz = Class.forName(fqcn);
            for (Method m : clazz.getDeclaredMethods()) {
                for (String sql : sqlOf(m)) {
                    if (!sql.stripLeading().startsWith("<script>")) {
                        continue;
                    }
                    checked++;
                    try {
                        builder.parse(new InputSource(new StringReader(sql)));
                    } catch (Exception e) {
                        broken.add(clazz.getSimpleName() + "#" + m.getName() + " → " + e.getMessage());
                    }
                }
            }
        }
        assertThat(broken)
            .as("这些 <script> SQL 不是良构 XML，MyBatis 建 bean 时会抛 SAXParseException 直接崩启动"
                + "（最常见原因：写了 <> 或裸 <，改成 != 或 &lt; 或包 CDATA）")
            .isEmpty();
        assertThat(checked)
            .as("一个 <script> 都没扫到 —— 要么本模块确实没有，要么扫描坏了；坏了的话这支测试是摆设")
            .isGreaterThan(0);
    }

    /** 取方法上四种 SQL 注解的原文（按 MyBatis 的拼法，多行数组直接相连）。 */
    private static List<String> sqlOf(Method m) {
        List<String> out = new ArrayList<>();
        for (Annotation a : m.getAnnotations()) {
            if (a instanceof Select s) {
                out.add(String.join(" ", s.value()));
            } else if (a instanceof Update u) {
                out.add(String.join(" ", u.value()));
            } else if (a instanceof Insert i) {
                out.add(String.join(" ", i.value()));
            } else if (a instanceof Delete d) {
                out.add(String.join(" ", d.value()));
            }
        }
        return out;
    }

    /** 走 target/classes 目录还原 FQCN，不写死清单 —— 新加的 mapper 自动进保护。 */
    private static List<String> scanMapperClassNames() throws Exception {
        Path root = Path.of(CLASSES_DIR);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith("Mapper.class"))
                .map(p -> root.relativize(p).toString()
                    .replace(java.io.File.separatorChar, '.')
                    .replaceAll("\\.class$", ""))
                .filter(n -> n.startsWith(PKG_PREFIX))
                .sorted()
                .toList();
        }
    }
}
