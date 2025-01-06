package com.zxj.utils;

import com.jcraft.jsch.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

public class SSHUtils {

    public static void main(String[] args) throws JSchException, IOException {
        DestHost host = new DestHost();
        SSHUtils.execCommandByChannelShell(SSHUtils.getJSchSession(host));
        System.out.println(SSHUtils.execCommandByChannelExec(SSHUtils.getJSchSession(host), "ls", ENCODING));
    }

    private static final String ENCODING = "UTF-8";

    public static Session getJSchSession(DestHost destHost) throws JSchException {
        JSch jsch = new JSch();

        Session session = jsch.getSession(destHost.getUsername(), destHost.getHost(), destHost.getPort());
        session.setPassword(destHost.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");//第一次访问服务器不用输入yes
        session.setTimeout(destHost.getTimeout());
        session.connect();

        return session;
    }

    public static String execCommandByChannelExec(Session session, String command, String resultEncoding) throws IOException,JSchException {
//1.默认方式，执行单句命令
        ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
        InputStream in = channelExec.getInputStream();
        channelExec.setCommand(command);
        channelExec.setErrStream(System.err);
        channelExec.connect();
        String result = IOUtils.toString(in, resultEncoding);
        channelExec.disconnect();

        return result;
    }

    public static String execCommandByChannelShell(Session session) throws IOException, JSchException, IOException {
        String result = "";

//2.尝试解决 远程ssh只能执行一句命令的情况
        ChannelShell channelShell = (ChannelShell) session.openChannel("shell");
        InputStream inputStream = channelShell.getInputStream();//从远端到达的数据  都能从这个流读取到
        channelShell.setPty(true);
        channelShell.connect();

        OutputStream outputStream = channelShell.getOutputStream();//写入该流的数据  都将发送到远程端
        //使用PrintWriter 就是为了使用println 这个方法
        //好处就是不需要每次手动给字符加\n
        PrintWriter printWriter = new PrintWriter(outputStream);
        printWriter.println("cd /home/zxj");
        printWriter.println("ls");
        printWriter.println("cd /home/zxj/code");
        printWriter.println("ls");
        printWriter.println("./test.sh");
        printWriter.println("exit");//为了结束本次交互
        printWriter.flush();//把缓冲区的数据强行输出

/**
 shell管道本身就是交互模式的。要想停止，有两种方式：
 一、人为的发送一个exit命令，告诉程序本次交互结束
 二、使用字节流中的available方法，来获取数据的总大小，然后循环去读。
 为了避免阻塞
 */
        byte[] tmp = new byte[1024];
        while(true){

            while(inputStream.available() > 0){
                int i = inputStream.read(tmp, 0, 1024);
                if(i < 0) break;
                String s = new String(tmp, 0, i);
                if(s.indexOf("--More--") >= 0){
                    outputStream.write((" ").getBytes());
                    outputStream.flush();
                }
                System.out.println(s);
            }
            if(channelShell.isClosed()){
                System.out.println("exit-status:"+channelShell.getExitStatus());
                break;
            }
            try{
                Thread.sleep(1000);
            } catch(Exception e){
                e.printStackTrace();
            }

        }
        outputStream.close();
        inputStream.close();
        channelShell.disconnect();
        session.disconnect();
        System.out.println("DONE");

        return result;
    }
}
