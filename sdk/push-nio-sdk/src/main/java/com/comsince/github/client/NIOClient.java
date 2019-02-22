package com.comsince.github.client;

import com.comsince.github.core.*;
import com.comsince.github.core.callback.CompletedCallback;
import com.comsince.github.core.callback.ConnectCallback;
import com.comsince.github.core.callback.DataCallback;
import com.comsince.github.logger.Log;
import com.comsince.github.push.Header;
import com.comsince.github.push.Signal;
import com.comsince.github.logger.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NIOClient implements ConnectCallback,DataCallback,CompletedCallback {
    Log log = LoggerFactory.getLogger(NIOClient.class);
    private AsyncServer asyncServer;
    private AsyncSocket asyncSocket;
    private String host;
    private int port;

    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();


    volatile boolean isConnected = false;

    Header receiveHeader = null;
    ByteBufferList receiveBuffer = new ByteBufferList();

    private PushMessageCallback pushMessageCallback;

    public void setPushMessageCallback(PushMessageCallback pushMessageCallback){
        this.pushMessageCallback = pushMessageCallback;
    }

    public NIOClient(String host, int port) {
        this.host = host;
        this.port = port;
        asyncServer = new AsyncServer(host+"-"+port);
    }

    public void connect(){
        if(!isConnected){
            asyncServer.connectSocket(host,port,this);
        }
    }

    private void sub(){
        //start register
        final Header header = new Header();
        header.setSignal(Signal.SUB);
        header.setLength(0);

        Util.writeAll(asyncSocket, header.getContents(), new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                log.i("send sub signal success");
            }
        });

    }

    @Override
    public void onConnectCompleted(Exception ex, AsyncSocket socket) {
        if(ex != null){
            log.i("connect failed");
            return;
        }

        isConnected = true;

        this.asyncSocket = socket;

        asyncSocket.setDataCallback(this);

        asyncSocket.setClosedCallback(this);


                scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        log.i("send heartbeat");
                        Header header = new Header();
                        header.setSignal(Signal.PING);
                        byte[] sendByte = header.getContents();
                        Util.writeAll(asyncSocket, sendByte, new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception ex) {
                                log.i("send heartbeat onCompleted");
                            }
                        });
                    }
                },500,(new Random().nextInt(120) +30) * 1000, TimeUnit.MILLISECONDS);

        sub();

    }

    @Override
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if(receiveBuffer.remaining() == 0){
            ByteBufferList headerBuffer = bb.get(Header.LENGTH);
            receiveHeader = new Header(headerBuffer.getAll());
            log.i("receive signal "+receiveHeader.getSignal());
        }
        int bodyLength = receiveHeader.getLength();
        int read = bodyLength - receiveBuffer.remaining();
        int reallyRead = read > bb.remaining() ? bb.remaining() : read;
        bb.get(receiveBuffer,reallyRead);

        if(receiveBuffer.remaining() == bodyLength){
            String message = receiveBuffer.readString(Charset.forName("UTF-8"));
            log.i("receive body-> "+message);
            if(pushMessageCallback != null){
                pushMessageCallback.receiveMessage(message);
            }
        }
    }

    @Override
    public void onCompleted(Exception ex) {
        if(ex != null){
            ex.printStackTrace();
            isConnected = false;
        }
        //retry
        scheduledExecutorService.schedule(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return asyncServer.connectSocket(host,port,NIOClient.this);
            }
        },5, TimeUnit.SECONDS);

    }
}
