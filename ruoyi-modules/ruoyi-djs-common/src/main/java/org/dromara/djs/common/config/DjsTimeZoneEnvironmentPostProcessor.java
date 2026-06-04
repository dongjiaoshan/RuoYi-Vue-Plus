package org.dromara.djs.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.TimeZone;

/**
 * 全局时区收口：强制 JVM 默认时区为 {@code Asia/Shanghai}。
 *
 * <h3>为什么需要</h3>
 * <p>ruoyi {@code JacksonConfig} 用 {@code builder.timeZone(TimeZone.getDefault())} 序列化
 * {@code java.util.Date}（BaseEntity 的 {@code createTime / updateTime} 等）。当运行 JVM 的默认
 * 时区是 UTC（{@code mvn spring-boot:run} fork 子进程未继承系统时区、容器无 TZ 等场景）时，所有
 * {@code Date} 列在前端会少 8 小时显示（DB 存的本地值是对的，纯序列化偏移）；而 {@code LocalDateTime}
 * 业务字段按墙钟序列化不受影响 —— 于是同一行"创建时间"错、"业务日期"对，极具迷惑性。</p>
 *
 * <h3>为什么这样修</h3>
 * <ul>
 *   <li>{@code spring.jackson.time-zone} 无效：会被 ruoyi 上述 customizer 覆盖。</li>
 *   <li>自定义 Jackson customizer 无法可靠排在 ruoyi 之后（其无 {@code @Order} = 最低优先级，并列顺序不确定）。</li>
 *   <li>故在 {@link EnvironmentPostProcessor} 阶段（早于任何 Bean / ObjectMapper 构建）显式
 *       {@link TimeZone#setDefault} 收口 —— dev + prod 一致，不依赖 {@code -Duser.timezone} 启动参数。</li>
 * </ul>
 *
 * <p>合规：系统单区域（中国）运营，JDBC {@code serverTimezone=GMT+8 / Asia/Shanghai} 与之对齐、无 DST，
 * 设默认时区无副作用。</p>
 */
public class DjsTimeZoneEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 业务统一时区。 */
    private static final String ZONE_ID = "Asia/Shanghai";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!ZONE_ID.equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone(ZONE_ID));
        }
    }

    @Override
    public int getOrder() {
        // 尽量早，先于其它依赖时区的环境处理
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
