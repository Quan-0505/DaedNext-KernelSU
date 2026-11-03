package io.github.quan0505.daednext;

import java.io.*;

/** Bounded SSE framing, independent of Android and JSON. */
final class OverviewEvents {
    interface Listener { void sample(String json) throws Exception; }
    static void read(Reader source, Listener listener) throws Exception {
        BufferedReader reader = new BufferedReader(source);
        String event=""; StringBuilder data=new StringBuilder(),line=new StringBuilder();
        int ch;
        while((ch=reader.read())!=-1){
            if(ch!='\n'){
                line.append((char)ch);
                if(line.length()+data.length()>2*1024*1024)throw new IOException("SSE event too large");
                continue;
            }
            String value=line.toString();line.setLength(0);
            if(value.endsWith("\r"))value=value.substring(0,value.length()-1);
            if(value.isEmpty()){
                if(data.length()>0&&(event.equals("runtime.overview")||event.equals("runtime.overview.delta")))
                    listener.sample(data.substring(0,data.length()-1));
                event="";data.setLength(0);
            }else if(value.startsWith("event:"))event=field(value,6);
            else if(value.startsWith("data:"))data.append(field(value,5)).append('\n');
        }
        // An incomplete event at EOF is never published.
    }
    private static String field(String line,int start){return line.substring(start+(line.length()>start&&line.charAt(start)==' '?1:0));}
}
