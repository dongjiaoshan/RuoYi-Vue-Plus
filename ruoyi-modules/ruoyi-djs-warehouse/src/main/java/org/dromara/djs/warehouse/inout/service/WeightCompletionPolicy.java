package org.dromara.djs.warehouse.inout.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.dto.DictDataDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.djs.warehouse.inout.domain.vo.CompletionCheckVo;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

/** 可复用纯判定：燎毛为接收占出栏下限；分割为剩余占原入库上限。 */
@Service
@RequiredArgsConstructor
public class WeightCompletionPolicy {
    public static final String BURN_DICT = "djs_burn_yield_threshold";
    public static final String CUT_DICT = "djs_cut_yield_threshold";
    public static final String BURN_MESSAGE = "当前白条重量有误，请联系管理员处理。";
    public static final String CUT_MESSAGE = "请确认白条是否已分割完成。";
    private final DictService dictService;

    public CompletionCheckVo burn(BigDecimal receivedWeight, BigDecimal marketingWeight) {
        return evaluate(receivedWeight, marketingWeight, percentage(BURN_DICT), false, BURN_MESSAGE);
    }

    /** remainingWeight 与 originalInWeight 分别为剩余量及原始入库重，不能以领用重替代分母。 */
    public CompletionCheckVo cut(BigDecimal remainingWeight, BigDecimal originalInWeight) {
        return evaluate(remainingWeight, originalInWeight, percentage(CUT_DICT), true, CUT_MESSAGE);
    }

    public BigDecimal percentage(String dictType) {
        List<DictDataDTO> entries = dictService.getDictData(dictType);
        List<DictDataDTO> defaults = entries == null ? List.of() : entries.stream()
            .filter(e -> e != null && "Y".equals(e.getIsDefault())).toList();
        if (defaults.size() != 1) {
            throw new ServiceException("完成条件字典 " + dictType + " 必须配置且仅配置一个默认百分比");
        }
        String value = defaults.getFirst().getDictValue();
        try {
            BigDecimal percent = new BigDecimal(value == null ? "" : value.trim());
            validatePercentage(percent);
            return percent;
        } catch (NumberFormatException e) {
            throw new ServiceException("完成条件字典 " + dictType + " 的默认值必须是 0 至 100 的数字百分比");
        }
    }

    static CompletionCheckVo evaluate(BigDecimal actualWeight, BigDecimal referenceWeight,
                                      BigDecimal percent, boolean upperBound, String message) {
        if (referenceWeight == null || referenceWeight.signum() <= 0) {
            throw new ServiceException("完成判定所需的原始基准重量缺失或无效，请核对原始重量");
        }
        if (actualWeight == null || actualWeight.signum() < 0) {
            throw new ServiceException("完成判定重量缺失或为负，请核对出入库记录");
        }
        validatePercentage(percent);
        // movePointLeft 是十进制精确运算，避免比率除法或显示精度舍入改变边界。
        BigDecimal threshold = referenceWeight.multiply(percent).movePointLeft(2);
        int comparison = actualWeight.compareTo(threshold);
        boolean met = upperBound ? comparison <= 0 : comparison >= 0;
        return new CompletionCheckVo(!met, met ? "" : message, actualWeight, referenceWeight,
            percent, threshold, upperBound ? "LTE" : "GTE");
    }

    private static void validatePercentage(BigDecimal percent) {
        if (percent == null || percent.signum() < 0 || percent.compareTo(new BigDecimal("100")) > 0) {
            throw new ServiceException("完成条件字典的默认值必须是 0 至 100 的数字百分比");
        }
    }

    /** 只处理比例确认；状态、数量、上界及数据完整性均须先由业务服务独立验证。 */
    public static void requireConfirmation(CompletionCheckVo check, Boolean confirmed) {
        if (check.confirmationRequired() && !Boolean.TRUE.equals(confirmed)) {
            throw new ServiceException(check.message());
        }
    }
}
