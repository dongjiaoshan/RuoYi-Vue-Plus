package org.dromara.djs.warehouse.pigbuy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.warehouse.pigbuy.domain.PigPurchase;
import org.dromara.djs.warehouse.pigbuy.domain.bo.PigPurchaseBo;
import org.dromara.djs.warehouse.pigbuy.domain.query.PigPurchaseQuery;
import org.dromara.djs.warehouse.pigbuy.domain.vo.PigPurchaseVo;
import org.dromara.djs.warehouse.pigbuy.mapper.PigPurchaseMapper;
import org.dromara.djs.warehouse.pigbuy.service.IPigPurchaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 外购猪只到货登记 Service 实现（FIX-WMS-MP-PIGBUY-001）。
 *
 * <h3>登记流程</h3>
 * <p>{@link #submitPurchase} 单 {@code @Transactional}：生成 {@code purchaseNo}（{@code PBUY+yyMMdd+4}）
 * → 置 {@code purchaseStatus=pending} + {@code operatorId}=登录人 → INSERT。本卡仅登记，不联动库存 /
 * stock_flow（外购猪只进燎毛 / 分割流的合流留 follow-up，不改 burn 文件）。</p>
 *
 * <h3>幂等</h3>
 * <p>{@code purchase_no} UNIQUE (tenant_id, purchase_no, del_unique)；inline {@code selectMaxPurchaseNoByDate}
 * + 序号 max+1，事务内串行 + UNIQUE 兜底（避开共享 BizCodeType 枚举，本子域独立编号）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-PIGBUY-001
 */
@Slf4j
@Service
public class PigPurchaseServiceImpl
    extends DjsBaseServiceImpl<PigPurchaseMapper, PigPurchase>
    implements IPigPurchaseService {

    /**
     * 业务码前缀。
     */
    private static final String PURCHASE_NO_PREFIX = "PBUY";

    /**
     * 业务码日期段格式 {@code yyMMdd}。
     */
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * 字典 djs_pig_purchase_status 值：待处理（登记初始态）。
     */
    private static final String STATUS_PENDING = "pending";

    public PigPurchaseServiceImpl(PigPurchaseMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitPurchase(PigPurchaseBo bo) {
        PigPurchase record = toEntity(bo);
        if (record == null) {
            throw new ServiceException("外购猪只到货入参转换失败");
        }
        record.setPurchaseNo(generatePurchaseNo());
        record.setPurchaseStatus(STATUS_PENDING);
        // 登记人 = 当前登录态（mp sa-token 真路，ADR-0003 / ADR-0007）
        record.setOperatorId(LoginHelper.getUserId());
        baseMapper.insert(record);
        return record.getId();
    }

    @Override
    public TableDataInfo<PigPurchaseVo> queryPageList(PigPurchaseQuery query, PageQuery pageQuery) {
        Page<PigPurchaseVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(query));
        return TableDataInfo.build(page);
    }

    @Override
    public List<PigPurchaseVo> queryList(PigPurchaseQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public PigPurchaseVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * BO → Entity 转换钩子（MapStruct-Plus）。protected 方便单测覆盖。
     */
    protected PigPurchase toEntity(PigPurchaseBo bo) {
        return MapstructUtils.convert(bo, PigPurchase.class);
    }

    /**
     * 生成 {@code purchaseNo}：{@code PBUY+yyMMdd+4 位}（本子域 inline，UNIQUE 兜底）。
     *
     * <p>protected 方便单测 stub 固定返回值。</p>
     */
    protected String generatePurchaseNo() {
        String yyMMdd = LocalDate.now().format(YYMMDD);
        String max = baseMapper.selectMaxPurchaseNoByDate(yyMMdd);
        int next = 1;
        if (StringUtils.isNotBlank(max) && max.length() >= 4) {
            try {
                next = Integer.parseInt(max.substring(max.length() - 4)) + 1;
            }
            catch (NumberFormatException e) {
                log.warn("解析 purchase_no 序号失败，回落序号 1：{}", max);
            }
        }
        return PURCHASE_NO_PREFIX + yyMMdd + String.format("%04d", next);
    }

    private LambdaQueryWrapper<PigPurchase> buildQueryWrapper(PigPurchaseQuery query) {
        LambdaQueryWrapper<PigPurchase> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PigPurchase::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getSourceType()), PigPurchase::getSourceType, query.getSourceType())
            .eq(StringUtils.isNotBlank(query.getPurchaseStatus()), PigPurchase::getPurchaseStatus, query.getPurchaseStatus())
            .eq(query.getOperatorId() != null, PigPurchase::getOperatorId, query.getOperatorId())
            .ge(query.getArriveTimeFrom() != null, PigPurchase::getArriveTime, query.getArriveTimeFrom())
            .le(query.getArriveTimeTo() != null, PigPurchase::getArriveTime, query.getArriveTimeTo())
            .orderByDesc(PigPurchase::getId);
        return wrapper;
    }

}
