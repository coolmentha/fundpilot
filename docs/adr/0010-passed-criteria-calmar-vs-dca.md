# passed 判定：回撤维度从绝对值约束改为 Calmar 比较

`BenchmarkCalculator.judgePassed` 的回撤约束原为**绝对值**（策略最大回撤 ≤ dca 最大回撤，见 ADR-0009）。
本次变更为**相对值**：策略风险调整收益（Calmar = 收益/最大回撤）须 ≥ dca 的 Calmar。收益门槛不变（策略收益严格 > hs300 ∧ > dca）。
all-in 仍退出判定，仅落库展示。判定口径全链路统一——calibrate 主路径与寻优 test 集共用同一个 `judgePassed`。

## Considered Options

- **A. 回撤约束改为策略 Calmar ≥ dca Calmar（已采纳）**：收益须 > hs300 ∧ > dca；策略 Calmar 须 ≥ dca Calmar。
- **B. 保留绝对回撤约束（ADR-0009 现状）**：收益须 > hs300 ∧ > dca；策略回撤须 ≤ dca 回撤。
- **C. 绝对回撤 + Calmar 双约束**：两者都要求。绝对约束已卡死合理策略,加 Calmar 救不回来,等于没改。

## Consequences

选 A 的核心理由：**"回撤对得起收益"是比值语义,不是绝对值语义**。

1. **绝对回撤约束惩罚合理风险换合理收益**——基金 1958 实战数据揭示:
   策略收益 19.72% / 回撤 9.68%,dca 收益 15.07% / 回撤 8.84%。策略用 0.84% 的额外回撤换了 4.65% 的额外收益,
   风险调整后明显更优(Calmar 2.04 vs 1.70),但绝对回撤 9.68% > 8.84% 判 false。
   一个收益 50%/回撤 12% vs dca 15%/8% 的策略更极端——明显更优却被判失败。
   绝对约束把"任何超额回撤"都视为失败,而超额回撤配得上超额收益时本该通过。
2. **Calmar 是"回撤对得起收益"的直接数学化**——单位回撤换的收益,与 CONTEXT.md「风险调整收益」
   (策略收益/策略最大回撤,类 Calmar)完全同源。train 集排序用 Calmar 选最优,test 集验证用 Calmar 比 dca,
   择优标尺和验证标尺一致,逻辑闭环。策略 Calmar ≥ dca Calmar 即"策略单位回撤换的收益不劣于定投"。
3. **保留"策略收益 > dca"收益门槛**——绝对收益仍要赢定投(有 alpha),但回撤维度从"不能超"放宽到
   "风险调整后不能差"。这正好实现"接受超额回撤,但要配得上超额收益":策略可输 dca 回撤,只要单位回撤效率更高。
4. **不选 C**——绝对回撤约束已在惩罚合理策略,叠加 Calmar 无法救回(1958 仍因回撤超 dca 判 false)。

## 除零兜底

dca 回撤为 0(单调上涨窗口)时 dcaCalmar 视为 +∞(代码用 `null` 表示):零回撤换正收益风险调整后无敌,
策略有任何回撤就不如定投。策略回撤也为 0 时双方都 +∞,临界(≥)通过。兜底逻辑在
`BenchmarkCalculator.calmarRatio`(返 null 表 +∞)与 `judgePassed`/`validate` 的三态比较中一致实现。

## 代价

判定口径再次变更,**历史回测记录的 passed 值不重算**(同 ADR-0009 的留痕原则)。
原本因"回撤 ≤ dca"判 true 的记录,若策略 Calmar < dca Calmar,按新标准本该 false;
原本因"回撤超 dca"判 false 的记录,若策略 Calmar ≥ dca Calmar,按新标准本该 true。
这些历史值保持不动作为历史事实;新判定只影响今后跑的回测。

`judgePassed` 签名保留 allIn 参数(调用方仍计算并落库展示),内部不使用——维持调用方契约,
避免无领域收益的签名扩散改动,未来若调用方重构可一并清理。

## 演进

本 ADR supersede [ADR-0009](./0009-passed-criteria-drop-allin-return-and-use-dca-drawdown.md) 的回撤标尺部分。
ADR-0009 的"收益门槛去 all-in"决策不变(仍生效),仅其"回撤标尺挂 dca 绝对值"被本 ADR 的 Calmar 比较取代。
