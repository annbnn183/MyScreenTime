<div align="center"><img src="assets/ic_launcher_round.png" width="120" alt="應用程式圖示" /></div>

# <div align="center">Screen Time · 螢幕使用時間</div>

<div align="center">

一款檢視裝置與應用程式使用時長、開啟次數與長期趨勢的 Android 應用程式。

**由 美麗的黑莓 接手並主導維護 · 程式碼主要由 AI 輔助編寫**

</div>

<div align="center">

[![GitHub Release](https://img.shields.io/badge/Release-v1.0.16-blue?style=for-the-badge)](https://github.com/annbnn183/MyScreenTime/releases)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=for-the-badge)](https://www.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge)](https://kotlinlang.org)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)](https://www.gnu.org/licenses/gpl-3.0)
[![Build](https://img.shields.io/badge/Build-GitHub%20Actions-2088FF?style=for-the-badge)](.github/workflows/build.yml)

</div>

<div align="center">

[中文](README.md) · [English](README_en.md) · [日本語](README_ja.md) · [한국어](README_ko.md) · 繁體中文

</div>

---

## 📑 目錄

- [關於這個專案](#關於這個專案)
- [✨ 功能特性](#-功能特性)
- [📸 介面截圖](#-介面截圖)
- [⬇️ 下載](#️-下載)
- [💬 聯絡我](#-聯絡我)
- [☕ 打賞 / 支持我](#-打賞--支持我)
- [🧭 產品戰略與發展路線](#-產品戰略與發展路線)
- [⚙️ 自動建置](#️-自動建置)
- [🔐 校驗和](#-校驗和)
- [📄 授權](#-授權)

---

## 關於這個專案

這個專案現在由我（**美麗的黑莓**）接手。從功能規劃、互動取捨到每個版本的發布，都由我推進；具體程式碼主要借助 AI 編寫與迭代。應用程式內的主介面與「設定 → 關於」裡也放了同樣的說明。

在原開源專案的基礎上，我陸續補上了這些能力，並堅持一條原則：**開源、無廣告、不聯網、不蒐集資料**。介面為 Material 3，使用 Kotlin + Jetpack Compose + Vico 開發。

---

## ✨ 功能特性

| 🗓️ 多週期與長期趨勢 | 📊 應用程式四列網格排行 |
| :--- | :--- |
| 日 / 週 / 月多週期檢視，以及 **4 週、半年、一年（平均）** 的長期趨勢檢視：讀取系統預聚合的長週期統計，歷史更完整，也不卡頓。 | 可按使用時長 / 開啟次數 / 名稱排序，附細進度條，支援「再顯示 40 個」增量載入。 |
| 🖱️ 開啟次數與占比 | 🎯 每日螢幕使用目標提醒 |
| 統計各應用程式的開啟（進入前景）次數及占比，並寫入 CSV 報表。 | 自訂目標時長與預警比例，每天在達標前提醒一次（Android 13+ 需通知權限）。 |
| 📈 月度洞察 | 📤 CSV 匯出 / 分享 |
| 最長使用日、活躍天數，以及 30 天目標日曆（綠 = 達標，紅 = 超標）。 | 從頂部工具列一鍵匯出目前週期的每日摘要與應用程式排行。 |
| 🔍 單一應用程式詳情 | 🧩 主畫面小工具 |
| 每個應用程式獨立詳情頁，含對比圖表。 | 無需開啟應用程式即可在桌面檢視使用情況。 |

---

## 💾 資料管理：備份、還原與移轉

> 你累積了一年的資料，比剛安裝時更有價值。本次更新聚焦於讓資料**能被完整備份、安全還原，並在換手機時移轉過去**。

**會備份哪些資料：** 全部設定（主題、預設時間段、忽略應用清單、每日目標、自動備份開關等）與**逐日、逐應用的開啟次數永久紀錄**。

**不會（也無法）備份的資料：** 使用**時長**與小時分布來自 Android 的 `UsageStatsManager`，是**裝置本身的系統資料**，任何應用都無法將它匯出並寫入另一支手機。本應用**如實**只備份自己持有的資料，絕不偽造「時長備份」。移轉後，新裝置從授權當天起重新累積時長。

**備份**
- 具版本號、匯出時間、時區、筆數與 SHA-256 校驗和的 JSON。
- 寫入後立即讀回比對，**只有一致才提示成功**（不一致提示「校驗失敗」）。
- 可選密碼加密（AES-256-GCM + PBKDF2，隨機 salt/IV）。**密碼一旦忘記，資料無法找回。**
- 儲存位置由你決定（內部儲存、下載、SD 卡、Google Drive 等），請勿只留在應用私有目錄（解除安裝會被刪除）。

**還原（安全優先）**
1. 校驗：非空、≤32MB、合法 JSON、版本可辨識、校驗和與筆數一致。
2. 目前資料的安全快照（記憶體＋私有檔案）與「還原進行中」標記。
3. 選擇**合併（保留現有）**（歷史按逐日逐應用取較大值併入，重複匯入不會翻倍）或**覆蓋（取代現有）**。
4. 還原後再次核對筆數。
5. 任何失敗都**自動回滾**到快照，絕不留「只還原一半」的狀態。
6. 崩潰安全：還原途中被強制關閉或斷電，下次啟動會偵測標記並自動回滾。

**換手機移轉步驟：** 舊手機 → 備份（可加密）到可拷走的位置 → 新手機安裝同簽名版本 → 還原 → 選擇合併或覆蓋 → 依提示重新授予「使用情況存取」權限（此系統特殊權限無法隨檔案移轉）。

**自動本機備份：** 每天一份，只保留最近 7 份，存於應用私有外部目錄（解除安裝即刪除，移轉請手動匯出）。

**刪除全部資料：** 先留一份安全快照，再清空設定與歷史。

**版本相容：** 備份格式與資料庫結構相互獨立，檔案帶 `format`/`schemaVersion`；**較新格式的備份會被拒絕並說明原因**，較舊格式會被處理。

---

## 📸 介面截圖

以下為 **v1.0.15** 實機截圖（深色主題）。

| 主介面 · 月檢視 / 月度概覽 | 應用程式排行 · 累計時長 / 四列網格 | 應用程式排行 · 載入更多 / 時段分布 |
| :---: | :---: | :---: |
| <img src="assets/screenshots/01-overview.jpg" width="200" alt="主介面：接手公告、月檢視時長圖表與月度概覽" /> | <img src="assets/screenshots/02-ranking.jpg" width="200" alt="應用程式排行：本範圍累計使用 238 小時與四列應用程式網格" /> | <img src="assets/screenshots/03-ranking-more.jpg" width="200" alt="應用程式排行：分批載入更多應用程式與按時段分布圖" /> |

| 設定 · 外觀 / 顯示 / 每日目標 | 設定 · 關於 / 執行日誌 / 版本 |
| :---: | :---: |
| <img src="assets/screenshots/04-settings.jpg" width="200" alt="設定：主題、純黑背景、動態取色、預設時間段與每日目標" /> | <img src="assets/screenshots/05-about.jpg" width="200" alt="設定：目標提醒、關於、執行日誌、計算方法與版本號" /> |

---

## ⬇️ 下載

到 [GitHub Releases](https://github.com/annbnn183/MyScreenTime/releases) 下載最新 APK。

> 從 **v1.0.7** 起，所有建置使用同一份固定簽名，新版本可以直接覆蓋安裝。如果你還在用 v1.0.4–v1.0.6，升級前請先解除安裝一次舊版本。

---

## 💬 聯絡我

提需求、報 bug、聊想法，或者只是想認識一下，都歡迎：

| 方式 | 帳號 |
| :--- | :--- |
| 微信 | `cookiebesting` |
| 信箱 | [meilideheimei@Outlook.com](mailto:meilideheimei@Outlook.com) |

也可以直接在 [Issues](https://github.com/annbnn183/MyScreenTime/issues) 裡留言。如果覺得這個專案有用，給倉庫點個 ⭐ Star 就是對我最大的支持。

---

## ☕ 打賞 / 支持我

如果你喜歡這個軟體，可以給我打賞，支持我繼續把它維護下去，謝謝你的鼓勵。

**付款**：`廣州市白雲區棠下順紡商行(cookiebesting)`　·　`#付款:廣州市白雲區棠下順紡商行(cookiebesting)`

### 微信收款碼

長按儲存下圖，用微信「掃一掃 → 相簿」辨識即可付款：

<div align="center">
  <img src="assets/wechat-pay-qr.jpg" width="260" alt="微信收款碼 - 廣州市白雲區棠下順紡商行(cookiebesting)" />
</div>

### 複製到微信即可付款

複製下面這條連結，貼到微信裡（發給「檔案傳輸助手」或任意聊天視窗），點開即可付款：

```
https://payapp.wechatpay.cn/sjt/qr/AQEp0bAiFO50xLIWNTOrUoZE
```

> 上面的連結來自收款碼本身，是微信支付的官方收款地址；需要在**微信內**開啟才能發起付款，普通瀏覽器裡無法完成支付。金額由你自行填寫，謝謝支持。

---

## 🧭 產品戰略與發展路線

> **核心定位**：MyScreenTime 不是一個手機防沉迷軟體，而是一個**可靠、準確、長期、隱私優先的個人數位行為統計工具**。
> 防沉迷工具試圖*改變*使用者的行為；統計工具幫助使用者*理解*自己的行為。我們優先把第二件事做好。

**我的數位時間帳本** —— 記錄、統計、比較、分析、匯出，讓使用者知道自己的時間究竟去了哪裡。

### 五條產品原則

1. **資料準確性優先於功能數量。**
2. **長期歷史優先於短期排行榜。**
3. **隱私優先於雲端商業化。**
4. **使用者能夠匯出並掌控自己的資料。**
5. **不評價使用者的行為，只呈現事實。**

### 路線圖（四階段）

| 階段 | 優先級 | 週期 | 目標 |
| :--- | :---: | :--- | :--- |
| 一 · 基礎可靠性 | P0 | 第 1 個月 | 讓使用者相信每一項資料（準確性、跨午夜/時區/重啟、Android 版本相容、權限與缺失提示、公開計算口徑） |
| 二 · 資料分析能力 | P1 | 第 2 個月 | 從「用了多久」升級為「使用規律」（24h 分布、工作日/週末對比、週/月同比、單應用歷史曲線、時間占比、均值與中位數） |
| 三 · 歷史資料與自主權 | P2 | 第 3 個月 | 長期保存與使用自己的資料（完整備份、CSV/JSON 匯入匯出、自訂區間、清理與保留設定、完整性檢查） |
| 四 · 體驗與生態 | 長期 | 第 4–6 個月 | 隨時可看、可分享、可研究（小工具優化、自訂圖表排序、應用分類、月/年度報告、圖表圖片匯出、多語言、F-Droid 評估） |

### 核心功能優先級

| 優先級 | 功能 | 產品價值 |
| :---: | :--- | :--- |
| **P0** | 使用時長準確性 | 產品根基 |
| **P0** | 歷史資料完整性 | 長期價值 |
| **P0** | 應用程式開啟次數 | 差異化 |
| **P0** | CSV / JSON 資料管理 | 自主權 |
| **P1** | 24 小時分布熱力圖 | 發現使用規律 |
| **P1** | 週同比 / 月同比 | 發現變化 |
| **P1** | 單應用程式長期趨勢 | 深度分析 |
| **P1** | 應用程式分類統計 | 理解時間分配 |
| **P2** | 年度報告 | 長期回顧 |
| **P2** | 主畫面小工具 | 便利性 |
| **P3** | 多裝置資料合併 | 擴充能力 |

### 關鍵技術點：長期與短期資料分離

Android 的 `UsageStatsManager` 有兩個不同介面：

- `queryUsageStats`：查詢系統**聚合**的應用程式使用統計（適合長期時長）。
- `queryEvents`：查詢應用程式使用**事件**；官方文件明確事件記錄**只保留幾天**，不能據此還原半年前的每一次開啟。

> 因此採用「按日摘要、長期保存」的儲存策略：使用時長、開啟次數、小時分布都做**每日摘要後長期保存**；原始事件不長期依賴系統記錄；應用程式資訊保存必要的歷史對應。即使 Android 清理了舊事件，已累積的歷史統計也不會消失。

**術語統一**：應用程式進入前景事件並不等於使用者實際點擊了多少次螢幕，故本倉庫統一使用「**開啟次數**」與「**開啟次數占比**」，不再使用「點擊量 / 點擊率」。

### 四大競爭壁壘

1. **長期歷史** —— 不只能看今天，還能回顧過去一年、三年甚至更久的個人使用變化。
2. **資料顆粒度** —— 不只有應用程式排行，還能看到小時分布、開啟次數、時間占比和週期對比。
3. **資料自主權** —— 離線使用、本機保存、可備份、可匯出、可遷移，不被平台鎖定。
4. **統計可信度** —— 公開統計口徑，說明資料來源、缺失情況和估算誤差，不製造虛假的精確感。

### 衡量指標（不只看 Stars）

| 指標 | 衡量目的 |
| :--- | :--- |
| 統計誤差率 | 資料是否可信 |
| 連續 30 天資料完整率 | 是否可靠 |
| 崩潰率 | 穩定性 |
| 冷啟動耗時 | 使用體驗 |
| 資料匯出成功率 | 資料自主權 |
| 30 天留存率 | 長期價值 |
| 使用者回饋解決率 | 維護品質 |

> 注意：30 天留存率高不一定代表產品優秀（使用者可能只是偶爾檢視），需結合資料完整性、使用頻率與實際回饋綜合判斷。

### 戰略總結

MyScreenTime 不需要成為功能最多的螢幕使用時間軟體，而應該成為使用者**最信任的螢幕使用時間資料記錄工具**。

> **記錄事實，累積歷史，分析規律，把資料的解釋權交還給使用者。**

<details>
<summary>📋 路線圖詳細清單（點擊展開）</summary>

**階段一 · 基礎可靠性（P0）**
- [ ] 檢查螢幕使用時長統計準確性
- [ ] 檢查跨午夜、時區變化、重啟後的資料
- [ ] 驗證不同 Android 版本的資料相容性
- [ ] 檢查應用程式開啟次數統計
- [ ] 提供資料缺失、權限關閉的提示
- [ ] 明確每種統計的計算口徑

**階段二 · 資料分析能力（P1）**
- [ ] 24 小時使用時間分布圖
- [ ] 工作日與週末對比
- [ ] 本週與上週對比
- [ ] 本月與上月對比
- [ ] 每個應用程式的歷史變化曲線
- [ ] 應用程式使用時間佔總時間的比例
- [ ] 每日平均使用時長與中位數

**階段三 · 歷史資料與自主權（P2）**
- [ ] 完整歷史資料備份
- [ ] CSV 匯入與匯出
- [ ] JSON 備份與還原
- [ ] 自訂日期範圍查詢
- [ ] 資料清理與日誌保留設定
- [ ] 統計資料完整性檢查

**階段四 · 體驗與生態（長期）**
- [ ] 主畫面小工具優化
- [ ] 自訂圖表和排序
- [ ] 應用程式分類統計
- [ ] 月度、年度報告
- [ ] 圖表圖片匯出
- [ ] 多語言完善
- [ ] F-Droid 發布可行性評估

**暫不投入**（可能增加成本卻不一定提高核心價值）：AI 聊天助手、社交排行榜、強制鎖定應用程式、複雜會員訂閱、雲端帳戶體系、大量與統計無關的功能。

**差異化參考（競品）**
- *Android 數位健康*：系統整合、權限鏈路成熟；不足是廠商實作與歷史分析體驗不一 → 我們的機會在長週期統計與匯出。
- *Screen Time*：無廣告、無蒐集、小時圖表與主畫面小工具 → 我們強化長期趨勢、開啟次數與深度分析。
- *Lumen*：功能豐富 → 我們不求大而全，把統計體驗做到專業。

</details>

---

## ⚙️ 自動建置

推送到 `main` 會觸發 [`.github/workflows/build.yml`](.github/workflows/build.yml)，建置 `assembleDefaultDebug`，用固定金鑰簽名後把 APK 發佈到 Releases。**版本號以 [`app/build.gradle.kts`](app/build.gradle.kts) 的 `versionName` 為唯一來源**：Release 標籤、Release 名稱、APK 檔名（如 `MyScreenTime-v1.0.16.apk`）以及應用程式內「設定 → 關於」顯示的版本都由它生成，因此始終一致；發佈新版本只需同時遞增 `versionName` 與 `versionCode`。簽名金鑰只存放在倉庫的 Actions Secrets 中，不入庫（見 `.gitignore`）。

---

## 🔐 校驗和

為驗證發佈 APK 的真實性與完整性，可將其簽名憑證指紋與下方對照：

```
SHA-256: d075b5e1611b318d9e2f11e62d2bfebe0afbed51c17f935ba701d233d3fef501
SHA-1: 57839e6c38b7821da9c9c80767e7cedf9d8b5eab
MD5: bf656b4b431a1a688a9b2fa69b430e9e
```

憑證主體：`CN=MyScreenTime, OU=OpenSource, O=MyScreenTime, L=Beijing, ST=Beijing, C=CN`。本倉庫所有發佈的 APK 均使用同一固定金鑰簽名，簽名一致即可直接覆蓋安裝、無須解除安裝舊版。

---

## 📄 授權

程式碼沿用 [GNU General Public License v3 or later (GPL-3.0-or-later)](https://www.gnu.org/licenses/gpl-3.0)。

依賴授權：

- [Core KTX](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Activity Compose](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Compose BOM](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Compose UI](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Compose UI Graphics](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Compose Material 3](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Compose Material Icons Extended](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Lifecycle Runtime Compose](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Navigation Compose](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [DataStore Preferences](https://github.com/androidx/androidx) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Koin AndroidX Compose](https://github.com/InsertKoinIO/koin) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Vico](https://github.com/patrykandpatrick/vico) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization) is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

<div align="center">Screen Time · 美麗的黑莓</div>
