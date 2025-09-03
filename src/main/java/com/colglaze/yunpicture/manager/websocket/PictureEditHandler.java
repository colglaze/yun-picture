//package com.colglaze.yunpicture.manager.websocket;
//
//import cn.hutool.core.collection.CollUtil;
//import cn.hutool.json.JSONUtil;
//import com.colglaze.yunpicture.manager.websocket.disruptor.PictureEditEventProducer;
//import com.colglaze.yunpicture.manager.websocket.model.PictureEditActionEnum;
//import com.colglaze.yunpicture.manager.websocket.model.PictureEditMessageTypeEnum;
//import com.colglaze.yunpicture.manager.websocket.model.PictureEditRequestMessage;
//import com.colglaze.yunpicture.manager.websocket.model.PictureEditResponseMessage;
//import com.colglaze.yunpicture.model.entity.User;
//import com.colglaze.yunpicture.service.UserService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.module.SimpleModule;
//import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//
//import java.io.IOException;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class PictureEditHandler extends TextWebSocketHandler {
//
//    private final UserService userService;
//    private final PictureEditEventProducer pictureEditEventProducer;
//
//    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
//    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();
//
//    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
//    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();
//
//    /**
//     * 连接建立成功
//     *
//     * @param session
//     * @throws Exception
//     */
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        super.afterConnectionEstablished(session);
//        // 保存会话到集合中
//        User user = (User) session.getAttributes().get("user");
//        Long pictureId = (Long) session.getAttributes().get("pictureId");
//        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
//        pictureSessions.get(pictureId).add(session);
//        // 构造响应，发送加入编辑的消息通知
//        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
//        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
//        String message = String.format("用户 %s 加入编辑", user.getUserName());
//        pictureEditResponseMessage.setMessage(message);
//        pictureEditResponseMessage.setUser(userService.getUserVO(user));
//        // 广播给所有用户
//        broadcastToPicture(pictureId, pictureEditResponseMessage);
//    }
//
//    /**
//     * 收到前端发送的消息，根据消息类别处理消息
//     *
//     * @param session
//     * @param message
//     * @throws Exception
//     */
//    @Override
//    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        super.handleTextMessage(session, message);
//        // 获取消息内容，将 JSON 转换为 PictureEditRequestMessage
//        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
//        // 从 Session 属性中获取到公共参数
//        User user = (User) session.getAttributes().get("user");
//        Long pictureId = (Long) session.getAttributes().get("pictureId");
//        // 根据消息类型处理消息（生产消息到 Disruptor 环形队列中）
//        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
//    }
//
//    /**
//     * 进入编辑状态
//     *
//     * @param pictureEditRequestMessage
//     * @param session
//     * @param user
//     * @param pictureId
//     */
//    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
//        // 没有用户正在编辑该图片，才能进入编辑
//        if (!pictureEditingUsers.containsKey(pictureId)) {
//            // 设置用户正在编辑该图片
//            pictureEditingUsers.put(pictureId, user.getId());
//            // 构造响应，发送加入编辑的消息通知
//            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
//            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
//            String message = String.format("用户 %s 开始编辑图片", user.getUserName());
//            pictureEditResponseMessage.setMessage(message);
//            pictureEditResponseMessage.setUser(userService.getUserVO(user));
//            // 广播给所有用户
//            broadcastToPicture(pictureId, pictureEditResponseMessage);
//        }
//    }
//
//    /**
//     * 处理编辑操作
//     *
//     * @param pictureEditRequestMessage
//     * @param session
//     * @param user
//     * @param pictureId
//     */
//    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
//        // 正在编辑的用户
//        Long editingUserId = pictureEditingUsers.get(pictureId);
//        String editAction = pictureEditRequestMessage.getEditAction();
//        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
//        if (actionEnum == null) {
//            log.error("无效的编辑动作");
//            return;
//        }
//        // 确认是当前的编辑者
//        if (editingUserId != null && editingUserId.equals(user.getId())) {
//            // 构造响应，发送具体操作的通知
//            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
//            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
//            String message = String.format("%s 执行 %s", user.getUserName(), actionEnum.getText());
//            pictureEditResponseMessage.setMessage(message);
//            pictureEditResponseMessage.setEditAction(editAction);
//            pictureEditResponseMessage.setUser(userService.getUserVO(user));
//            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
//            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
//        }
//    }
//
//
//    /**
//     * 退出编辑状态
//     *
//     * @param pictureEditRequestMessage
//     * @param session
//     * @param user
//     * @param pictureId
//     */
//    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
//        // 正在编辑的用户
//        Long editingUserId = pictureEditingUsers.get(pictureId);
//        // 确认是当前的编辑者
//        if (editingUserId != null && editingUserId.equals(user.getId())) {
//            // 移除用户正在编辑该图片
//            pictureEditingUsers.remove(pictureId);
//            // 构造响应，发送退出编辑的消息通知
//            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
//            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
//            String message = String.format("用户 %s 退出编辑图片", user.getUserName());
//            pictureEditResponseMessage.setMessage(message);
//            pictureEditResponseMessage.setUser(userService.getUserVO(user));
//            broadcastToPicture(pictureId, pictureEditResponseMessage);
//        }
//    }
//
//    /**
//     * 关闭连接
//     *
//     * @param session
//     * @param status
//     * @throws Exception
//     */
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//        super.afterConnectionClosed(session, status);
//        // 从 Session 属性中获取到公共参数
//        User user = (User) session.getAttributes().get("user");
//        Long pictureId = (Long) session.getAttributes().get("pictureId");
//        // 移除当前用户的编辑状态
//        handleExitEditMessage(null, session, user, pictureId);
//        // 删除会话
//        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
//        if (sessionSet != null) {
//            sessionSet.remove(session);
//            if (sessionSet.isEmpty()) {
//                pictureSessions.remove(pictureId);
//            }
//        }
//        // 通知其他用户，该用户已经离开编辑
//        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
//        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
//        String message = String.format("用户 %s 离开编辑", user.getUserName());
//        pictureEditResponseMessage.setMessage(message);
//        pictureEditResponseMessage.setUser(userService.getUserVO(user));
//        broadcastToPicture(pictureId, pictureEditResponseMessage);
//    }
//
//    /**
//     * 广播给该图片的所有用户（支持排除掉某个 Session）
//     *
//     * @param pictureId
//     * @param pictureEditResponseMessage
//     * @param excludeSession
//     */
//    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws IOException {
//        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
//        if (CollUtil.isNotEmpty(sessionSet)) {
//            // 创建 ObjectMapper
//            ObjectMapper objectMapper = new ObjectMapper();
//            // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
//            SimpleModule module = new SimpleModule();
//            module.addSerializer(Long.class, ToStringSerializer.instance);
//            module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
//            objectMapper.registerModule(module);
//            // 序列化为 JSON 字符串
//            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
//            TextMessage textMessage = new TextMessage(message);
//            for (WebSocketSession session : sessionSet) {
//                // 排除掉的 session 不发送
//                if (excludeSession != null && session.equals(excludeSession)) {
//                    continue;
//                }
//                if (session.isOpen()) {
//                    session.sendMessage(textMessage);
//                }
//            }
//        }
//    }
//
//    /**
//     * 广播给该图片的所有用户
//     *
//     * @param pictureId
//     * @param pictureEditResponseMessage
//     */
//    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws IOException {
//        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
//    }
//
//}
package com.colglaze.yunpicture.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.colglaze.yunpicture.manager.websocket.disruptor.PictureEditEventProducer;
import com.colglaze.yunpicture.manager.websocket.model.PictureEditActionEnum;
import com.colglaze.yunpicture.manager.websocket.model.PictureEditMessageTypeEnum;
import com.colglaze.yunpicture.manager.websocket.model.PictureEditRequestMessage;
import com.colglaze.yunpicture.manager.websocket.model.PictureEditResponseMessage;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    private final UserService userService;
    private final PictureEditEventProducer pictureEditEventProducer;

    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    // 会话锁，确保对同一会话的发送操作是线程安全的
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    // 用于延迟广播的线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    /**
     * 连接建立成功
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        // 保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);

        // 为每个会话创建锁对象
        sessionLocks.putIfAbsent(session.getId(), new Object());

        // 延迟发送欢迎消息，确保连接完全建立
        scheduler.schedule(() -> {
            try {
                // 构造响应，发送加入编辑的消息通知
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
                String message = String.format("用户 %s 加入编辑", user.getUserName());
                pictureEditResponseMessage.setMessage(message);
                pictureEditResponseMessage.setUser(userService.getUserVO(user));

                // 安全地广播给所有用户
                safeBroadcastToPicture(pictureId, pictureEditResponseMessage, null);
            } catch (Exception e) {
                log.error("Error sending welcome message to session: {}", session.getId(), e);
            }
        }, 100, TimeUnit.MILLISECONDS); // 延迟100ms发送
    }

    /**
     * 收到前端发送的消息，根据消息类别处理消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        // 获取消息内容，将 JSON 转换为 PictureEditRequestMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 从 Session 属性中获取到公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 根据消息类型处理消息（生产消息到 Disruptor 环形队列中）
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    /**
     * 进入编辑状态
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置用户正在编辑该图片
            pictureEditingUsers.put(pictureId, user.getId());
            // 构造响应，发送加入编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("用户 %s 开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给所有用户
            safeBroadcastToPicture(pictureId, pictureEditResponseMessage, null);
        }
    }

    /**
     * 处理编辑操作
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 正在编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            log.error("无效的编辑动作");
            return;
        }
        // 确认是当前的编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 构造响应，发送具体操作的通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s 执行 %s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            safeBroadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 退出编辑状态
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 正在编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        // 确认是当前的编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除用户正在编辑该图片
            pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("用户 %s 退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            safeBroadcastToPicture(pictureId, pictureEditResponseMessage, null);
        }
    }

    /**
     * 关闭连接
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        // 从 Session 属性中获取到公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        // 移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        // 移除会话锁
        sessionLocks.remove(session.getId());

        // 通知其他用户，该用户已经离开编辑
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        safeBroadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    /**
     * 安全地发送消息到单个会话
     */
    private void safeSendMessage(WebSocketSession session, TextMessage textMessage) {
        Object lock = sessionLocks.get(session.getId());
        if (lock == null) {
            return; // 会话已关闭或锁已被移除
        }

        synchronized (lock) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (IllegalStateException e) {
                log.warn("Session {} is in invalid state during send: {}", session.getId(), e.getMessage());
            } catch (IOException e) {
                log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * 安全地广播给该图片的所有用户（支持排除掉某个 Session）
     */
    private void safeBroadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isEmpty(sessionSet)) {
            return;
        }

        try {
            // 创建 ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(module);

            // 序列化为 JSON 字符串（只序列化一次，提高性能）
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);

            // 并行流发送消息（线程安全）
            sessionSet.parallelStream().forEach(session -> {
                if (excludeSession != null && session.equals(excludeSession)) {
                    return; // 排除指定会话
                }
                safeSendMessage(session, textMessage);
            });

        } catch (Exception e) {
            log.error("Error broadcasting message for picture {}: {}", pictureId, e.getMessage());
        }
    }

    /**
     * 安全地广播给该图片的所有用户
     */
    private void safeBroadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) {
        safeBroadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    /**
     * 销毁方法，清理资源
     */
    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}