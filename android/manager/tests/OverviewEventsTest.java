package io.github.quan0505.daednext;

import java.io.*;
import java.util.*;

public final class OverviewEventsTest {
    public static void main(String[] args)throws Exception{
        List<String> samples=new ArrayList<>();
        OverviewEvents.read(new StringReader(": heartbeat\r\nretry: 1000\r\nevent: runtime.overview\r\ndata: {\r\ndata: \"downloadRate\":\"104857600\"}\r\n\r\nevent: runtime.group.selection\ndata: ignored\n\nevent: runtime.overview.delta\ndata:{\"trafficAvailable\":false}\n\nevent: runtime.overview.delta\ndata: incomplete"),samples::add);
        if(!samples.equals(Arrays.asList("{\n\"downloadRate\":\"104857600\"}","{\"trafficAvailable\":false}")))throw new AssertionError(samples);
        char[] huge=new char[2*1024*1024+1];Arrays.fill(huge,'x');
        try{OverviewEvents.read(new StringReader(new String(huge)),s->{throw new AssertionError("Oversized frame published");});throw new AssertionError("Oversized frame accepted");}catch(IOException expected){}
        samples.clear();OverviewEvents.read(new StringReader("event: runtime.overview.delta\ndata: old\nevent: other\n\nevent: runtime.overview.delta\ndata: new\n\n"),samples::add);
        if(!samples.equals(Arrays.asList("new")))throw new AssertionError(samples);
        System.out.println("SSE full/delta, CRLF, multiline, ignored events, EOF and size bound passed");
    }
}
