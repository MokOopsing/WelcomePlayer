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

App 启动后会按文件名排序依次播放。

全部播放结束后 App 自动退出。

支持：
- mp3
- wav
- m4a
- aac
- ogg
- flac

## 配合 vivo 捷径

捷径只需要执行“启动 WelcomePlayer”。

汽车连接蓝牙 -> 捷径 -> 启动 WelcomePlayer -> 自动播放 -> 播放结束退出。

## 注意

这是第一版测试工程。不同 Android/vivo 系统版本对应用访问共享 Music 目录的行为可能不同。
