# 寻优验证口径与落库状态分离

寻优在 test 集（1 年回测窗口的后 0.3 年）用 passed 判定验证泛化，但落库走标准 `calibrate`（1 年全窗口回测）。
两者窗口不同，可能出现"寻优成功但全窗口未通过（`CALIBRATION_FAILED`）"——这是寻优与校准职责分离的正常结果，非 bug。

## Considered Options

- **A. 接受矛盾，落库走标准 calibrate（已采纳）**：寻优在 test 集（0.3 年）达标即成功，随后 `createDraft` + `calibrate`
  走 1 年全窗口回测留痕。可能出现"寻优成功但全窗口 `CALIBRATION_FAILED`"。前端用"寻优完成"措辞（不承诺"已通过"）消化。
- **B. 寻优绕过 calibrate，直接写 test 集结果**：寻优成功后手动写一条 `passed=true` 的 `StrategyBacktestEntity`（test 集结果）
  + 置 `CALIBRATED`，跳过 calibrate 的全窗口回测。
- **C. calibrate 窗口改为 test 集窗口**：把 `calibrate` 的固定窗口从 1 年改成 0.3 年，与寻优 test 集对齐。

## Consequences

选 A 的核心理由：**寻优与校准职责分离，留痕口径统一**。

1. 职责分离——寻优的职责是"生成有泛化能力的候选参数"（test 集达标即证明泛化），`calibrate` 的职责是
   "用统一固定窗口留痕验证"。两者窗口不同是各自职责的必然结果，不是缺陷。test 集达标说明参数有泛化能力，
   全窗口未通过说明这段历史整体难做，用户据此判断是否采纳——矛盾本身是"信息"。
2. 留痕口径统一——`calibrate` 跑的回测是 1 年窗口，与所有现有回测记录（手动复跑、历史 calibrate）可比。
   `activate` 的 `existsByPassedTrue` 检查也基于同一窗口口径。
3. 不破坏现有约定——`BACKTEST_WINDOW_DAYS=365` 不变，`calibrate`/`backtest` 逻辑不动，寻优只复用纯计算段
   （`BacktestSimulator.simulate` + `BenchmarkCalculator`）。

不选 B：留痕回测是 0.3 年窗口，与现有 1 年窗口回测记录不可比；且 `activate` 查 `existsByPassedTrue` 时，
test 集的 passed 与全窗口 passed 口径分裂，激活前置条件语义混乱。
不选 C：破坏 `calibrate` 固定 1 年约定，所有现有回测记录（1 年口径）与新记录（0.3 年口径）不再可比，
全局面板切换代价过大。

## 代价

状态可能割裂——寻优成功（test 达标）但落库 `CALIBRATION_FAILED`（全窗口未通过）时，用户看到"寻优完成"提示
但列表是红色"未通过"标签。前端措辞用"寻优完成"（不承诺"已通过"）消化，用户在回测历史里可看到全窗口回测的
实际收益/回撤/passed，据此决定是否手动调参重试或忽略该版本。
