package cc.anian.client;


public class Utils {

    public static final byte[] MAGIC_DATA_HEADER = new byte[]{0x63, 0x48, 0x4a, 0x76, 0x65, 0x48, 0x68, 0x34, 0x65, 0x51, 0x3d, 0x3d};
    public static final String MAGIC_SEND_AGAIN_FLAG = "cCcC";

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
        byte[] b = new byte[2];
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

    public static byte[] hexToByteArray(String inHex){
        int hexlen = inHex.length();
        byte[] result;
        if (hexlen % 2 == 1){
            //奇数
            hexlen++;
            result = new byte[(hexlen/2)];
            inHex="0"+inHex;
        }else {
            //偶数
            result = new byte[(hexlen/2)];
        }
        int j=0;
        for (int i = 0; i < hexlen; i+=2){
            result[j]=(byte)Integer.parseInt(inHex.substring(i,i+2), 16);
            j++;
        }
        return result;
    }


}
