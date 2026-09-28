package org.dromara.djs.warehouse.inout.service;

import org.dromara.common.core.domain.dto.DictDataDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("local") @Tag("dev")
class WeightCompletionPolicyTest {
    DictService dict = mock(DictService.class);
    WeightCompletionPolicy policy = new WeightCompletionPolicy(dict);
    static DictDataDTO entry(String value, String defaultFlag) {
        var item = new DictDataDTO(); item.setDictValue(value); item.setIsDefault(defaultFlag); return item;
    }
    void configure(String type, String value) { when(dict.getDictData(type)).thenReturn(List.of(entry(value,"Y"))); }

    @ParameterizedTest
    @CsvSource({"49.999,100,true", "50,100,false", "50.001,100,false", "0.0005,0.001,false", "0.0004999,0.001,true"})
    void burnMinimumUsesExactInclusiveBoundary(String actual, String original, boolean confirmation) {
        configure(WeightCompletionPolicy.BURN_DICT,"50");
        var result=policy.burn(new BigDecimal(actual),new BigDecimal(original));
        assertThat(result.confirmationRequired()).isEqualTo(confirmation);
        assertThat(result.comparison()).isEqualTo("GTE");
        assertThat(result.message()).isEqualTo(confirmation ? WeightCompletionPolicy.BURN_MESSAGE : "");
    }
    @ParameterizedTest
    @CsvSource({"19.999,40,false", "20,40,false", "20.001,40,true", "0.0005,0.001,false", "0.0005001,0.001,true"})
    void cutMaximumUsesExactInclusiveBoundary(String remaining, String original, boolean confirmation) {
        configure(WeightCompletionPolicy.CUT_DICT,"50");
        var result=policy.cut(new BigDecimal(remaining),new BigDecimal(original));
        assertThat(result.confirmationRequired()).isEqualTo(confirmation);
        assertThat(result.comparison()).isEqualTo("LTE");
        assertThat(result.message()).isEqualTo(confirmation ? WeightCompletionPolicy.CUT_MESSAGE : "");
    }
    @Test void modifiedDictionaryIsReadForEachAssessmentWithoutRounding() {
        configure(WeightCompletionPolicy.CUT_DICT,"50");
        assertThat(policy.cut(new BigDecimal("1"),new BigDecimal("3")).confirmationRequired()).isFalse();
        configure(WeightCompletionPolicy.CUT_DICT,"33.333");
        var result=policy.cut(new BigDecimal("1"),new BigDecimal("3"));
        assertThat(result.confirmationRequired()).isTrue();
        assertThat(result.thresholdWeight()).isEqualByComparingTo("0.99999");
    }
    @Test void percentageEndpointsZeroAndHundredHaveDefinedExactMeaning() {
        configure(WeightCompletionPolicy.CUT_DICT,"0");
        assertThat(policy.cut(BigDecimal.ZERO,BigDecimal.TEN).confirmationRequired()).isFalse();
        assertThat(policy.cut(new BigDecimal("0.001"),BigDecimal.TEN).confirmationRequired()).isTrue();
        configure(WeightCompletionPolicy.CUT_DICT,"100");
        assertThat(policy.cut(BigDecimal.TEN,BigDecimal.TEN).confirmationRequired()).isFalse();
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"text","50%","-1","100.001","NaN"})
    void malformedOrOutOfRangeConfigurationFails(String value) {
        configure(WeightCompletionPolicy.CUT_DICT,value);
        assertThatThrownBy(() -> policy.cut(BigDecimal.ONE,BigDecimal.TEN))
            .isInstanceOf(ServiceException.class).hasMessageContaining("0 至 100");
    }
    @Test void missingDefaultAndAmbiguousDefaultsAreConfigurationErrors() {
        when(dict.getDictData(WeightCompletionPolicy.CUT_DICT)).thenReturn(null, List.of(),
            List.of(entry("50","N")), List.of(entry("50","Y"),entry("60","Y")));
        for(int i=0;i<4;i++) {
            assertThatThrownBy(() -> policy.cut(BigDecimal.ONE,BigDecimal.TEN)).hasMessageContaining("仅配置一个默认百分比");
        }
    }
    @Test void invalidReferenceAndNegativeRemainingAreNeverConfirmableRatioExceptions() {
        configure(WeightCompletionPolicy.CUT_DICT,"50");
        assertThatThrownBy(() -> policy.cut(BigDecimal.ONE,null)).hasMessageContaining("基准重量");
        assertThatThrownBy(() -> policy.cut(BigDecimal.ONE,BigDecimal.ZERO)).hasMessageContaining("基准重量");
        assertThatThrownBy(() -> policy.cut(new BigDecimal("-0.001"),BigDecimal.TEN)).hasMessageContaining("为负");
    }
    @Test void onlyExplicitTrueAcknowledgesAnAbnormalRatio() {
        configure(WeightCompletionPolicy.CUT_DICT,"50");
        var result=policy.cut(new BigDecimal("6"),BigDecimal.TEN);
        assertThatThrownBy(() -> WeightCompletionPolicy.requireConfirmation(result,null)).hasMessage(WeightCompletionPolicy.CUT_MESSAGE);
        assertThatThrownBy(() -> WeightCompletionPolicy.requireConfirmation(result,false)).hasMessage(WeightCompletionPolicy.CUT_MESSAGE);
        assertThatCode(() -> WeightCompletionPolicy.requireConfirmation(result,true)).doesNotThrowAnyException();
    }
}
