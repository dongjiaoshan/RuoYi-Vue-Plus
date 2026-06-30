package org.dromara.djs.common.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.job.domain.DjsJobLog;
import org.dromara.djs.common.job.domain.bo.DjsJobLogQuery;
import org.dromara.djs.common.job.domain.vo.DjsJobLogVo;
import org.dromara.djs.common.job.mapper.DjsJobLogMapper;
import org.dromara.djs.common.job.service.IDjsJobLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * djs 定时任务执行日志 Service 实现（DENGBO row7）。
 *
 * <p>纯审计表，无软删 CRUD 业务语义，直接走 {@link DjsJobLogMapper}（{@code BaseMapperPlus}）。
 * 落日志全为旁路操作，{@link org.dromara.djs.common.job.DjsJobRunner} 已对调用做 try/catch
 * 兜底，本类不额外吞异常。</p>
 *
 * @author djs
 * @since DENGBO-R7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DjsJobLogServiceImpl implements IDjsJobLogService {

    /** error_msg 列长度上限，超出截断。 */
    private static final int MAX_ERROR_LEN = 1000;

    private final DjsJobLogMapper baseMapper;

    @Override
    public TableDataInfo<DjsJobLogVo> queryPageList(DjsJobLogQuery query, PageQuery pageQuery) {
        Page<DjsJobLogVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(query));
        return TableDataInfo.build(page);
    }

    @Override
    public Long recordStart(String jobName, LocalDate targetDate, String triggerType) {
        DjsJobLog entity = new DjsJobLog();
        entity.setJobName(jobName);
        entity.setTargetDate(targetDate);
        entity.setStatus("running");
        entity.setTriggerType(triggerType);
        entity.setRunTime(LocalDateTime.now());
        baseMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void recordDone(Long logId, long costMs) {
        if (logId == null) {
            return;
        }
        DjsJobLog entity = new DjsJobLog();
        entity.setId(logId);
        entity.setStatus("success");
        entity.setCostMs(costMs);
        baseMapper.updateById(entity);
    }

    @Override
    public void recordFail(Long logId, long costMs, String error) {
        if (logId == null) {
            return;
        }
        DjsJobLog entity = new DjsJobLog();
        entity.setId(logId);
        entity.setStatus("fail");
        entity.setCostMs(costMs);
        entity.setErrorMsg(truncate(error));
        baseMapper.updateById(entity);
    }

    /**
     * 构造查询条件：job_name eq / status eq / trigger_type eq，按 run_time 倒序。
     */
    private LambdaQueryWrapper<DjsJobLog> buildQueryWrapper(DjsJobLogQuery query) {
        LambdaQueryWrapper<DjsJobLog> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.eq(StringUtils.isNotBlank(query.getJobName()), DjsJobLog::getJobName, query.getJobName())
                .eq(StringUtils.isNotBlank(query.getStatus()), DjsJobLog::getStatus, query.getStatus())
                .eq(StringUtils.isNotBlank(query.getTriggerType()), DjsJobLog::getTriggerType, query.getTriggerType());
        }
        return wrapper.orderByDesc(DjsJobLog::getRunTime).orderByDesc(DjsJobLog::getId);
    }

    /**
     * 截断错误信息到 {@link #MAX_ERROR_LEN}，null 安全。
     */
    private String truncate(String error) {
        if (Objects.isNull(error)) {
            return null;
        }
        return error.length() > MAX_ERROR_LEN ? error.substring(0, MAX_ERROR_LEN) : error;
    }

}
