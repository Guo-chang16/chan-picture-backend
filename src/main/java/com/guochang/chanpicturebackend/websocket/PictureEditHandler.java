package com.guochang.chanpicturebackend.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.guochang.chanpicturebackend.model.entity.User;
import com.guochang.chanpicturebackend.service.UserService;
import com.guochang.chanpicturebackend.websocket.disruptor.PictureEditEventProducer;
import com.guochang.chanpicturebackend.websocket.model.PictureEditActionEnum;
import com.guochang.chanpicturebackend.websocket.model.PictureEditMessageTypeEnum;
import com.guochang.chanpicturebackend.websocket.model.PictureEditRequestMessage;
import com.guochang.chanpicturebackend.websocket.model.PictureEditResponseMessage;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.guochang.chanpicturebackend.websocket.model.PictureEditMessageTypeEnum.ENTER_EDIT;

@Component
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    /**
     * 用户连接成功时调用,仅用于广播谁进入了会话
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        //保存对话到集合中
        User user = (User) session.getAttributes().get("user");
        Long PictureId = (Long) session.getAttributes().get("pictureId");
        pictureSessions.put(PictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(PictureId).add(session);
        //构造响应结果
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 加入编辑状态", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        //广播给 pictureId 的所有用户
        broadcastToPicture(PictureId, pictureEditResponseMessage, null);

    }

    /**
     * 处理前端消息
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        //获取消息
        String msg = message.getPayload();
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(msg, PictureEditRequestMessage.class);
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum messageTypeEnum = PictureEditMessageTypeEnum.valueOf(type);

        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        //发送消息给 disruptor
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    /**
     * 处理进入编辑状态的消息
     * @param session
     * @param pictureEditRequestMessage
     * @param user
     * @param pictureId
     * @throws Exception
     */
    public void handleEnterEditMessage(WebSocketSession session, PictureEditRequestMessage pictureEditRequestMessage, User user, Long pictureId) throws Exception {

        //判断当前图片是否正在被编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            pictureEditingUsers.put(pictureId, user.getId());
        }

        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(ENTER_EDIT.getValue());
        String message = String.format("用户 %s 正在进行编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        //当没有用户在进行具体编辑操作时，并不需要给EditAction赋值
        //pictureEditResponseMessage.setEditAction();
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        broadcastToPicture(pictureId, pictureEditResponseMessage);

    }

    public void handleEditActionMessage(WebSocketSession session, PictureEditRequestMessage pictureEditRequestMessage, User user, Long pictureId) throws Exception {
        //解析请求消息
        Long editUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);

        if (actionEnum == null) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            pictureEditResponseMessage.setMessage("编辑操作错误");
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
            return;
        }

        //校验操作权限，只有有权限的用户才可以进行具体操作
        if (editUserId != null && !editUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(ENTER_EDIT.getValue());
            String message = String.format("用户 %s 正在进行 %s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            //用户在进行具体编辑操作时，需要给EditAction赋值
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            //广播操作，除去当前对话
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    //主动点击 "退出编辑" 按钮
    public void handleExitEditMessage(WebSocketSession session, PictureEditRequestMessage pictureEditRequestMessage, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s 退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage, null);
        }
    }

    // 关闭连接(连接关闭导致被动退出)
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 移除当前用户的编辑状态
        handleExitEditMessage(session, null, user, pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        // 响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);

    }

    // 广播给 pictureId 的所有用户
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws Exception {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 创建 ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
            objectMapper.registerModule(module);
            // 序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除掉的 session 不发送
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    // 全部广播
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }


}
