package com.slenergy.gateway.api.server.device;

import com.slenergy.gateway.ems.Load;
import com.slenergy.gateway.ems.Power;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * class Communication description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-03-20
 * @since 1.0
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class Communicator extends Load {

    protected int capacity;
    protected int timeout;
    protected int port;
    protected String ip;
    private SocketChannel channel;

    public Communicator() {
        capacity = 1024;
        timeout = 5000;
        port = 8080;
        ip = "127.0.0.1";
        channel = null;
    }

    public Communicator(int capacity, int timeout) {
        this.capacity = capacity;
        this.timeout = timeout;
        port = 8080;
        ip = "127.0.0.1";
        channel = null;
    }

    public Communicator(int capacity, int timeout, int port, String ip) {
        this.capacity = capacity;
        this.timeout = timeout;
        this.port = port;
        this.ip = ip;
        channel = null;
    }

    public Communicator(Power power, int pvPriority, double batteryPowerLimited, double utilityPowerLimited, int capacity, int timeout, int port, String ip) {
        super(power, pvPriority, batteryPowerLimited, utilityPowerLimited);
        this.capacity = capacity;
        this.timeout = timeout;
        this.port = port;
        this.ip = ip;
        channel = null;
    }

    public void connect() throws IOException {
        channel = SocketChannel.open(new InetSocketAddress(ip, port));
        channel.configureBlocking(true);
    }

    public void disconnect() throws IOException {
        channel.close();
    }

    public JsonObject sendCommand(JsonObject param) throws IOException {
        System.out.println("ip: " + ip +"port: " + port);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.clear();
        buffer.put(param.toString().getBytes());
        buffer.flip();
        int len = channel.write(buffer);
        if (len <= 0) {
            disconnect();
            throw new IOException("unable to write data");
        }
        buffer.clear();
        len = channel.read(buffer);
        if (len <= 0) {
            disconnect();
            throw new IOException("unable to read data");
        }

        return new JsonObject(new String(buffer.array(), 0, len));
    }

    public String sendCommand(String commandStr) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.clear();
        buffer.put(commandStr.getBytes());
        buffer.flip();
        int len = channel.write(buffer);
        if (len <= 0) {
            disconnect();
            throw new IOException("unable to write data");
        }
        buffer.clear();
        len = channel.read(buffer);
        if (len <= 0) {
            disconnect();
            throw new IOException("unable to read data");
        }
        return new String(buffer.array(), 0, len);
    }

}
