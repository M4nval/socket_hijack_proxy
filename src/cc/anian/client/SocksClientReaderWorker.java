package cc.anian.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocksClientReaderWorker implements Runnable{

    private SocketChannel socket;

    public SocksClientReaderWorker(SocketChannel socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        SocksProtocolInfo socksProtocolInfo = SocksProtocolInfo.build(socket);
        if (socksProtocolInfo == null){
            return;
        }
        byte[] datagramBytes = socksProtocolInfo.getDatagramBytes();

        try {
            Tunnel.write(ByteBuffer.wrap(datagramBytes));
            //将数据包注册到readerWorker，readerWorker统一处理代理server的所有响应
            SocksServerReaderWorker.registerSocket(socksProtocolInfo.id, socket);
            System.out.println("已发送首次代理数据包到代理server，等待readerWorker处理代理server的响应 id=" + socksProtocolInfo.id);
            //还需要继续监听客户端的后续请求（例如https协议，需要客户端多次交互）
            while (socket.isOpen() && socket.isConnected() && socket.finishConnect()){
                byte[] data = SocksProtocolInfo.parseSendData(socket);
                if (data != null){
                    byte[] sendData = Utils.mergeByteArray(
                            Utils.MAGIC_DATA_HEADER,
                            Utils.hexToByteArray(socksProtocolInfo.id),
                            Utils.MAGIC_SEND_AGAIN_FLAG.getBytes(),
                            Utils.int2ByteArray(data.length),
                            data
                    );
                    Tunnel.write(ByteBuffer.wrap(sendData));
                    System.out.println("已发送后续代理数据包到代理server，等待readerWorker处理代理server的响应 id=" + socksProtocolInfo.id + " data=" + Utils.bytesToHex(sendData));
                }
            }
        } catch (IOException e) {
            System.out.println("客户端socket已关闭");
            try {
                socket.close();
            } catch (IOException ex) {
            }
        }
    }

    public static byte[] readByLength(SocketChannel socket, int readLength) throws IOException {
        ByteBuffer byteBuf = ByteBuffer.allocate(readLength);
        int read = 0;
        while (read < readLength){
            read += socket.read(byteBuf);
        }
        return byteBuf.array();
    }
}
