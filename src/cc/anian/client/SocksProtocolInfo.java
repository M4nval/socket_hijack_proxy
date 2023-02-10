package cc.anian.client;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.UUID;

public class SocksProtocolInfo {

    String host;
    Integer port;

    String id;

    byte[] data;

    private SocksProtocolInfo() {
    }

    public static SocksProtocolInfo build(SocketChannel clientSocket){
        SocksProtocolInfo socksProtocolInfo = new SocksProtocolInfo();
        try {
            // 链接主键
            socksProtocolInfo.id = UUID.randomUUID().toString().replace("-", "");
            // 解析socks协商过程，获取host和port
            parseSocksNegotiate(socksProtocolInfo, clientSocket);
            // 解析数据包，获取data
            socksProtocolInfo.data = parseSendData(clientSocket);

            if (socksProtocolInfo.data == null){
                System.out.println("代理请求数据包中没有请求数据，不需要处理。 id=" + socksProtocolInfo.id);
                return null;
            }
            System.out.println("解析代理请求数据包成功，即将发送给代理server，id=" + socksProtocolInfo.id + " host=" + socksProtocolInfo.host + " port=" + socksProtocolInfo.port + " length=" + + socksProtocolInfo.data.length + " data=" + Utils.bytesToHex(socksProtocolInfo.data));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return socksProtocolInfo;
    }

    public static byte[] parseSendData(SocketChannel socketChannel) throws IOException {
        byte[] result = null;
        ByteBuffer buf = ByteBuffer.allocate(2048);
        boolean hasRead = false;
        for (int length = socketChannel.read(buf); length >= 0; length = socketChannel.read(buf)){
            if (hasRead && length == 0){
                break;
            }
            if (length > 0){
                hasRead = true;
                byte[] dataChunk = Arrays.copyOfRange(buf.array(), 0, length);
                if (result == null){
                    result = dataChunk;
                } else {
                    result = Utils.mergeByteArray(result, dataChunk);
                }
                buf.clear();
            }
        }
        return result;
        /*if (socksProtocolInfo.data != null){
            //替换 Connection: keepAlive 为 Connection: close 防止socks server那边总是堵塞
            String s = new String(socksProtocolInfo.data).replaceAll("Connection: keep-alive", "Connection: close");
            socksProtocolInfo.data = s.getBytes(StandardCharsets.UTF_8);
            System.out.println("[conn replace]:" + s);
        }*/
    }

    private static void parseSocksNegotiate(SocksProtocolInfo socksProtocolInfo, SocketChannel socketChannel) throws Exception {
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);
        int read = 0;
        while (read == 0){
            read = socketChannel.read(byteBuffer);
        }
        byteBuffer.flip();
        int ver = byteBuffer.get();
        if (ver != 5){
            return;
        }
        int nmethods = byteBuffer.get();
        for(int i = 0; i < nmethods; ++i) {
            int var10 = byteBuffer.get();
        }
        byte[] authTypeConfirmMsg =new byte[]{5, 0};
        ByteBuffer sendBuff = ByteBuffer.allocate(14);
        sendBuff.clear();
        sendBuff.put(authTypeConfirmMsg);
        sendBuff.flip();
        while (sendBuff.hasRemaining()) {
            socketChannel.write(sendBuff);
        }
        byteBuffer.clear();
        read = 0;
        while (read == 0){
            read = socketChannel.read(byteBuffer);
        }
        byteBuffer.flip();

        int version = byteBuffer.get();
        int cmd;
        int rsv;
        int atyp;
        if (version == 2) {
            version = byteBuffer.get();
            cmd = byteBuffer.get();
            rsv = byteBuffer.get();
            atyp = byteBuffer.get();
        } else {
            cmd = byteBuffer.get();
            rsv = byteBuffer.get();
            atyp = byteBuffer.get();
        }
        byte[] targetPort = new byte[2];
        String host = "";
        byte[] target;
        if (atyp == 1) {
            target = new byte[4];
            for (int i = 0; i < 4; i++){
                target[i] = byteBuffer.get();
            }
            for (int i = 0; i < 2; i++){
                targetPort[i] = byteBuffer.get();
            }
            String[] tempArray = new String[4];

            int temp;
            for(int i = 0; i < target.length; ++i) {
                temp = target[i] & 255;
                tempArray[i] = temp + "";
            }

            String[] var23 = tempArray;
            temp = tempArray.length;

            for(int var16 = 0; var16 < temp; ++var16) {
                String tempstr = var23[var16];
                host = host + tempstr + ".";
            }

            host = host.substring(0, host.length() - 1);
        } else if (atyp == 3) {
            int targetLen = byteBuffer.get();
            target = new byte[targetLen];
            for (int i = 0; i < targetLen; i++){
                target[i] = byteBuffer.get();
            }
            for (int i = 0; i < 2; i++){
                targetPort[i] = byteBuffer.get();
            }
            host = new String(target);
        } else if (atyp == 4) {
            target = new byte[16];
            for (int i = 0; i < 16; i++){
                target[i] = byteBuffer.get();
            }
            for (int i = 0; i < 2; i++){
                targetPort[i] = byteBuffer.get();
            }
            host = Inet6Address.getByAddress(target).getHostAddress();
        }
        int port = (targetPort[0] & 255) * 256 + (targetPort[1] & 255);
        if (cmd != 2 && cmd != 3) {
            if (cmd == 1) {
                host = InetAddress.getByName(host).getHostAddress();
                try {
                    socketChannel.write(ByteBuffer.wrap(Utils.mergeByteArray(new byte[]{5, 0, 0, 1}, InetAddress.getByName(host).getAddress(), targetPort)));
                    System.out.println("socks代理协商完成，代理目标解析成功 host=" + host + " port=" + port);
                    socksProtocolInfo.host = host;
                    socksProtocolInfo.port = port;
                } catch (IOException e){
                    socketChannel.write(ByteBuffer.wrap(Utils.mergeByteArray(new byte[]{5, 5, 0, 1}, InetAddress.getByName(host).getAddress(), targetPort)));
                    throw new Exception(String.format("[%s:%d] Remote failed", host, port));
                }
            } else {
                throw new Exception("Socks5 - Unknown CMD");
            }
        } else {
            throw new Exception("not implemented");
        }

    }


    public byte[] getDatagramBytes(){
        return Utils.mergeByteArray(
                Utils.MAGIC_DATA_HEADER,
                id2Byte(),
                hostLength2Byte(),
                host2Byte(),
                port2Byte(),
                dataLength2byte(),
                this.data
        );
    }

    public byte[] dataLength2byte() {
        int length = this.data.length;
        return Utils.int2ByteArray(length);
    }

    private byte[] hostLength2Byte(){
        int length = host2Byte().length;
        return Utils.short2ByteArray((short) length);
    }

    private byte[] host2Byte(){
        return host.getBytes();
    }

    private byte[] port2Byte(){
        int length = this.port;
        return Utils.short2ByteArray((short) length);
    }

    private byte[] id2Byte(){
        return Utils.hexToByteArray(id);
    }
}
