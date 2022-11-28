import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.*;
import java.io.*;
public class HTTPProxy {
    public static void main(String[] args) throws IOException {
        //监听端口
        ServerSocket serverSocket = new ServerSocket(8341);
        int i = 1;
        while (true) {
            new myProxy(serverSocket.accept()).start();
            System.out.println("第" + i + "个线程启动");
            i++;
        }
    }
    static class myProxy extends Thread {

        private Socket socket;
        //OutputStream clientOutput = null;
        //InputStream clientInput = null;
        //Socket proxySocket = null;
        //InputStream proxyInput = null;
        //OutputStream proxyOutput = null;
        public myProxy(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            OutputStream clientOutput = null;
            InputStream clientInput = null;
            Socket proxySocket = null;
            InputStream proxyInput = null;
            OutputStream proxyOutput = null;
            try {
                clientInput = socket.getInputStream();
                clientOutput = socket.getOutputStream();
                BufferedReader buffer = new BufferedReader(new InputStreamReader(clientInput));
                String line;
                String host = "";
                StringBuilder headStr = new StringBuilder();
                File file = new File("cache.properties");
                if (!file.exists()) {
                    file.createNewFile();
                }
                FileInputStream fileInputStream = new FileInputStream("cache.properties");
                FileOutputStream fileOutputStream = new FileOutputStream("cache.properties", true);
                //将缓存保存到props中
                Properties props= new Properties();
                props.load(fileInputStream);
                //ArrayList<String> cache = readCache(fileInputStream);

                //读取HTTP请求头，并拿到HOST请求头和method
                while (null != (line = buffer.readLine())) {
                    System.out.println(line);
                    headStr.append(line + "\r\n");
                    if (line.length() == 0) {
                        break;
                    } else {
                        String[] temp = line.split(" ");
                        if (temp[0].contains("Host")) {
                            host = temp[1];
                        }
                    }
                }
                String type = headStr.substring(0, headStr.indexOf(" "));//获取请求的类型
                String firstLine = headStr.substring(0, headStr.indexOf("\r\n"));//获取请求的首行
                String url = firstLine.split(" ")[1];
                System.out.println("首行是"+firstLine);
                //根据host头解析出目标服务器的host和port
                String[] hostTemp = host.split(":");
                host = hostTemp[0];
                int port = 80;
                if (hostTemp.length > 1) {
                    port = Integer.valueOf(hostTemp[1]);
                }
                //连接到目标服务器
                proxySocket = new Socket(host, port);
                proxyInput = proxySocket.getInputStream();
                proxyOutput = proxySocket.getOutputStream();
                //根据HTTP method来判断是https还是http请求
                if ("CONNECT".equalsIgnoreCase(type)) {//https先建立隧道
                    clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                    clientOutput.flush();
                    System.out.println("回复成功");
                } else if ("GET".equalsIgnoreCase(type)) { //get请求则进行缓存操作
                    String content = props.getProperty(url);
                    if(content == null) {
                        System.out.println("不存在缓存");
                        //直接将请求头转发给目标服务器
                        proxyOutput.write(headStr.toString().getBytes());
                        //接受目标服务器相应并转发给客户端并收集response
                        String response = waitServerAndTransfer(clientOutput, proxyInput);
                        //如果存在Last-Modified,则记录缓存
                        System.out.println(response);
                        //props.setProperty(url, response);
                        if(response.contains("Last-Modified")) {
                            props.setProperty(url, response);
                        }
                    } else { //存在缓存
                        String modifyTime = findLastModifyTime(content);
                        //  构造条件GET请求
                        StringBuffer ifModify = new StringBuffer();
                        ifModify.append(firstLine+ "\r\n");
                        ifModify.append("Host: " + host + ":" + port + "\r\n");
                        ifModify.append("If-modified-since: " + modifyTime + "\r\n");
                        ifModify.append("\r\n");
                        String ifStr = ifModify.toString();
                        proxyOutput.write(ifStr.getBytes());
                        proxyOutput.flush();
                        BufferedReader buffer_res = new BufferedReader(new InputStreamReader(proxyInput));
                        String res = buffer_res.readLine();
                        System.out.println("受到服务器返回的消息："+res);
                        if(res.contains("304")) { //缓存命中
                            System.out.println("缓存命中");
                            clientOutput.write(content.getBytes());
                            clientOutput.flush();
                        } else { //缓存失效
                            System.out.println("缓存失效");
                            proxyOutput.write(headStr.toString().getBytes());
                            //接受目标服务器相应并转发给客户端并收集response
                            String response = waitServerAndTransfer(clientOutput, proxyInput);
                            //如果存在Last-Modified,则更新缓存
                            if(response.contains("Last-Modified")) {
                                props.setProperty(url, response);
                            }
                        }
                    }
                    //将更新后的缓存写回文件
                    //props.setProperty("1", "2");
                    //props.forEach((k, v) -> System.out.println(k + ": " + v) );
                    props.store(fileOutputStream, null);
                    System.out.println("缓存保存成功");
                } else {//http直接将请求头转发
                    proxyOutput.write(headStr.toString().getBytes());
                }
                //新开线程转发客户端请求至目标服务器
                new BlindlyForward(clientInput, proxyOutput).start();
                //转发目标服务器响应至客户端
                while (true) {
                    clientOutput.write(proxyInput.read());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (proxyInput != null) {
                    try {
                        proxyInput.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (proxyOutput != null) {
                    try {
                        proxyOutput.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (proxySocket != null) {
                    try {
                        proxySocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (clientInput != null) {
                    try {
                        clientInput.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (clientOutput != null) {
                    try {
                        clientOutput.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }
    //对于connect请求建立连接后进行盲转发
    static class BlindlyForward extends Thread {

        private InputStream input;
        private OutputStream output;

        public BlindlyForward(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        public void run() {
            try {
                while (true) {
                    output.write(input.read());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    //寻找缓存响应内容中上次修改的时间
    private static String findLastModifyTime(String content) {
        String LastModifyTime = null;
        String[] temp = content.split("\r\n");
        for(String iter : temp) {
            if(iter.startsWith("Last-Modified")) {
                LastModifyTime = iter.substring(15);
            }
        }
        System.out.println("上次修改时间"+ LastModifyTime);
        return LastModifyTime;
    }
    //等待服务器响应并转发给客户端并收集响应结果然后返回
    private static String waitServerAndTransfer(OutputStream clientOutput, InputStream proxyInput) throws IOException {
        List<byte[]> response = new ArrayList<>();
        byte[] bytes = new byte[2048];
        int length = 0;
        String content;
        while(true) {
            if ((length = proxyInput.read(bytes)) >= 0) {
                //  写回给客户端
                clientOutput.write(bytes, 0, length);
                //clientOutput.flush();
                //String show_response=new String(bytes,0,bytes.length);
                //System.out.println("服务器发回的消息是:\n---\n"+show_response+"\n---");
                //System.out.println(bytes.length);
                //  收集响应结果
                byte[] part = new byte[length];
                System.arraycopy(bytes, 0, part, 0, length);
                response.add(part);
                continue;
            }
            break;
        }

        //  将响应结果返回
        //List<Byte> list = new LinkedList<>();
        byte[] result = response.get(0);
        for(int i = 1; i < response.size(); i++) {
            result = byteMerger(result, response.get(i));
        }
        if(result == null) {
            content = null;
        }
        content = new String(result);
        //System.out.println("收到回复是："+content);
        return content;

    }
    //合并byte[]
    private static byte[] byteMerger(byte[] bt1, byte[] bt2){
        byte[] bt3 = new byte[bt1.length+bt2.length];
        System.arraycopy(bt1, 0, bt3, 0, bt1.length);
        System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
        return bt3;
    }
}
