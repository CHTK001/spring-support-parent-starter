# Spring Support WebRTC Starter

基于Spring Boot的WebRTC视频通话和视频会议支持组件，使用Socket.IO作为信令服务器。

## 功能特性

- 🎥 **视频通话**: 支持一对一视频通话
- 👥 **视频会议**: 支持多人视频会议
- 🔊 **音频控制**: 支持音频开关控制
- 📺 **视频控制**: 支持视频开关控制
- 🖥️ **屏幕共享**: 支持屏幕共享功能
- 🏠 **房间管理**: 支持房间创建、加入、离开
- 🔢 **房间号系统**: 支持雪花算法生成的唯一数字房间号
- ⚡ **实时信令**: 基于Socket.IO的实时信令交换
- 🔧 **自动配置**: Spring Boot自动配置支持
- 💾 **数据库持久化**: 支持房间和用户信息的数据库存储
- 👤 **创建人管理**: 支持房间创建人权限管理
- 🔄 **自动清理**: 支持过期房间和空房间的自动清理
- 📊 **房间统计**: 支持房间使用情况统计和监控

## 快速开始

### 1. 添加依赖

在你的Spring Boot项目中添加以下依赖：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-webrtc-starter</artifactId>
    <version>4.0.0.33</version>
</dependency>

<!-- 如果需要数据库持久化功能，还需要添加MyBatis依赖 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-starter</artifactId>
    <version>4.0.0.33</version>
</dependency>
```

### 2. 配置文件

在`application.yml`中添加配置：

```yaml
plugin:
  webrtc:
    enabled: true
    stun-server:
      url: "stun:stun.l.google.com:19302"
    turn-server:
      url: "turn:your-turn-server.com:3478"
      username: "your-username"
      password: "your-password"
    room:
      max-rooms: 100
      max-users-per-room: 10
      timeout-minutes: 60
    # 数据库持久化配置（可选）
    persistence:
      enabled: true
      cleanup:
        expired-rooms-cron: "0 */10 * * * ?"  # 每10分钟清理过期房间
        empty-rooms-cron: "0 */5 * * * ?"     # 每5分钟清理空房间
        room-timeout-minutes: 60              # 房间超时时间（分钟）

# 数据库配置（如果启用持久化）
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/webrtc_db?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8
    username: your_username
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 3. 启动应用

启动你的Spring Boot应用，WebRTC功能将自动启用。

## API接口

### REST API

#### 获取WebRTC配置

```http
GET /api/webrtc/config
```

**响应示例:**

```json
{
  "stunServer": {
    "url": "stun:stun.l.google.com:19302"
  },
  "turnServer": {
    "url": "turn:your-turn-server.com:3478",
    "username": "your-username",
    "password": "your-password"
  }
}
```

#### 获取所有房间

```http
GET /api/webrtc/rooms
```

**接口说明:** 获取系统中所有房间的基本信息列表

**请求参数:** 无

**响应字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | String | 房间唯一标识符 |
| roomNumber | Long | 房间号码，用于用户加入房间 |
| creatorId | String | 房间创建者用户ID |
| userCount | Integer | 当前房间内用户数量 |
| status | String | 房间状态：ACTIVE(活跃)、CLOSED(已关闭) |
| createTime | String | 房间创建时间，ISO 8601格式 |
| lastActiveTime | String | 房间最后活跃时间，ISO 8601格式 |

**响应示例:**

```json
[
  {
    "id": "room123",
    "roomNumber": 123456789,
    "creatorId": "user123",
    "userCount": 3,
    "status": "ACTIVE",
    "createTime": "2024-01-01T10:00:00",
    "lastActiveTime": "2024-01-01T10:30:00"
  }
]
```

#### 获取指定房间信息

```http
GET /api/webrtc/rooms/{roomId}
```

**接口说明:** 根据房间ID获取房间详细信息，包括房间内用户列表

**路径参数:**
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |

**响应字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | String | 房间唯一标识符 |
| roomNumber | Long | 房间号码 |
| creatorId | String | 房间创建者用户ID |
| users | Array | 房间内用户列表 |
| users[].userId | String | 用户唯一标识符 |
| users[].userName | String | 用户显示名称 |
| users[].joinTime | String | 用户加入房间时间，ISO 8601格式 |
| users[].videoEnabled | Boolean | 用户视频是否开启 |
| users[].audioEnabled | Boolean | 用户音频是否开启 |
| userCount | Integer | 当前房间内用户数量 |
| status | String | 房间状态：ACTIVE(活跃)、CLOSED(已关闭) |
| createTime | String | 房间创建时间，ISO 8601格式 |
| lastActiveTime | String | 房间最后活跃时间，ISO 8601格式 |

**响应示例:**

```json
{
  "id": "room123",
  "roomNumber": 123456789,
  "creatorId": "user123",
  "users": [
    {
      "userId": "user123",
      "userName": "张三",
      "joinTime": "2024-01-01T10:00:00",
      "videoEnabled": true,
      "audioEnabled": true
    }
  ],
  "userCount": 1,
  "status": "ACTIVE",
  "createTime": "2024-01-01T10:00:00",
  "lastActiveTime": "2024-01-01T10:30:00"
}
```

#### 通过房间号获取房间信息

```http
GET /api/webrtc/rooms/number/{roomNumber}
```

**接口说明:** 根据房间号获取房间详细信息，用于用户通过房间号加入房间前的信息查询

**路径参数:**
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomNumber | Long | 是 | 房间号码，用户可见的房间标识 |

**响应字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | String | 房间唯一标识符 |
| roomNumber | Long | 房间号码 |
| creatorId | String | 房间创建者用户ID |
| users | Array | 房间内用户列表 |
| users[].userId | String | 用户唯一标识符 |
| users[].userName | String | 用户显示名称 |
| users[].joinTime | String | 用户加入房间时间，ISO 8601格式 |
| users[].videoEnabled | Boolean | 用户视频是否开启 |
| users[].audioEnabled | Boolean | 用户音频是否开启 |
| userCount | Integer | 当前房间内用户数量 |
| status | String | 房间状态：ACTIVE(活跃)、CLOSED(已关闭) |
| createTime | String | 房间创建时间，ISO 8601格式 |
| lastActiveTime | String | 房间最后活跃时间，ISO 8601格式 |

**响应示例:**

```json
{
  "id": "room123",
  "roomNumber": 123456789,
  "creatorId": "user123",
  "users": [
    {
      "userId": "user123",
      "userName": "张三",
      "joinTime": "2024-01-01T10:00:00",
      "videoEnabled": true,
      "audioEnabled": true
    }
  ],
  "userCount": 1,
  "status": "ACTIVE",
  "createTime": "2024-01-01T10:00:00",
  "lastActiveTime": "2024-01-01T10:30:00"
}
```

#### 创建房间

```http
POST /api/webrtc/rooms
Content-Type: application/json
```

**接口说明:** 创建新的WebRTC房间，系统会自动生成房间号

**请求参数:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符，建议使用UUID |
| creatorId | String | 是 | 房间创建者用户ID |

**请求示例:**

```json
{
  "roomId": "room123",
  "creatorId": "user123"
}
```

**响应字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | String | 房间唯一标识符 |
| roomNumber | Long | 系统自动生成的房间号码 |
| creatorId | String | 房间创建者用户ID |
| users | Array | 房间内用户列表，创建时为空 |
| userCount | Integer | 当前房间内用户数量，创建时为0 |
| status | String | 房间状态，创建时为ACTIVE |
| createTime | String | 房间创建时间，ISO 8601格式 |
| lastActiveTime | String | 房间最后活跃时间，ISO 8601格式 |

**响应示例:**

```json
{
  "id": "room123",
  "roomNumber": 123456789,
  "creatorId": "user123",
  "users": [],
  "userCount": 0,
  "status": "ACTIVE",
  "createTime": "2024-01-01T10:00:00",
  "lastActiveTime": "2024-01-01T10:00:00"
}
```

#### 通过房间号加入房间

```http
POST /api/webrtc/rooms/number/{roomNumber}/join
Content-Type: application/json
```

**接口说明:** 用户通过房间号加入指定房间

**路径参数:**
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomNumber | Long | 是 | 房间号码 |

**请求参数:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | String | 是 | 用户唯一标识符 |
| userName | String | 是 | 用户显示名称 |

**请求示例:**

```json
{
  "userId": "user456",
  "userName": "李四"
}
```

**响应字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | Boolean | 操作是否成功 |
| message | String | 操作结果消息 |
| room | Object | 房间信息对象 |
| room.id | String | 房间唯一标识符 |
| room.roomNumber | Long | 房间号码 |
| room.creatorId | String | 房间创建者用户ID |
| room.userCount | Integer | 当前房间内用户数量 |
| room.status | String | 房间状态 |
| room.createTime | String | 房间创建时间，ISO 8601格式 |
| room.lastActiveTime | String | 房间最后活跃时间，ISO 8601格式 |

**响应示例:**

```json
{
  "success": true,
  "message": "Joined room successfully",
  "room": {
    "id": "room123",
    "roomNumber": 123456789,
    "creatorId": "user123",
    "userCount": 1,
    "status": "ACTIVE",
    "createTime": "2024-01-01T10:00:00",
    "lastActiveTime": "2024-01-01T10:05:00"
  }
}
```

#### 获取房间用户列表

```http
GET /api/webrtc/rooms/{roomId}/users
```

**接口说明:** 获取指定房间内所有用户的详细信息

**路径参数:**
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |

**响应字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| userId | String | 用户唯一标识符 |
| userName | String | 用户显示名称 |
| joinTime | String | 用户加入房间时间，ISO 8601格式 |
| videoEnabled | Boolean | 用户视频是否开启 |
| audioEnabled | Boolean | 用户音频是否开启 |

**响应示例:**

```json
[
  {
    "userId": "user123",
    "userName": "张三",
    "joinTime": "2024-01-01T10:00:00",
    "videoEnabled": true,
    "audioEnabled": true
  }
]
```

#### 关闭房间（仅创建人可操作）

```http
POST /api/webrtc/rooms/{roomId}/close
Content-Type: application/json
```

**接口说明:** 关闭指定房间，只有房间创建者可以执行此操作

**路径参数:**
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |

**请求参数:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| creatorId | String | 是 | 房间创建者用户ID，用于权限验证 |

**请求示例:**

```json
{
  "creatorId": "user123"
}
```

**响应字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | Boolean | 操作是否成功 |
| message | String | 操作结果消息 |
| roomId | String | 被关闭的房间ID |

**响应示例:**

```json
{
  "success": true,
  "message": "Room closed successfully",
  "roomId": "room123"
}
```

#### 获取房间统计信息

```http
GET /api/webrtc/rooms/statistics
```

**接口说明:** 获取系统中房间和用户的统计信息，用于监控和管理

**请求参数:** 无

**响应字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| totalRooms | Integer | 系统中房间总数（包括已关闭的房间） |
| activeRooms | Integer | 当前活跃房间数量 |
| totalUsers | Integer | 当前在线用户总数 |
| averageUsersPerRoom | Double | 每个房间的平均用户数 |
| peakConcurrentUsers | Integer | 历史最高并发用户数 |
| lastUpdated | String | 统计信息最后更新时间，ISO 8601格式 |

**响应示例:**

```json
{
  "totalRooms": 25,
  "activeRooms": 15,
  "totalUsers": 45,
  "averageUsersPerRoom": 3.0,
  "peakConcurrentUsers": 60,
  "lastUpdated": "2024-01-01T10:30:00"
}
```

#### 手动清理过期房间

```http
POST /api/webrtc/rooms/cleanup/expired
```

**接口说明:** 手动触发清理过期房间的操作，通常用于管理员维护

**请求参数:** 无

**响应字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | Boolean | 操作是否成功 |
| message | String | 操作结果消息 |
| cleanedRoomsCount | Integer | 被清理的房间数量 |

**响应示例:**

```json
{
  "success": true,
  "message": "Expired rooms cleaned up",
  "cleanedRoomsCount": 5
}
```

#### 删除房间

```http
DELETE /api/webrtc/rooms/{roomId}
```

**接口说明:** 强制删除指定房间，通常用于管理员操作

**路径参数:**
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |

**请求参数:** 无

**响应:** HTTP 204 No Content（删除成功）或相应的错误状态码

### Socket.IO事件

#### 客户端发送事件

##### `joinRoom` - 加入房间

**事件说明:** 用户加入指定房间

**参数说明:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |
| userId | String | 是 | 用户唯一标识符 |
| userName | String | 是 | 用户显示名称 |

**使用示例:**

```javascript
socket.emit('joinRoom', {
  roomId: 'room123',
  userId: 'user123',
  userName: '张三'
});
```

##### `leaveRoom` - 离开房间

**事件说明:** 用户离开指定房间

**参数说明:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |
| userId | String | 是 | 用户唯一标识符 |

**使用示例:**

```javascript
socket.emit('leaveRoom', {
  roomId: 'room123',
  userId: 'user123'
});
```

##### `offer` - 发送WebRTC Offer

**事件说明:** 发送WebRTC连接的Offer信息给指定用户

**参数说明:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |
| fromUserId | String | 是 | 发送方用户ID |
| toUserId | String | 是 | 接收方用户ID |
| offer | Object | 是 | WebRTC Offer对象 |
| offer.type | String | 是 | 固定值："offer" |
| offer.sdp | String | 是 | SDP描述信息 |

**使用示例:**

```javascript
socket.emit('offer', {
  roomId: 'room123',
  fromUserId: 'user123',
  toUserId: 'user456',
  offer: {
    type: 'offer',
    sdp: 'v=0\r\no=- 123456789 2 IN IP4 127.0.0.1\r\n...'
  }
});
```

##### `answer` - 发送WebRTC Answer

**事件说明:** 发送WebRTC连接的Answer信息给指定用户

**参数说明:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |
| fromUserId | String | 是 | 发送方用户ID |
| toUserId | String | 是 | 接收方用户ID |
| answer | Object | 是 | WebRTC Answer对象 |
| answer.type | String | 是 | 固定值："answer" |
| answer.sdp | String | 是 | SDP描述信息 |

**使用示例:**

```javascript
socket.emit('answer', {
  roomId: 'room123',
  fromUserId: 'user456',
  toUserId: 'user123',
  answer: {
    type: 'answer',
    sdp: 'v=0\r\no=- 987654321 2 IN IP4 127.0.0.1\r\n...'
  }
});
```

##### `iceCandidate` - 发送ICE Candidate

**事件说明:** 发送ICE候选信息给指定用户，用于建立P2P连接

**参数说明:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |
| fromUserId | String | 是 | 发送方用户ID |
| toUserId | String | 是 | 接收方用户ID |
| candidate | Object | 是 | ICE候选对象 |
| candidate.candidate | String | 是 | ICE候选字符串 |
| candidate.sdpMLineIndex | Number | 是 | SDP媒体行索引 |
| candidate.sdpMid | String | 是 | SDP媒体ID |

**使用示例:**

```javascript
socket.emit('iceCandidate', {
  roomId: 'room123',
  fromUserId: 'user123',
  toUserId: 'user456',
  candidate: {
    candidate: 'candidate:1 1 UDP 2130706431 192.168.1.100 54400 typ host',
    sdpMLineIndex: 0,
    sdpMid: '0'
  }
});
```

##### `mediaStateChange` - 媒体状态变化

**事件说明:** 通知房间内其他用户当前用户的媒体状态变化

**参数说明:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |
| userId | String | 是 | 用户唯一标识符 |
| videoEnabled | Boolean | 是 | 视频是否开启 |
| audioEnabled | Boolean | 是 | 音频是否开启 |

**使用示例:**

```javascript
socket.emit('mediaStateChange', {
  roomId: 'room123',
  userId: 'user123',
  videoEnabled: true,
  audioEnabled: false
});
```

##### `getRoomInfo` - 获取房间信息

**事件说明:** 请求获取指定房间的详细信息

**参数说明:**
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| roomId | String | 是 | 房间唯一标识符 |

**使用示例:**

```javascript
socket.emit('getRoomInfo', {
  roomId: 'room123'
});
```

#### 服务端发送事件

##### `joined` - 加入房间成功

**事件说明:** 用户成功加入房间后，服务端推送给该用户的确认事件

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| roomId | String | 房间唯一标识符 |
| userId | String | 加入房间的用户ID |
| users | Array | 房间内其他用户列表 |
| users[].userId | String | 用户唯一标识符 |
| users[].userName | String | 用户显示名称 |
| users[].joinTime | String | 用户加入时间，ISO 8601格式 |
| users[].videoEnabled | Boolean | 用户视频是否开启 |
| users[].audioEnabled | Boolean | 用户音频是否开启 |

**使用示例:**

```javascript
socket.on('joined', (data) => {
  // data 结构:
  {
    roomId: 'room123',
    userId: 'user123',
    users: [
      {
        userId: 'user456',
        userName: '李四',
        joinTime: '2024-01-01T10:00:00',
        videoEnabled: true,
        audioEnabled: true
      }
    ]
  }
});
```

##### `joinFailed` - 加入房间失败

**事件说明:** 用户加入房间失败时，服务端推送的错误事件

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| roomId | String | 房间唯一标识符 |
| userId | String | 尝试加入房间的用户ID |
| reason | String | 失败原因描述 |

**使用示例:**

```javascript
socket.on('joinFailed', (data) => {
  // data 结构:
  {
    roomId: 'room123',
    userId: 'user123',
    reason: '房间已满' // 或其他错误原因
  }
});
```

##### `userJoined` - 用户加入房间

**事件说明:** 有新用户加入房间时，推送给房间内其他用户的通知事件

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| userId | String | 新加入用户的唯一标识符 |
| userName | String | 新加入用户的显示名称 |
| joinTime | String | 用户加入时间，ISO 8601格式 |
| videoEnabled | Boolean | 用户视频是否开启 |
| audioEnabled | Boolean | 用户音频是否开启 |

**使用示例:**

```javascript
socket.on('userJoined', (user) => {
  // user 结构:
  {
    userId: 'user789',
    userName: '王五',
    joinTime: '2024-01-01T10:05:00',
    videoEnabled: true,
    audioEnabled: true
  }
});
```

##### `userLeft` - 用户离开房间

**事件说明:** 有用户离开房间时，推送给房间内其他用户的通知事件

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| roomId | String | 房间唯一标识符 |
| userId | String | 离开用户的唯一标识符 |
| userName | String | 离开用户的显示名称 |

**使用示例:**

```javascript
socket.on('userLeft', (data) => {
  // data 结构:
  {
    roomId: 'room123',
    userId: 'user456',
    userName: '李四'
  }
});
```

##### `offer` - 接收WebRTC Offer

**事件说明:** 接收来自其他用户的WebRTC Offer信息

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| roomId | String | 房间唯一标识符 |
| fromUserId | String | 发送方用户ID |
| toUserId | String | 接收方用户ID |
| offer | Object | WebRTC Offer对象 |
| offer.type | String | 固定值："offer" |
| offer.sdp | String | SDP描述信息 |

**使用示例:**

```javascript
socket.on('offer', (data) => {
  // data 结构:
  {
    roomId: 'room123',
    fromUserId: 'user123',
    toUserId: 'user456',
    offer: {
      type: 'offer',
      sdp: 'v=0\r\no=- 123456789 2 IN IP4 127.0.0.1\r\n...'
    }
  }
});
```

##### `answer` - 接收WebRTC Answer

**事件说明:** 接收来自其他用户的WebRTC Answer信息

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| roomId | String | 房间唯一标识符 |
| fromUserId | String | 发送方用户ID |
| toUserId | String | 接收方用户ID |
| answer | Object | WebRTC Answer对象 |
| answer.type | String | 固定值："answer" |
| answer.sdp | String | SDP描述信息 |

**使用示例:**

```javascript
socket.on('answer', (data) => {
  // data 结构:
  {
    roomId: 'room123',
    fromUserId: 'user456',
    toUserId: 'user123',
    answer: {
      type: 'answer',
      sdp: 'v=0\r\no=- 987654321 2 IN IP4 127.0.0.1\r\n...'
    }
  }
});
```

##### `iceCandidate` - 接收ICE Candidate

**事件说明:** 接收来自其他用户的ICE候选信息

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| roomId | String | 房间唯一标识符 |
| fromUserId | String | 发送方用户ID |
| toUserId | String | 接收方用户ID |
| candidate | Object | ICE候选对象 |
| candidate.candidate | String | ICE候选字符串 |
| candidate.sdpMLineIndex | Number | SDP媒体行索引 |
| candidate.sdpMid | String | SDP媒体ID |

**使用示例:**

```javascript
socket.on('iceCandidate', (data) => {
  // data 结构:
  {
    roomId: 'room123',
    fromUserId: 'user123',
    toUserId: 'user456',
    candidate: {
      candidate: 'candidate:1 1 UDP 2130706431 192.168.1.100 54400 typ host',
      sdpMLineIndex: 0,
      sdpMid: '0'
    }
  }
});
```

##### `mediaStateChanged` - 媒体状态变化

**事件说明:** 房间内用户媒体状态发生变化时的通知事件

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| roomId | String | 房间唯一标识符 |
| userId | String | 状态变化的用户ID |
| userName | String | 状态变化的用户名称 |
| videoEnabled | Boolean | 用户视频是否开启 |
| audioEnabled | Boolean | 用户音频是否开启 |

**使用示例:**

```javascript
socket.on('mediaStateChanged', (data) => {
  // data 结构:
  {
    roomId: 'room123',
    userId: 'user123',
    userName: '张三',
    videoEnabled: false,
    audioEnabled: true
  }
});
```

##### `roomInfo` - 房间信息

**事件说明:** 响应getRoomInfo请求，返回房间详细信息

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| roomId | String | 房间唯一标识符 |
| users | Array | 房间内用户列表 |
| users[].userId | String | 用户唯一标识符 |
| users[].userName | String | 用户显示名称 |
| users[].joinTime | String | 用户加入时间，ISO 8601格式 |
| users[].videoEnabled | Boolean | 用户视频是否开启 |
| users[].audioEnabled | Boolean | 用户音频是否开启 |
| userCount | Integer | 房间内用户总数 |

**使用示例:**

```javascript
socket.on('roomInfo', (data) => {
  // data 结构:
  {
    roomId: 'room123',
    users: [
      {
        userId: 'user123',
        userName: '张三',
        joinTime: '2024-01-01T10:00:00',
        videoEnabled: true,
        audioEnabled: true
      }
    ],
    userCount: 1
  }
});
```

##### `roomNotFound` - 房间不存在

**事件说明:** 请求的房间不存在时的错误通知

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| roomId | String | 请求的房间ID |

**使用示例:**

```javascript
socket.on('roomNotFound', (data) => {
  // data 结构:
  {
    roomId: 'room123'
  }
});
```

##### `error` - 错误信息

**事件说明:** 系统错误或操作失败时的通知事件

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| message | String | 错误描述信息 |
| code | String | 错误代码（可选） |
| details | Object | 详细错误信息（可选） |

**使用示例:**

```javascript
socket.on('error', (data) => {
  // data 结构:
  {
    message: '错误描述',
    code: 'ERROR_CODE', // 可选
    details: {} // 可选的详细信息
  }
});
```

##### `connectionReplaced` - 连接被替换

**事件说明:** 当同一用户ID在同一房间中建立新连接时，旧连接会收到此事件通知

**数据字段说明:**
| 字段名 | 类型 | 说明 |
|--------|------|------|
| message | String | 连接被替换的提示信息 |

**使用示例:**

```javascript
socket.on('connectionReplaced', (message) => {
  console.log('连接被替换:', message);
  // 处理连接被替换的逻辑，如显示提示信息
  alert('您的连接已被新的连接替换');
});
```

## 房间号系统

本WebRTC组件支持两种房间标识方式：

1. **房间ID**: 传统的字符串标识符（如：`abc12345`）
2. **房间号**: 基于雪花算法生成的唯一数字标识符（如：`123456789`）

### 雪花算法特性

- **唯一性**: 保证生成的房间号全局唯一
- **有序性**: 房间号按时间递增
- **高性能**: 支持高并发场景下的ID生成
- **用户友好**: 9位纯数字，便于用户记忆和输入

### 使用场景

- **房间ID**: 适用于程序间调用，具有更好的随机性
- **房间号**: 适用于用户手动输入，更加直观和易记

用户可以通过房间号快速加入房间，无需记住复杂的房间ID字符串。

## 重复用户处理机制

### 功能说明

为了确保房间中每个用户ID的唯一性，系统实现了重复用户处理机制。当同一用户ID尝试在同一房间中建立多个连接时，系统会自动处理重复连接。

### 处理流程

1. **检测重复用户**: 当用户尝试加入房间时，系统检查该用户ID是否已在房间中
2. **关闭旧连接**: 如果发现重复用户，系统会：
    - 向旧连接发送`connectionReplaced`事件通知
    - 断开旧的Socket.IO连接
3. **更新连接**: 将现有用户对象的SocketIOClient更新为新连接
4. **通知重连成功**: 向新连接发送`joined`事件，包含`reconnected: true`标识

### 前端处理建议

```javascript
// 监听连接被替换事件
socket.on('connectionReplaced', (message) => {
  console.log('连接被替换:', message);
  // 显示提示信息给用户
  showNotification('您的连接已被新的连接替换', 'warning');
  // 可选：清理当前页面状态
  cleanup();
});

// 监听重连成功事件
socket.on('joined', (data) => {
  if (data.reconnected) {
    console.log('重新连接成功');
    showNotification('重新连接成功', 'success');
  } else {
    console.log('首次加入房间成功');
  }
});
```

### 使用场景

- **多标签页**: 用户在多个浏览器标签页中打开同一房间
- **网络重连**: 网络断开后用户重新连接
- **设备切换**: 用户从一个设备切换到另一个设备
- **意外刷新**: 页面刷新导致的重新连接

## 数据库持久化

### 功能特性

本组件支持可选的数据库持久化功能，提供以下特性：

- **房间持久化**: 房间信息存储到数据库，支持重启后恢复
- **用户管理**: 用户信息和状态的持久化存储
- **创建人权限**: 支持房间创建人的权限管理
- **自动清理**: 定时清理过期房间和空房间
- **统计监控**: 提供房间使用情况的统计信息

### 数据库表结构

#### webrtc_room - 房间表

```sql
CREATE TABLE webrtc_room
(
    webrtc_room_id               VARCHAR(64) PRIMARY KEY COMMENT '房间ID',
    webrtc_room_number           BIGINT UNIQUE NOT NULL COMMENT '房间号（雪花算法生成）',
    webrtc_room_creator_id       VARCHAR(64)   NOT NULL COMMENT '创建人ID',
    webrtc_room_status           VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '房间状态',
    webrtc_room_max_users        INT         DEFAULT 10 COMMENT '最大用户数',
    webrtc_room_current_users    INT         DEFAULT 0 COMMENT '当前用户数',
    webrtc_room_create_time      DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    webrtc_room_update_time      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    webrtc_room_last_active_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    webrtc_room_is_deleted       TINYINT     DEFAULT 0 COMMENT '是否删除',
    webrtc_room_version          INT         DEFAULT 1 COMMENT '版本号（乐观锁）',
    webrtc_room_remark           VARCHAR(500) COMMENT '备注'
);
```

#### webrtc_user - 用户表

```sql
CREATE TABLE webrtc_user
(
    webrtc_user_id               VARCHAR(64) PRIMARY KEY COMMENT '用户ID',
    webrtc_user_name             VARCHAR(100) NOT NULL COMMENT '用户名',
    webrtc_user_display_name     VARCHAR(100) COMMENT '显示名称',
    webrtc_user_status           VARCHAR(20) DEFAULT 'ONLINE' COMMENT '用户状态',
    webrtc_user_avatar           VARCHAR(500) COMMENT '头像URL',
    webrtc_user_device_info      VARCHAR(200) COMMENT '设备信息',
    webrtc_user_ip_address       VARCHAR(50) COMMENT 'IP地址',
    webrtc_user_browser_info     VARCHAR(200) COMMENT '浏览器信息',
    webrtc_user_last_active_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    webrtc_user_create_time      DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    webrtc_user_update_time      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    webrtc_user_is_deleted       TINYINT     DEFAULT 0 COMMENT '是否删除',
    webrtc_user_version          INT         DEFAULT 1 COMMENT '版本号（乐观锁）',
    webrtc_user_remark           VARCHAR(500) COMMENT '备注'
);
```

### 数据库初始化

1. **自动初始化**: 如果使用Flyway，将提供的SQL脚本放入`src/main/resources/db/migration/`目录
2. **手动初始化**: 执行提供的`V1.0__init_webrtc.sql`脚本

### 配置说明

#### 启用持久化

```yaml
plugin:
  webrtc:
    persistence:
      enabled: true  # 启用数据库持久化
      cleanup:
        expired-rooms-cron: "0 */10 * * * ?"  # 清理过期房间的Cron表达式
        empty-rooms-cron: "0 */5 * * * ?"     # 清理空房间的Cron表达式
        room-timeout-minutes: 60              # 房间超时时间（分钟）
```

#### 数据库连接

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/webrtc_db
    username: your_username
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 自动清理机制

组件提供两种自动清理机制：

1. **过期房间清理**: 定时清理超过指定时间未活跃的房间
2. **空房间清理**: 定时清理没有用户的房间

清理任务可以通过Cron表达式进行配置，也可以通过API手动触发。

## 客户端集成

### 前端技术栈支持

本WebRTC组件提供标准的REST API和Socket.IO接口，支持与各种前端技术栈集成：

- **原生JavaScript**: 使用fetch API调用REST接口，使用Socket.IO客户端库进行实时通信
- **Vue.js**: 通过axios或fetch调用API，集成Socket.IO进行状态管理
- **React**: 使用hooks管理WebRTC状态，结合Socket.IO实现实时功能
- **Angular**: 通过HttpClient调用REST API，使用Socket.IO服务进行通信
- **移动端**: 支持React Native、Flutter等移动端框架集成

### 集成步骤

1. **引入Socket.IO客户端库**

```html
<script src="/socket.io/socket.io.js"></script>
```

2. **获取WebRTC配置**

```javascript
fetch('/api/webrtc/config')
  .then(response => response.json())
  .then(config => {
    // 使用配置初始化WebRTC
  });
```

3. **建立Socket.IO连接**

```javascript
const socket = io();
```

4. **监听WebRTC事件**

```javascript
socket.on('joined', handleJoined);
socket.on('userJoined', handleUserJoined);
socket.on('offer', handleOffer);
socket.on('answer', handleAnswer);
socket.on('iceCandidate', handleIceCandidate);
```

### 核心功能实现

#### 房间管理

- 通过REST API创建和管理房间
- 支持房间ID和房间号两种标识方式
- 实时获取房间状态和用户列表

#### 媒体流处理

- 获取本地媒体流（摄像头、麦克风）
- 建立WebRTC对等连接
- 处理远程媒体流显示

#### 信令交换

- 通过Socket.IO进行实时信令交换
- 处理Offer/Answer协商
- ICE候选交换

### 最佳实践

1. **错误处理**: 妥善处理媒体设备访问失败、网络连接异常等情况
2. **资源清理**: 在组件卸载时清理WebRTC连接和媒体流
3. **状态管理**: 合理管理连接状态、用户列表等应用状态
4. **用户体验**: 提供连接状态指示、音视频控制等用户界面

## 错误处理和最佳实践

### 服务端错误处理

本模块提供了完善的错误处理机制：

1. **房间管理错误**
    - 房间不存在
    - 房间已满
    - 用户权限不足
    - 房间状态异常

2. **用户管理错误**
    - 用户已在房间中
    - 用户不在房间中
    - 用户状态异常
   - 重复用户连接处理

3. **数据库操作错误**
    - 连接超时
    - 数据完整性约束
    - 事务回滚

### 最佳实践

1. **资源管理**
    - 自动清理过期房间
    - 定期清理离线用户
    - 监控内存使用情况
   - 重复用户连接处理：确保一个房间中每个用户ID只有一个活跃连接

2. **性能优化**
    - 使用连接池管理数据库连接
    - 实现缓存机制减少数据库查询
    - 异步处理耗时操作

3. **安全考虑**
    - 输入参数验证
    - SQL注入防护
    - 访问权限控制

4. **监控和日志**
    - 记录关键操作日志
    - 监控系统性能指标
    - 异常情况告警

## 配置说明

### WebRTC配置属性

| 属性                                      | 类型      | 默认值                          | 说明           |
|-----------------------------------------|---------|------------------------------|--------------|
| `plugin.webrtc.enabled`                 | boolean | true                         | 是否启用WebRTC功能 |
| `plugin.webrtc.stun-server.url`         | String  | stun:stun.l.google.com:19302 | STUN服务器地址    |
| `plugin.webrtc.turn-server.url`         | String  | -                            | TURN服务器地址    |
| `plugin.webrtc.turn-server.username`    | String  | -                            | TURN服务器用户名   |
| `plugin.webrtc.turn-server.password`    | String  | -                            | TURN服务器密码    |
| `plugin.webrtc.room.max-rooms`          | int     | 100                          | 最大房间数量       |
| `plugin.webrtc.room.max-users-per-room` | int     | 10                           | 每个房间最大用户数    |
| `plugin.webrtc.room.timeout-minutes`    | int     | 60                           | 房间超时时间（分钟）   |

## 部署指南

### 开发环境部署

1. **添加依赖**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-webrtc-starter</artifactId>
    <version>4.0.0.33</version>
</dependency>
```

2. **配置数据库**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/webrtc_db
    username: your_username
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

3. **启动应用**

```bash
mvn spring-boot:run
```

### 生产环境部署

1. **Docker部署**

```dockerfile
FROM openjdk:17-jre-slim
COPY target/your-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

2. **环境变量配置**

```bash
# 数据库配置
SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/webrtc_db
SPRING_DATASOURCE_USERNAME=webrtc_user
SPRING_DATASOURCE_PASSWORD=your_password

# WebRTC配置
SPRING_WEBRTC_STUN_SERVER_URL=stun:stun.l.google.com:19302
SPRING_WEBRTC_ROOM_MAX_ROOMS=100
SPRING_WEBRTC_ROOM_MAX_USERS_PER_ROOM=10
```

3. **负载均衡配置**
    - 支持多实例部署
    - 使用Redis进行会话共享
    - 配置Nginx进行负载均衡

## 常见问题解答

### Q: 房间ID和房间号有什么区别？

A:

- **房间ID**: 字符串格式（如`abc12345`），适用于程序调用
- **房间号**: 9位数字格式（如`123456789`），基于雪花算法生成，便于用户记忆和输入
- 两者都可以用来标识和加入房间，选择哪种取决于使用场景

### Q: 房间号是如何生成的？

A: 房间号使用雪花算法生成，具有以下特点：

- 全局唯一性
- 按时间递增
- 高并发安全
- 固定9位数字长度

### Q: 可以自定义房间号吗？

A: 目前房间号由系统自动生成以保证唯一性。如需自定义，建议使用房间ID方式。

### Q: 如何处理房间满员的情况？

A: 系统会返回相应的错误码和提示信息，前端可以根据返回结果进行相应处理。

### Q: 数据库表结构可以自定义吗？

A: 可以通过配置文件自定义表名前缀，但不建议修改表结构，以免影响功能正常使用。

### Q: 支持多少人同时在线？

A: 取决于：

- 服务器性能
- 数据库连接数
- 内存使用情况
- 默认单个房间最多10人，可通过配置调整

### Q: 如何监控系统运行状态？

A: 模块提供了以下监控接口：

- 房间统计信息
- 用户在线状态
- 系统性能指标
- 可集成Spring Boot Actuator进行监控

### Q: 如何处理数据库连接异常？

A: 系统内置了连接池管理和异常重试机制，同时提供了详细的错误日志记录。

## 性能优化建议

### 服务端优化

1. **数据库连接池配置**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

2. **JVM参数优化**

```bash
java -Xms2g -Xmx4g -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+HeapDumpOnOutOfMemoryError \
     -jar your-app.jar
```

3. **缓存配置**

```yaml
spring:
  cache:
    type: redis
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

4. **异步处理配置**

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 8
        max-size: 16
        queue-capacity: 100
```

## 安全考虑

### 1. 身份验证和授权

- 支持集成Spring Security进行身份验证
- 提供基于角色的访问控制
- 支持JWT令牌验证

### 2. 数据安全

- 所有敏感数据加密存储
- 支持HTTPS传输加密
- 防止SQL注入攻击

### 3. 访问控制

- IP白名单/黑名单支持
- 请求频率限制
- 房间访问权限控制

### 4. 审计日志

- 记录所有关键操作
- 用户行为追踪
- 异常访问告警

## 监控和日志

### 应用监控

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

### 日志配置

```yaml
logging:
  level:
    com.chua.webrtc: DEBUG
    org.springframework.web.socket: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/webrtc.log
    max-size: 100MB
    max-history: 30
```

### 监控指标

- **房间相关指标**
    - 活跃房间数量
    - 房间创建/关闭次数
    - 房间平均使用时长

- **用户相关指标**
    - 在线用户数量
    - 用户加入/离开次数
    - 用户平均在线时长

- **系统性能指标**
    - 内存使用情况
    - 数据库连接池状态
    - API响应时间

## 注意事项

1. **数据库配置**: 确保数据库连接配置正确
2. **依赖版本**: 注意Spring Boot版本兼容性
3. **内存管理**: 定期清理过期房间和用户数据
4. **并发处理**: 高并发场景下注意线程安全
5. **日志级别**: 生产环境建议调整日志级别
6. **监控告警**: 配置适当的监控和告警机制

## 许可证

本项目采用 MIT 许可证。