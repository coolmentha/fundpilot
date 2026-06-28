package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

/**
 * 基金类型统一识别器:按基金名称一次性识别两个正交维度的分类(CONTEXT.md「基金类型自动识别」)。
 * <p>组合 {@link FundSubTypeClassifier}(数据源维度)与 {@link FundCategoryClassifier}(策略参数维度),
 * 落 {@code fund_dict} 表时调用,搜索返回的候选自带分类,避免运行时重复识别。
 *
 * <h3>识别规则</h3>
 * <ol>
 *   <li>先调 {@link FundSubTypeClassifier#classify(String)} 得 fundSubType + benchmarkIndexCode
 *       (ETF/指数增强/指数/兜底 ACTIVE)</li>
 *   <li>再调 {@link FundCategoryClassifier#classify(String, FundSubType)} 得 fundCategory
 *       (宽基/行业/主动/混合)</li>
 * </ol>
 * 两个分类器共用 {@link BenchmarkIndexTable} 的宽基指数词,但 fundCategory 额外引入行业词表,
 * 与 fundSubType 的识别规则正交(见 CONTEXT.md「基金子类型」的 _Avoid_:两者不可合并)。
 */
public final class FundTypeClassifier {

    private FundTypeClassifier() {
    }

    /**
     * 统一识别两个维度。
     *
     * @param fundName 基金名称(如 易方达沪深300ETF联接A)
     * @return 三值结果(fundSubType + fundCategory + benchmarkIndexCode);入参为空时兜底 ACTIVE + 主动 + 沪深300
     */
    public static FundTypeClassification classify(String fundName) {
        FundSubTypeResult subResult = FundSubTypeClassifier.classify(fundName);
        FundCategory category = FundCategoryClassifier.classify(fundName, subResult.fundSubType());
        return new FundTypeClassification(subResult.fundSubType(), category, subResult.benchmarkIndexCode());
    }
}
