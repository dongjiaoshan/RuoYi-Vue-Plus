package org.dromara.djs.common.job;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * djs 定时任务注册表（DENGBO row7）。
 *
 * <p>各 job 类在 {@code @PostConstruct} 里把自己的「重算逻辑」按 jobName 注册进来
 * （{@code Consumer<LocalDate>}，入参为重算目标日，无日期 job 忽略该参数）。
 * admin「定时任务重跑」页据此列出可重跑 job 名 + 按日期范围逐日调用对应 Consumer。</p>
 *
 * <p>注册逻辑与 {@code @Scheduled} 触发同源（共用同一段重算代码），定时与手动重跑行为一致。</p>
 *
 * @author djs
 * @since DENGBO-R7
 */
@Component
public class DjsJobRegistry {

    /** jobName → 重算逻辑（入参为目标日，无日期 job 忽略）。 */
    private final Map<String, Consumer<LocalDate>> registry = new ConcurrentHashMap<>();

    /**
     * 注册一个可重跑 job；同名后注册覆盖前者。
     *
     * @param jobName job 名（与 {@code @Scheduled} 日志名一致）
     * @param task    重算逻辑（入参为目标日，可忽略）
     */
    public void register(String jobName, Consumer<LocalDate> task) {
        registry.put(jobName, task);
    }

    /**
     * 取指定 job 的重算逻辑；未注册返回 null。
     */
    public Consumer<LocalDate> get(String jobName) {
        return registry.get(jobName);
    }

    /**
     * 是否已注册指定 job。
     */
    public boolean contains(String jobName) {
        return registry.containsKey(jobName);
    }

    /**
     * 已注册的全部 job 名（升序、不可变）。
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(new TreeSet<>(registry.keySet()));
    }

}
