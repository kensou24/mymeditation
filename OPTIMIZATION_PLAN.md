# 冥想播放器优化任务计划

> 基于 2026-06-07 全面代码审查，按优先级排序 — ✅ 全部完成

---

## Phase 1: 关键 Bug 修复 ✅

- [x] **T01: MediaPlayer 错误处理** — `OnErrorListener`，文件损坏时自动跳下一首
- [x] **T02: MediaPlayer 异步 prepare** — `prepareAsync()` + `OnPreparedListener` 防止 ANR
- [x] **T03: 禁用按钮视觉反馈** — `StateListDrawable` + alpha 变化
- [x] **T04: cancelTimer 空回调** — 仅 timer 激活时回调
- [x] **T05: PendingIntent requestCode** — 不同 action 使用不同 code

## Phase 2: 音频体验增强 ✅

- [x] **T06: MediaSessionCompat** — 蓝牙耳机、锁屏控件、系统音量面板
- [x] **T07: 音频焦点管理** — AudioFocusRequest，处理来电/闹钟/duck
- [x] **T08: 上一首/下一首按钮** — 布局新增按钮 + 通知栏支持
- [x] **T09: 播放模式** — 顺序循环、单曲循环、随机播放
- [x] **T10: 播放列表逻辑** — 从选中文件开始，末尾后循环到目录开头

## Phase 3: 稳定性与兼容性 ✅

- [x] **T11: 配置变更处理** — `configChanges` 属性 + `onSaveInstanceState`/`onRestoreInstanceState`
- [x] **T12: Android 11+ 文件访问** — `MANAGE_EXTERNAL_STORAGE` + `requestLegacyExternalStorage` + 引导设置页
- [x] **T13: 睡眠定时器上限** — 最大 300 分钟
- [x] **T14: onDestroy 安全解绑** — try-catch 防止 `IllegalArgumentException`

## Phase 4: UI/UX 优化 ✅

- [x] **T15: 主题色应用到列表项** — DirectoryAdapter/FileAdapter 接受 ThemeColors
- [x] **T16: 播放进度记忆** — SharedPreferences 记住上次目录和文件
- [x] **T17: 加载状态提示** — ProgressBar 水平进度条
- [x] **T18: 文件排序选项** — 按名称/大小/修改时间循环切换
- [x] **T19: 字符串资源化** — 所有硬编码中文替换为 `@string/xxx`，strings.xml 新增 40+ 条目

## Phase 5: 代码清理 ✅

- [x] **T20: 清理 dead code** — 删除无用字段/分支
- [x] **T21: Release 构建优化** — `minifyEnabled` + `shrinkResources` + ProGuard 规则
- [x] **T22: ThemeManager 线程安全** — `volatile` + 双重检查锁
