# AgentCraft - AI 智能助手前端

一个基于 Vue 3 的 AI 聊天助手前端应用，支持多智能体对话、文件问答、PPT 生成和深度研究等功能。

## 项目结构

```
agentcraft-frontend/
├── index.html          # 主 HTML 文件
├── README.md           # 项目说明文档（本文件）
├── config.js           # 配置文件 - 修改后端地址
├── css/
│   └── style.css       # 样式文件
└── js/
    ├── config.js       # 全局配置（后端地址等）
    ├── constants.js    # 常量定义
    ├── utils.js        # 工具函数
    ├── api.js          # API 调用封装
    └── app.js          # Vue 主应用
```

## 功能特性

- **多智能体对话**：支持对话助手、文件问答、PPT 生成、深度研究四种模式
- **会话管理**：新建、切换、删除会话
- **文件上传**：支持 PDF、Word、TXT、PNG、JPG 格式文件上传和问答
- **流式输出**：实时显示 AI 回复内容
- **思考过程**：展示 AI 的思考过程
- **参考来源**：显示 AI 回答的参考链接
- **推荐问题**：根据上下文推荐相关问题
- **代码高亮**：支持 Markdown 和代码语法高亮
- **响应式设计**：适配不同屏幕尺寸

## 配置后端地址

### 修改后端地址

打开 `js/config.js` 文件，修改 `backendUrl` 的值：

```javascript
const CONFIG = {
    // 修改这里的地址，例如：
    backendUrl: 'http://127.0.0.1:8211'
    // 或者
    // backendUrl: 'http://localhost:8888'
};
```

保存文件后刷新浏览器即可生效。

## 启动项目

### 方式一：使用 VSCode Live Server（推荐）

1. 在 VSCode 中安装 **Live Server** 扩展
2. 右键点击 `index.html`
3. 选择 **"Open with Live Server"**
4. 浏览器会自动打开项目

### 方式二：使用 Python HTTP 服务器

1. 确保已安装 Python
2. 在项目目录下运行：
   ```bash
   python -m http.server 8080
   ```
3. 在浏览器访问：`http://localhost:8080`

### 方式三：使用 Node.js http-server

1. 安装 http-server：
   ```bash
   npm install -g http-server
   ```
2. 在项目目录下运行：
   ```bash
   http-server -p 8080
   ```
3. 在浏览器访问：`http://localhost:8080`

### 方式四：直接打开 HTML 文件

直接双击 `index.html` 文件，在浏览器中打开。
> **注意**：这种方式可能存在一些跨域限制，推荐使用前面三种方式。

## 常见问题

### 无法连接到后端服务

- 检查后端服务是否正在运行
- 确认 `js/config.js` 中的 `backendUrl` 配置是否正确
- 查看浏览器控制台是否有错误信息

### 文件上传失败

- 确认上传的文件格式是否支持（PDF、Word、TXT、PNG、JPG）
- 检查文件大小是否超过后端限制
- 查看浏览器控制台错误信息

### 界面样式异常

- 确保网络连接正常，因为项目使用 CDN 加载 Vue 等依赖
- 检查 `css/style.css` 文件是否存在

### 推荐问题不显示

- 推荐问题仅在当前对话的最后一条 AI 消息后显示
- 如果对话历史较长，可能不会显示推荐问题

## 技术栈

- **Vue 3** - 前端框架（CDN 方式）
- **Marked.js** - Markdown 渲染
- **Highlight.js** - 代码语法高亮
- **DOMPurify** - XSS 防护
- **Font Awesome** - 图标库

## 开发说明

### 代码结构

- `config.js` - 全局配置，存放后端地址等可配置项
- `constants.js` - 常量定义，如智能体列表、文件类型等
- `utils.js` - 工具函数，如 ID 生成、文件大小格式化等
- `api.js` - API 调用封装，所有与后端交互的接口
- `app.js` - Vue 主应用，负责状态管理和 UI 渲染

### 添加新智能体

在 `js/constants.js` 的 `AGENTS` 数组中添加新项：

```javascript
const AGENTS = [
    { id: 'chat', name: '对话助手', icon: '💬' },
    { id: 'file', name: '文件问答', icon: '📁' },
    { id: 'ppt', name: 'PPT生成', icon: '📊' },
    { id: 'deep', name: '深度研究', icon: '🔬' },
    // 添加新的智能体
    { id: 'newagent', name: '新智能体', icon: '🆕' }
];
```

然后在 `js/api.js` 的 `getStreamChatUrl` 函数中添加对应的 API 路径。

## 许可证

MIT License
