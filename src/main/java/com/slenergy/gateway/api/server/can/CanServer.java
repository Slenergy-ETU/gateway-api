package com.slenergy.gateway.api.server.can;

import com.slenergy.gateway.api.server.GWapi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CanServer {


    private static final int PORT = 5533; // 监听的端口
    private static final int BUFFER_SIZE = 1024; // 缓冲区大小

    private static CanServer instance; // 单例实例
    private Selector selector; // 选择器
    private ServerSocketChannel serverSocketChannel; // 服务器Socket通道
    private SocketChannel clientChannel; // 唯一的客户端通道
    private boolean running = false; // 服务器运行标志

    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    private final static Logger LOGGER = LogManager.getLogger(CanServer.class);


    // 私有构造函数
//    private CanServer() {
//    }

    // 获取单例实例
    public static synchronized CanServer getInstance() {
        if (instance == null) {
            instance = new CanServer();
        }
        return instance;
    }

    // 启动服务器
    public void start() throws IOException {
        if (running) return;
        running = true;

        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        LOGGER.info("服务器启动，监听端口: {}", PORT);

        new Thread(this::runServer).start();
    }

    // 服务器运行逻辑
    private void runServer() {
        while (running) {
            try {
                if (selector.select() == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (key.isAcceptable()) {
                        handleAccept(key);
                    }
                    if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void handleAccept(SelectionKey key) {
        try {
            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
            clientChannel = serverChannel.accept();
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);

            LOGGER.info("客户端连接: {}", clientChannel.getRemoteAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void handleRead(SelectionKey key) {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        if (clientChannel == null) {
            LOGGER.warn("客户端通道为空，可能已经断开连接");
            return;
        }
        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                clientChannel.close();
                clientChannel = null;
                LOGGER.info("客户端已断开连接");
                return;
            }

            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            String receivedData = new String(data, "UTF-8");
            LOGGER.info("收到消息: " + receivedData);

            // 将响应放入队列
            responseQueue.offer(receivedData);

        } catch (IOException e) {
            try {
                if (clientChannel != null) clientChannel.close();
                clientChannel = null;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public String sendMessageAndReceive(String message) throws IOException, InterruptedException {
        if (clientChannel == null || !clientChannel.isConnected()) {
            LOGGER.info("客户端未连接，无法发送消息");
        }
        // 发送消息
        sendResponse(message);
        String response = null;
        if (message.contains("type")) {

        }else {
            // 等待响应
            response = responseQueue.poll(15000, TimeUnit.MILLISECONDS); // 等待 10 秒响应
        }


        if (response == null) {
            LOGGER.warn("未收到客户端响应，继续运行");
            return null; // 没有响应时返回 null
        }
        return response;
    }

    private void sendResponse(String response) throws IOException {
        if (clientChannel != null && clientChannel.isConnected()) {
            ByteBuffer buffer = ByteBuffer.wrap(response.getBytes("UTF-8"));
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer);
            }
            LOGGER.info("已发送消息: {}", response);
        } else {
            LOGGER.info("无法发送响应，客户端未连接");
        }
    }

    public void stop() {
        running = false;
        try {
            if (clientChannel != null) clientChannel.close();
            if (selector != null) selector.close();
            if (serverSocketChannel != null) serverSocketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
