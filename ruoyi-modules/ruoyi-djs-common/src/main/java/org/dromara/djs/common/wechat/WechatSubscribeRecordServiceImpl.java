package org.dromara.djs.common.wechat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link IWechatSubscribeRecordService} 实现（SYS-INFRA-008）。
 *
 * <p>重复授权走 UPDATE 而非 INSERT，避免一个用户对同一 template 累积多条 used=0 行。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatSubscribeRecordServiceImpl implements IWechatSubscribeRecordService {

    private final WechatSubscribeRecordMapper recordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertBo(WechatSubscribeRecordBo bo) {
        // 查是否已有同 (user_id, template_id, used=0) 行 —— 用户重复点订阅按钮场景
        WechatSubscribeRecord existing = recordMapper.selectOne(
            Wrappers.<WechatSubscribeRecord>lambdaQuery()
                .eq(WechatSubscribeRecord::getUserId, bo.getUserId())
                .eq(WechatSubscribeRecord::getTemplateId, bo.getTemplateId())
                .eq(WechatSubscribeRecord::getUsed, 0)
                .last("LIMIT 1")
        );

        int alwaysKeepFlag = Boolean.TRUE.equals(bo.getAlwaysKeep()) ? 1 : 0;

        if (existing != null) {
            // UPDATE 分支：覆盖授权时间 / 过期时间 / always_keep / openid
            existing.setSubscribedAt(bo.getSubscribedAt());
            existing.setExpiredAt(bo.getExpiredAt());
            existing.setAlwaysKeep(alwaysKeepFlag);
            existing.setOpenid(bo.getOpenid());
            recordMapper.updateById(existing);
            log.info("[subscribe-record] UPDATE existing record id={} userId={} templateId={} alwaysKeep={}",
                existing.getId(), bo.getUserId(), bo.getTemplateId(), alwaysKeepFlag);
            return existing.getId();
        }

        // INSERT 分支：新雪花 ID，create_time 显式赋值（DDL NOT NULL 无 default）
        WechatSubscribeRecord rec = new WechatSubscribeRecord();
        rec.setUserId(bo.getUserId());
        rec.setOpenid(bo.getOpenid());
        rec.setTemplateId(bo.getTemplateId());
        rec.setSubscribedAt(bo.getSubscribedAt());
        rec.setExpiredAt(bo.getExpiredAt());
        rec.setUsed(0);
        rec.setAlwaysKeep(alwaysKeepFlag);
        rec.setCreateTime(LocalDateTime.now());
        recordMapper.insert(rec);
        log.info("[subscribe-record] INSERT new record id={} userId={} templateId={} alwaysKeep={}",
            rec.getId(), bo.getUserId(), bo.getTemplateId(), alwaysKeepFlag);
        return rec.getId();
    }
}
