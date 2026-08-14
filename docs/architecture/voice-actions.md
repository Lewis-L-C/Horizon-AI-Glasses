# Voice Actions

The voice entry (`VoiceTranslationManager.handleVoiceCommand`) uses two levels:

1. **Local fast matching** — high-frequency commands matched directly in code
   (no network).
2. **AI intent analysis** — when local rules don't match, the recognized text
   is sent to DeepSeek, which picks one of the **27 predefined actions** below.
   The LLM only returns an action name; execution is handled by code.

## The 27 actions

| Action | Meaning | Example commands (from the code prompt) |
| --- | --- | --- |
| `translate` | OCR + translate text | 翻译 / 翻译一下 / 帮我翻译 |
| `close_translate` | stop translation | 关闭翻译 / 停止翻译 / 取消翻译 |
| `simultaneous` | start simultaneous interpretation | 同声传译 / 同传 / 开启同传 / 实时翻译 |
| `close_simultaneous` | stop simultaneous interpretation | 退出同传 / 关闭同传 / 停止同传 |
| `show_health` | show health panel | 我的健康 / 打开健康 / 健康监测 |
| `close_health` | hide health panel | 关闭健康 / 隐藏健康 / 停止健康监测 |
| `check_fatigue` | query fatigue | 我累吗 / 疲劳 / 我有点累 / 状态怎么样 |
| `show_map` | show map | 当前位置在哪 / 打开地图 |
| `close_map` | hide map | 关闭地图 / 隐藏地图 |
| `zoom_in` | zoom map in | 放大一点 |
| `zoom_out` | zoom map out | 缩小 |
| `navigate` | navigate to a place | 导航到肯德基 / 去西湖 |
| `cancel_navigation` | cancel navigation | 取消导航 |
| `close_all` | close everything | 关闭所有 / 关闭全部 / 全部关闭 |
| `take_photo` | take a photo | 拍照 / 拍一张 / 帮我拍个照 |
| `toggle_recording` | start/stop recording | 录像 / 开始录像 / 停止录像 / 拍视频 |
| `start_chat` | plain LLM chat | 今天天气怎么样 / 你好 |
| `pay` | QR payment scan | 支付 / 扫码支付 / 付款 / 扫一扫 |
| `traffic_light` | start traffic-light detection | 红绿灯 / 开启红绿灯 / 识别红绿灯 |
| `close_traffic_light` | stop traffic-light detection | 关闭红绿灯 / 停止红绿灯 |
| `blind_road` | start blind-path detection | 盲道 / 开启盲道 / 识别盲道 / 检测前方道路 |
| `close_blind_road` | stop blind-path detection | 关闭盲道 / 停止盲道 / 关闭道路检测 |
| `solve_problem` | photo problem-solving | 帮我解题 / 拍照解题 / 帮我看看这道题 |
| `navigate_3d` | 3D navigation / view | 3D导航 / 3D导航到肯德基 / 实际导航 / 三维导航 |
| `start_sport` | start sport tracking | 开始运动 / 开始跑步 / 开始走路 / 计步 |
| `stop_sport` | stop sport tracking | 停止运动 / 结束运动 / 关闭运动 |
| `query_sport` | query steps/distance | 走了多少步 / 跑了多远 / 步数 |

## Locally fast-matched commands (no LLM)

| Intent | Example commands |
| --- | --- |
| Start sport | 运动 / 开始运动 / 跑步 / 开始走路 / 计步 |
| Stop sport | 关闭运动 / 停止运动 / 结束运动 |
| 3D navigation (with/without destination) | 实际导航 / 3d导航 / 3导航 / 三维导航 / 3D导航到肯德基 |
| Stop 3D navigation | 关闭3d导航 / 关闭实际导航 / 退出3d导航 |
| Exit simultaneous mode | out / exit / stop / 关闭同传 / 退出同传 / 停止同传 |

> Source of truth: the intent prompt and `executeIntent` branches in
> `app/src/main/java/com/blue/glassesapp/feature/home/ui/VoiceTranslationManager.kt`.
