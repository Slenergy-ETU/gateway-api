package com.slenergy.gateway.api.server.wifi;

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

public class WifiConnectorServer {


    private static final int PORT = 5566; // 监听的端口
    private static final int BUFFER_SIZE = 1024; // 缓冲区大小

    private static WifiConnectorServer instance; // 单例实例
    private Selector selector; // 选择器
    private ServerSocketChannel serverSocketChannel; // 服务器Socket通道
    private SocketChannel clientChannel; // 唯一的客户端通道
    private boolean running = false; // 服务器运行标志

    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();


    // 私有构造函数
//    private CanServer() {
//    }

    // 获取单例实例
    public static synchronized WifiConnectorServer getInstance() {
        if (instance == null) {
            instance = new WifiConnectorServer();
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

        System.out.println("服务器启动，监听端口: " + PORT);

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

            System.out.println("客户端连接: " + clientChannel.getRemoteAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void handleRead(SelectionKey key) {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                clientChannel.close();
                clientChannel = null;
                System.out.println("客户端已断开连接");
                return;
            }

            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            String receivedData = new String(data, "UTF-8");
            System.out.println("收到消息: " + receivedData);

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
            System.out.println("客户端未连接，无法发送消息");
        }
        // 发送消息
        sendResponse(message);
        // 等待响应
        String response = null;
        while (response == null) {
            response = responseQueue.poll(500, TimeUnit.MILLISECONDS); // 轮询队列
        }
        return response;
    }

    private void sendResponse(String response) throws IOException {
        if (clientChannel != null && clientChannel.isConnected()) {
            ByteBuffer buffer = ByteBuffer.wrap(response.getBytes("UTF-8"));
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer);
            }
            System.out.println("已发送消息: " + response);
        } else {
            System.out.println("无法发送响应，客户端未连接");
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
