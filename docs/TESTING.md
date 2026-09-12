# 构建与测试

## 环境

- JDK 8 或更高版本；`javac` 和 `java` 应在 PATH 中。
- PowerShell 7 或 Windows PowerShell 5.1。
- 可选：系统 Ant，用于验证标准 `build.xml`。

## 快速验证

```powershell
./verify-responsive.ps1
```

没有 `日志示例/` 时，脚本运行完全自包含的测试：

- `FormatRegression`：DataFlash 格式、晚到 FMT/FMTU、类型边界。
- `ReaderRegression`：合成 BIN 读取、seek 和峰值保留。
- `ProductRegression`：处理器、预设、导出失败传播和关键产品规则。
- `ArchitectureRegression`：Workspace、对话框单实例、选择记忆和 EDT 约束。
- `LocalizationRegression`：中英文兼容映射和字段键不变。

输出写入被 Git 忽略的 `verification-0.5.8/`。

## 真实日志回归

在仓库根目录创建 `日志示例/`，放入本地测试日志。该目录和常见日志扩展名都已在 `.gitignore` 中，不会误提交。

完整脚本当前使用这些文件名作为跨格式固定用例：

```text
日志示例/28 1980-1-1 8-00-00.bin
日志示例/3 1980-1-1 8-00-00.bin
日志示例/00000108.log
日志示例/00000156.ulg
日志示例/00000157.ulg
```

它还会遍历目录中的全部 `.bin`，并选择体积最大的 BIN 做 UI 和 reader 压力测试。真实日志不得提交到公开仓库。

## 单独运行测试

```powershell
./build-responsive.ps1
New-Item -ItemType Directory -Force verification-0.5.8
javac -encoding UTF-8 --release 8 -cp build/FlightPlot-responsive-0.5.8.jar -d verification-0.5.8 tests/FormatRegression.java
java -Xmx128m -cp "build/FlightPlot-responsive-0.5.8.jar;verification-0.5.8" FormatRegression
```

测试类采用普通 `main` 和显式断言，不引入 JUnit，从而保持源码包轻量并兼容现有构建。

## 0.5.8 已完成的负载验证

- 657MB 真实 BIN：完整 UI 回归中打开约 0.85–0.88 秒，90% seek 约 4–5 毫秒。
- 1GiB 合成 BIN，56,512,722 条记录：在 `-Xmx128m` 下通过，打开约 1.56 秒。
- 20 个真实日志页、60 次切换：没有重扫、幽灵页或处理器清空。
- 100%、125%、150%、200% DPI 与多档窗口尺寸：图表、字体和对话框布局通过。
- 重复打开、移除全部、添加处理器、日志信息和颜色操作：同类窗口保持单实例。

这些数值来自 0.5.8 发布前的本机环境，只用于回归基线；不同磁盘和 CPU 的绝对耗时会变化。

## 发布前检查

```powershell
./verify-responsive.ps1
ant clean all
./package-responsive.ps1
git status --short
```

确认状态中没有 `.bin/.ulg/.log`、个人路径、`build/`、`verification-*` 或 `release/`。
