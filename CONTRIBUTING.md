# 参与开发

感谢提交问题、日志格式兼容修复和界面改进。请不要把含有设备序列号、坐标、飞行任务或人员信息的原始日志提交到公开 Issue 或 Pull Request。

## 开发环境

- JDK 8 或更高版本；提交前必须以 Java 8 目标构建。
- Windows 可直接运行 PowerShell 脚本；其他平台可使用 Ant。
- 不需要下载或提交专用 JDK、Ant 副本、IDE 工程文件和构建输出。

```powershell
./build-responsive.ps1
./verify-responsive.ps1
```

## 修改规则

1. 日志格式解析放在 `jMAVlib/src/me/drton/jmavlib/log/`，界面代码不得维护固定字段白名单。
2. 文件扫描、序列计算和导出在后台执行；Swing 组件只能在 EDT 更新。
3. 所有日志打开入口必须调用主窗口的统一打开流程；所有临时窗口必须由 `DialogCoordinator` 管理。
4. 日志页状态只保存在 `Workspace`/`ChartTab`，不要在按钮监听器中复制第二份状态。
5. 保持 `.bin/.BIN/.px4log/.log/.ulg`、`.fplot`、KML/GPX 和参数文本格式兼容。
6. 修复行为缺陷时，为能够在无真实日志环境运行的关键路径补充回归用例。
7. 新依赖必须说明用途、许可证和为何不能用 JDK 或现有代码完成。

## 提交检查

- 构建和自包含回归通过。
- `git status` 中没有日志、构建目录、验证图片或个人路径。
- 中英文入口、错误提示和取消路径完整。
- 更新对应 Markdown 文档和 `CHANGELOG.md`。
