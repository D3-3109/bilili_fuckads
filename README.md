# 哔哩哔哩商业开屏拦截：Modern 适配

基于 [bingqiu456/bilili_fuckads](https://github.com/bingqiu456/bilili_fuckads) 的
`c32131383d2c3266abc734335c8533044e035dbb` 修改。

## 兼容范围

- 目标应用：`tv.danmaku.bili` 8.84.0（8840200）。其他应用版本需要重新核对目标类。
- 编译依赖：`io.github.libxposed:api:102.0.0`，通过 `compileOnly` 引入。
- 模块最低 API 101，目标 API 102；API 101 的运行效果尚需对应框架实测。
- Android 最低 27；实际测试目标为 Android 17 / LSPosed 2.2.1（7912）。
- 测试包名：`me.bingyue.fuckbiliads.modern`，可与原模块并存。

## 修改

1. 使用 `XposedModule`、`onModuleLoaded` 和 `onPackageReady`，在应用实际类加载器准备好后安装拦截。
2. 将入口、作用域及 API 版本声明移至 `META-INF/xposed`，移除 legacy 入口和 Manifest 元数据。
3. 保留旧 `Splash.isValid()` 拦截；在旧广告选择器及新 `SplashOrderSelectComponentKt` 中，按静态方法的广告模型返回类型匹配并返回 `null`，覆盖本地、实时及热启动选择。
4. 对新旧商业广告 Fragment 工厂增加返回 `null` 的保护，避免实时结果直接进入展示。
5. 每个拦截点分别记录不同 `SplashSource` 的首次命中；必需类或方法缺失时抛出异常。Debug 包同时记录热启动检查的实际时间间隔。

目标为商业开屏相关类。

## 构建

需要 JDK 17 以上（本次使用 JDK 21）、Android SDK 35 和 Gradle 9.1.0。

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
```

如需将中间产物放到源代码目录外：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain `
  --project-cache-dir C:\build\fuckbiliads-cache `
  -PbuildRoot=C:\build\fuckbiliads-modern
```

Debug 包为测试签名。要长期发布，请自行配置并保存发布签名。

## 设备使用

安装后，在支持现代 API 的框架管理器中启用本模块，仅勾选 `tv.danmaku.bili`，然后结束并重新启动哔哩哔哩。
框架日志中搜索 `FuckBiliAdsModern`：

- `loaded ... api=102`：框架已加载模块。
- `ready hooks=N`：已安装拦截点，N 应大于 0。
- `blocked ...`：对应方法实际命中；它本身不表示服务器本轮一定提供了可展示广告。
- `hot-check ...`：Debug 包记录的热启动间隔和已经经过的时间。

不要把框架中显示“模块已安装”当作“已生效”。应结合启用状态、作用域和命中日志判断。

2026-10-15 在 Android 17、LSPosed 2.2.1（API 102）和哔哩哔哩 8.84.0 上确认：模块加载成功，安装 19 个拦截点，`source=COLD` 和 `source=HOT` 的选择方法均实际命中。测试期间修改的应用配置已恢复。

## 官方参考

- [现代 API 迁移说明](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)
- [API 102.0.0 的 Maven 发布物及源码](https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/)
