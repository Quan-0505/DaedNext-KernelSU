package io.github.quan0505.daednext;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.JSONObject;

/** One foreground-only subscription to the core's existing one-second samples. */
final class RuntimeMetricsFeed {
    interface Listener { void sample(JSONObject value); }
    private final ApiClient api;
    private final Listener listener;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private int epoch;
    private boolean running;
    private HttpURLConnection connection;
    private Future<?> job;
    RuntimeMetricsFeed(ApiClient api,Listener listener){this.api=api;this.listener=listener;}
    synchronized void start(){
        if(running)return;
        running=true;final int run=++epoch;
        job=worker.submit(()->pump(run));
    }
    synchronized void stop(){
        running=false;epoch++;
        if(connection!=null){connection.disconnect();connection=null;}
        if(job!=null){job.cancel(true);job=null;}
    }
    void close(){stop();worker.shutdownNow();}
    private synchronized boolean current(int run){return running&&epoch==run;}
    private void pump(int run){
        long delay=1000;
        while(current(run)&&!Thread.currentThread().isInterrupted()){
            HttpURLConnection stream=null;
            try{
                stream=api.runtimeEvents();
                synchronized(this){if(!current(run)){stream.disconnect();return;}connection=stream;}
                int code=stream.getResponseCode();
                if(code==401){api.clear();throw new IOException("Metrics session expired");}
                if(code!=200||!String.valueOf(stream.getContentType()).startsWith("text/event-stream"))throw new IOException("Metrics stream unavailable");
                try(Reader reader=new InputStreamReader(stream.getInputStream(),StandardCharsets.UTF_8)){
                    OverviewEvents.read(reader,json->{
                        JSONObject sample=new JSONObject(json);
                        if(current(run))listener.sample(sample);
                    });
                }
                delay=1000;
            }catch(Exception ignored){ /* Retry read-only subscription; old samples expire in the UI. */ }
            finally{
                synchronized(this){if(connection==stream)connection=null;}
                if(stream!=null)stream.disconnect();
            }
            if(!current(run))return;
            try{Thread.sleep(delay);}catch(InterruptedException stopped){Thread.currentThread().interrupt();return;}
            delay=Math.min(delay*2,8000);
        }
    }
}
