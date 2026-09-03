# WelcomePlayer

极简 Android 欢迎音频播放器。

## 使用方法

把音频文件放到：

`内部存储/Music/WelcomePlayer/`

例如：

```
Music/
└── WelcomePlayer/
    ├── 01_欢迎回来.mp3
    ├── 02_已连接车辆.mp3
    └── 03_祝您旅途愉快.mp3
```

App 启动后会按文件名排序依次播放。播放由后台前台服务托管，播放结束后 App 不会自动退出，服务也会继续留存。

USB 插拔事件会记录到应用内部的 `files/usb-events.log`，同时写入 Logcat，记录时间、广播 action、设备名称、厂商 ID、产品 ID、设备类别等信息。

支持：
- mp3
- wav
- m4a
- aac
- ogg
- flac

## 配合 vivo 捷径

捷径只需要执行“启动 WelcomePlayer”。

汽车连接蓝牙 -> 捷径 -> 启动 WelcomePlayer -> 自动播放。

## 注意

这是第一版测试工程。不同 Android/vivo 系统版本对应用访问共享 Music 目录的行为可能不同。
