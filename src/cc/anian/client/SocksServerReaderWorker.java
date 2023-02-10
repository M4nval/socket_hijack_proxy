package cc.anian.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class SocksServerReaderWorker implements Runnable{

    private static final Map<String, SocketChannel> socketMap = new HashMap<>();

    @Override
    public void run() {
        while (true){
            try {
                String id = Utils.bytesToHex(Tunnel.readByLength(16));
                int length = Utils.bytes2Int(Tunnel.readByLength(4));
                if (length == 0){
                    if (socketMap.containsKey(id)){
                        socketMap.get(id).close();
                        socketMap.remove(id);
                    }
                    System.out.println("目标请求超时，断开链接 id=" + id);
                    continue;
                }
                byte[] data = Tunnel.readByLength(length);
                SocketChannel socketChannel = socketMap.get(id);
                if (socketChannel == null || !socketChannel.isOpen() || !socketChannel.isConnected()){
                    System.out.println("收到代理server的响应数据包，但客户端连接已关闭，此数据包将被丢弃，id=" + id + " length=" + length + " data=" + Utils.bytesToHex(data));
                    socketMap.remove(id);
                    continue;
                }
                socketChannel.write(ByteBuffer.wrap(data));
                System.out.println("收到代理server的响应数据包，转发给客户端，id=" + id + " length=" + length + " data=" + Utils.bytesToHex(data));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public static void registerSocket(String id, SocketChannel socket){
        socketMap.put(id, socket);
    }

    


}
