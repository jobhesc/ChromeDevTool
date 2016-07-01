package com.example.hesc.chromedevtool;

import android.app.Application;
import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hesc on 16/6/27.
 */
public class MyApplication extends Application {
    private static final String TAG = "MyApplication";

    private static final String PAGE_ID = "1";

    private static final String PATH_PAGE_LIST = "/json";
    private static final String PATH_VERSION = "/json/version";
    private static final String PATH_ACTIVATE = "/json/activate/" + PAGE_ID;
    private static final String PATH_INSPECTOR = "/inspector";

    private static final String WEBKIT_REV = "@188492";
    private static final String WEBKIT_VERSION = "537.36 (" + WEBKIT_REV + ")";

    private static final String USER_AGENT = "test";
    private static final String PROTOCOL_VERSION = "1.1";

    private static final String HEADER_UPGRADE = "Upgrade";
    private static final String HEADER_CONNECTION = "Connection";
    private static final String HEADER_SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    private static final String HEADER_SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
    private static final String HEADER_SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String HEADER_SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";

    private static final String HEADER_UPGRADE_WEBSOCKET = "websocket";
    private static final String HEADER_CONNECTION_UPGRADE = "Upgrade";
    private static final String HEADER_SEC_WEBSOCKET_VERSION_13 = "13";

    @Override
    public void onCreate() {
        super.onCreate();
        listenChromeSocket();
    }

    private void listenChromeSocket(){
        Runnable socketLister = new Runnable() {
            @Override
            public void run() {
                try {
                    //LocalServerSocket的地址串必须包含devtools_remote
                    LocalServerSocket serverSocket = new LocalServerSocket("chromedevtool_devtools_remote");
                    while(!Thread.interrupted()){
                        //侦听本地socket接口数据
                        LocalSocket socket = serverSocket.accept();
                        //一旦socket接收到数据,则
                        Thread workThread = new WorkThread(getApplicationContext(), socket);
                        workThread.setDaemon(true);
                        workThread.start();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        new Thread(socketLister).start();
    }


    private static class WorkThread extends Thread {
        private LocalSocket mSocket;
        private Context mContext;

        public WorkThread(Context context, LocalSocket socket){
            mSocket = socket;
            mContext = context.getApplicationContext();
        }

        @Override
        public void run() {
            SocketRequest request = readRequest();
            if(request == null) return;

            try {
                //根据request返回不同的response
                if(PATH_VERSION.equals(request.path)){
                    handleVersion();
                } else if(PATH_PAGE_LIST.equals(request.path)){
                    handlePageList();
                } else if(PATH_ACTIVATE.equals(request.path)){
                    handleActivate();
                } else if(PATH_INSPECTOR.equals(request.path)){
                    handleInspector();
                }
            } catch (Throwable e){
                e.printStackTrace();
            } finally{
                try {
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleInspector(){
            try {
                InputStream in = new BufferedInputStream(mSocket.getInputStream());
                byte firstByte = readByteOrThrow(in);
                byte opcode = (byte)(firstByte & 0xf);
                byte maskAndFirstLengthBits = readByteOrThrow(in);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleActivate() {
            writeResponse("Target activation ignored\n", "text/plain");
        }

        private void handlePageList() {
            try {
                JSONArray reply = new JSONArray();
                JSONObject page = new JSONObject();
                page.put("type", "app");
                page.put("title", "chromeDevTools1.0(powered by test)");
                page.put("id", PAGE_ID);
                page.put("description", "");

                page.put("webSocketDebuggerUrl", "ws:///inspector");
                Uri chromeFrontendUrl = new Uri.Builder()
                        .scheme("http")
                        .authority("chrome-devtools-frontend.appspot.com")
                        .appendEncodedPath("serve_rev")
                        .appendEncodedPath(WEBKIT_REV)
                        .appendEncodedPath("devtools.html")
                        .appendQueryParameter("ws", "/inspector")
                        .build();
                page.put("devtoolsFrontendUrl", chromeFrontendUrl.toString());

                reply.put(page);

                writeResponse(reply.toString(), "application/json");
            } catch(JSONException e){
                e.printStackTrace();
            }
        }

        private void handleVersion() {
            try {
                JSONObject reply = new JSONObject();
                reply.put("WebKit-Version", WEBKIT_VERSION);
                reply.put("User-Agent", USER_AGENT);
                reply.put("Protocol-Version", PROTOCOL_VERSION);
                reply.put("Browser", "chromeDevTools\\/1.0");
                reply.put("Android-Package", mContext.getPackageName());

                writeResponse(reply.toString(), "application/json");
            } catch (JSONException e){
                e.printStackTrace();
            }
        }

        private static byte readByteOrThrow(InputStream in) throws IOException {
            int b = in.read();
            if (b == -1) {
                throw new EOFException();
            }
            return (byte)b;
        }

        private static void readBytesOrThrow(InputStream in, byte[] buf, int offset, int count)
                throws IOException {
            while (count > 0) {
                int n = in.read(buf, offset, count);
                if (n == -1) {
                    throw new EOFException();
                }
                count -= n;
                offset += n;
            }
        }

        private void writeResponse(String body, String contentType){
            try {
                byte[] bytes = body.getBytes("UTF-8");
                OutputStream out = mSocket.getOutputStream();

                HttpMessageWriter writer = new HttpMessageWriter(new BufferedOutputStream(out));
                writer.writeLine("HTTP/1.1 200 OK");
                writer.writeLine("Content-Type:" + contentType);
                writer.writeLine("Content-Length:" + bytes.length);
                writer.writeLine();
                writer.flush();
//                writer.close();

                out.write(bytes);
//                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private boolean isSupportableUpgradeRequest(SocketRequest request) {
            return HEADER_UPGRADE_WEBSOCKET.equalsIgnoreCase(request.headers.get(HEADER_UPGRADE)) &&
                    HEADER_CONNECTION_UPGRADE.equals(request.headers.get(HEADER_CONNECTION)) &&
                    HEADER_SEC_WEBSOCKET_VERSION_13.equals(request.headers.get(HEADER_SEC_WEBSOCKET_VERSION));
        }

        private SocketRequest readRequest(){
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                String line;
                int index=0;
                SocketRequest request = new SocketRequest();
                while(!(line = in.readLine()).equals("")){
                    Log.i(TAG, "socket request: " + line);
                    if(index == 0){
                        //socket请求的内容类似于以下格式: GET /json/version http1.1
                        String[] requestParts = line.split(" ", 3);
                        if (requestParts.length != 3) {
                            throw new IOException("Invalid request line: " + line);
                        }

                        request.method = requestParts[0];
                        request.path = requestParts[1];
                        request.protocol = requestParts[2];
                        request.headers = new HashMap<>();
                    } else {
                        String[] headerParts = line.split(": ", 2);
                        if (headerParts.length != 2) {
                            throw new IOException("Malformed header: " + line);
                        }
                        String name = headerParts[0];
                        String value = headerParts[1];

                        request.headers.put(name, value);
                    }
                    index++;
                }

                return request;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static class SocketRequest {
        public String path;
        public String protocol;
        public String method;

        public Map<String, String> headers;
    }

    private static class HttpMessageWriter {
        private final BufferedOutputStream mOut;
        private static final byte[] CRLF = "\r\n".getBytes();

        public HttpMessageWriter(BufferedOutputStream out) {
            mOut = out;
        }

        public void writeLine(String line) throws IOException {
            for (int i = 0, N = line.length(); i < N; i++) {
                char c = line.charAt(i);
                mOut.write((int) c);
            }
            mOut.write(CRLF);
        }

        public void writeLine() throws IOException {
            mOut.write(CRLF);
        }

        public void flush() throws IOException {
            mOut.flush();
        }
    }
}
