# UNO 记牌助手（Android）

UNO 悬浮标记辅助应用。提供始终覆盖在游戏上方的悬浮面板，用于手动记录对手可能/排除的颜色（红/黄/蓝/绿）。

## 功能概览

- 悬浮窗覆盖显示（可调整透明度）
- 动态增删对手，逐个对手显示四种颜色标记
- 颜色两态切换：彩色（未排除）↔ 灰色（排除态）
- 面板位置可拖动，支持锁定/解锁
- 一键重置所有颜色标记
- 前台服务保活悬浮面板

## 权限与系统能力

- `SYSTEM_ALERT_WINDOW`：在其他应用上层显示悬浮窗
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`：前台服务保活

## 构建与发布

- 生成发布签名并构建 Release（PowerShell）：
  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts/create-release-keystore.ps1
  ```
  脚本最后一步执行：
  ```powershell
  ./gradlew.bat :app:assembleRelease
  ```

## 验收与规范

- 需求说明：`requirement.md`
- MVP 验证清单：`docs/overlay-validation.md`
- OpenSpec 变更：`openspec/changes/overlay-color-marker/specs/overlay-color-marker/spec.md`
