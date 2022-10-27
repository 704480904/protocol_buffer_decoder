package com.ibiliang.protocol;

import com.google.common.base.Charsets;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Main {

    public static interface Listener {
        public void onComing(String nickname, String douyinNumber, String liveId);

        public void onSpeaking(String nickname, String douyinNumber);
    }

    private static int i = 0;

    public static void main(String[] args) throws IOException {
        System.out.println("Hello World");
//        FileInputStream fis = new FileInputStream("F:\\dy抓包\\74_.txt");
///Volumes/BLKJ-SSD/chatRoom/
///Volumes/BLKJ-SSD/contactBuffer/
///Volumes/BLKJ-SSD/contact/
        File file = new File("/Volumes/BLKJ-SSD/chatRoom/");
        File binFiles[] = file.listFiles();

        for (int i1 = 0; i1 < binFiles.length; i1++) {
            parseBinFile(binFiles[i1].getAbsolutePath());
        }

    }

    private static void parseBinFile(String binFile) throws IOException {
        System.out.println("=================" + binFile + "=================");
        FileInputStream fis = new FileInputStream(binFile);
        byte[] buf = new byte[2048];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len = 0;
        while ((len = fis.read(buf)) > 0) {
            baos.write(buf, 0, len);
        }
        String s = decodeProto(baos.toByteArray(), false, new String[]{"", ""}, new Listener() {

            public void onComing(String nickname, String douyinNumber, String liveId) {
                System.out.println(nickname + "." + douyinNumber + "." + liveId + "来了");
                //Listener.super.onComing(nickname, douyinNumber, liveId);
            }

            public void onSpeaking(String nickname, String douyinNumber) {

            }
        });
        System.out.println(s);


        System.out.println("=================" + binFile + "=================");
    }

    //main
    public static String decodeProto(byte[] data, boolean singleLine, String[] messageType, Listener listener) throws IOException {
        return decodeProto(ByteString.copyFrom(data), 0, singleLine, messageType, listener);
    }

    public static String decodeProto(ByteString data, int depth, boolean singleLine, String[] messageType, Listener listener) throws IOException {
        final CodedInputStream input = CodedInputStream.newInstance(data.asReadOnlyByteBuffer());
        return decodeProtoInput(input, depth, singleLine, messageType, listener);
    }

    /**
     * zyl's自定义流程
     *
     * @param number   数量
     * @param depth    深度
     * @param str      str
     * @param msgType  msg类型
     * @param listener
     */
    private static void customProcess(int number, int depth, String str, String[] msgType, Listener listener) {
        if (number == 2 && depth == 1) {
            System.out.println(str);
        }
        if (number == 12 && depth == 3) {
            System.out.println(str);
        }
        System.out.println(number + ":" + depth + ":" + str);
        if (number == 1 && depth == 1) {
            msgType[0] = str + i++; //WebcastChatMessage
//            System.out.println("msgType: " + str);
        }

        /*if(number == 1 && depth == 2){
            msgType[1] = str;
        }
*/

        if (msgType[0].startsWith("WebcastChatMessage")) {
            //System.out.println("msgType:" + msgType[0] + ",number:" + number + ",depth:" + depth + ",str:" + str);
            //昵称
            if (number == 3 && depth == 3) {
                System.out.print(str);
            }
            //账号
            if (number == 38 && depth == 3) {
                System.out.print("(" + str + ")");
            }
            //弹幕
            if (number == 3 && depth == 2) {
                System.out.println(":" + str);
            }
        }

        //进入房间消息
        if (msgType[0].startsWith("WebcastMemberMessage")) {
            if (number == 3 && depth == 3) {
                msgType[1] = str;
            }

            if (number == 1 && depth == 4 && str.equals("live_room_enter_toast")) {
                msgType[0] = "live_room_enter_toast";
            }
        }
        if (msgType[0].equals("live_room_enter_toast")) {
            //昵称
            if (number == 3 && depth == 7) {
                //System.out.print(str);
            }
            //账号
            if (number == 38 && depth == 7) {
                listener.onComing("", str, msgType[1]);
                //System.out.print("(" + str + ")");
            }
            if (number == 68 && depth == 7) {
                //System.out.println("来了");
                msgType[0] = "none";
            }
        }

    }

    private static String decodeProtoInput(CodedInputStream input, int depth, boolean singleLine, String[] msgType, Listener listener) throws IOException {
        StringBuilder s = new StringBuilder("{ ");
        boolean foundFields = false;
        while (true) {
            final int tag = input.readTag();
            int type = WireFormat.getTagWireType(tag);
            if (tag == 0 || type == WireFormat.WIRETYPE_END_GROUP) {
                break;
            }
            foundFields = true;
            protoNewline(depth, s, singleLine);

            final int number = WireFormat.getTagFieldNumber(tag);
            s.append(number).append(".").append(depth).append(": ");

            switch (type) {
                case WireFormat.WIRETYPE_VARINT:
                    long lng = input.readInt64();
                    customProcess(number, depth, String.valueOf(lng), msgType, listener);
                    s.append(lng);
                    break;
                case WireFormat.WIRETYPE_FIXED64:
                    s.append(Double.longBitsToDouble(input.readFixed64()));
                    break;
                case WireFormat.WIRETYPE_LENGTH_DELIMITED:
                    ByteString data = input.readBytes();
                    try {
                        String submessage = decodeProto(data, depth + 1, singleLine, msgType, listener);
                        if (data.size() < 30) {
                            boolean probablyString = true;
                            String str = new String(data.toByteArray(), Charsets.UTF_8);
                            for (char c : str.toCharArray()) {
                                if (c < '\n') {
                                    probablyString = false;
                                    break;
                                }
                            }
                            customProcess(number, depth, str, msgType, listener);
                            if (probablyString) {
                                s.append("\"").append(str).append("\" ");
                            }
                        }
                        s.append(submessage);
                    } catch (IOException e) {
                        String str = new String(data.toByteArray());
                        customProcess(number, depth, str, msgType, listener);
                        s.append('"').append(str).append('"');
                    }
                    break;
                case WireFormat.WIRETYPE_START_GROUP:
                    s.append(decodeProtoInput(input, depth + 1, singleLine, msgType, listener));
                    break;
                case WireFormat.WIRETYPE_FIXED32:
                    s.append(Float.intBitsToFloat(input.readFixed32()));
                    break;
                default:
                    throw new InvalidProtocolBufferException("Invalid wire type");
            }

        }
        if (foundFields) {
            protoNewline(depth - 1, s, singleLine);
        }
        return s.append('}').toString();
    }

    private static void protoNewline(int depth, StringBuilder s, boolean noNewline) {
        if (noNewline) {
            s.append(" ");
            return;
        }
        s.append('\n');
        for (int i = 0; i <= depth; i++) {
            s.append("\t");
        }
    }
}
