package io.github.quan0505.daednext;

import java.util.*;

/** Lossless span editing for DAE sections; the daemon remains the syntax validator. */
final class ResourceText {
    static final class Block {
        final int start, open, close;
        Block(int start,int open,int close){this.start=start;this.open=open;this.close=close;}
    }
    static final class Entry {
        final int start, body, codeEnd, end;
        final String text;
        Entry(String s,int start,int body,int codeEnd,int end){this.start=start;this.body=body;this.codeEnd=codeEnd;this.end=end;this.text=uncomment(s.substring(body,codeEnd)).trim();}
        boolean fallback(){return text.matches("(?s)^fallback\\s*:.*");}
        int arrow(){return operator(text,"->");}
    }
    static String uncomment(String s){
        StringBuilder out=new StringBuilder();char quote=0;boolean escape=false,comment=false;
        for(int i=0;i<s.length();i++){char c=s.charAt(i);
            if(comment){if(c=='\n'){comment=false;out.append(c);}continue;}
            if(quote!=0){out.append(c);if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
            if(c=='#'){comment=true;continue;}if(c=='\''||c=='"'||c=='`')quote=c;out.append(c);
        }return out.toString();
    }
    static int operator(String s,String needle){
        char quote=0;boolean escape=false;int depth=0;
        for(int i=0;i<s.length();i++){char c=s.charAt(i);
            if(quote!=0){if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
            if(c=='\''||c=='"'||c=='`'){quote=c;continue;}
            if(c=='('||c=='['||c=='{')depth++;else if(c==')'||c==']'||c=='}')depth--;
            if(depth==0&&s.startsWith(needle,i))return i;
        }return -1;
    }
    static List<Entry> entries(String s,int from,int to){
        List<Entry> out=new ArrayList<>();int segment=from,body=-1,last=-1,parens=0,braces=0,squares=0;
        char quote=0;boolean escape=false,comment=false;
        for(int i=from;i<=to;i++){
            char c=i==to?'\n':s.charAt(i);
            if(quote!=0){if(i==to)throw new IllegalArgumentException("引号未闭合");last=i+1;if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
            if(comment&&c!='\n')continue;
            if(comment)comment=false;
            if((c=='\n'||c==';')&&parens==0&&braces==0&&squares==0){
                if(body>=0){out.add(new Entry(s,segment,body,last,Math.min(i+1,to)));segment=i+1;body=-1;last=-1;}continue;
            }
            if(c=='#'){comment=true;continue;}
            if(Character.isWhitespace(c))continue;
            if(body<0)body=i;last=i+1;
            if(c=='\''||c=='"'||c=='`')quote=c;
            else if(c=='(')parens++;else if(c==')')parens--;
            else if(c=='{')braces++;else if(c=='}')braces--;
            else if(c=='[')squares++;else if(c==']')squares--;
            if(parens<0||braces<0||squares<0)throw new IllegalArgumentException("括号不匹配");
        }
        if(quote!=0||parens!=0||braces!=0||squares!=0)throw new IllegalArgumentException("表达式尚未闭合");
        return out;
    }
    static Block block(String s,String key,int from,int to){
        char quote=0;boolean escape=false,comment=false;int depth=0;
        for(int i=from;i<to;i++){char c=s.charAt(i);
            if(comment){if(c=='\n')comment=false;continue;}
            if(quote!=0){if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
            if(c=='#'){comment=true;continue;}if(c=='\''||c=='"'||c=='`'){quote=c;continue;}
            if(c=='{'){depth++;continue;}if(c=='}'){depth--;continue;}
            if(depth==0&&(Character.isLetter(c)||c=='_')){
                int end=i+1;while(end<to&&(Character.isLetterOrDigit(s.charAt(end))||s.charAt(end)=='_'||s.charAt(end)=='-'))end++;
                int brace=end;while(brace<to&&Character.isWhitespace(s.charAt(brace)))brace++;
                if(s.substring(i,end).equals(key)&&brace<to&&s.charAt(brace)=='{'){
                    char q=0;boolean esc=false,comm=false;int d=1;
                    for(int j=brace+1;j<to;j++){char x=s.charAt(j);
                        if(comm){if(x=='\n')comm=false;continue;}
                        if(q!=0){if(esc)esc=false;else if(x=='\\')esc=true;else if(x==q)q=0;continue;}
                        if(x=='#'){comm=true;continue;}if(x=='\''||x=='"'||x=='`'){q=x;continue;}
                        if(x=='{')d++;else if(x=='}'&&--d==0)return new Block(i,brace,j);
                    }throw new IllegalArgumentException("区块未闭合："+key);
                }i=end-1;
            }
        }return null;
    }
    static Block path(String s,String... keys){
        int from=0,to=s.length();Block found=null;
        for(String key:keys){found=block(s,key,from,to);if(found==null)return null;from=found.open+1;to=found.close;}return found;
    }
    static String wrap(String key,String raw){return block(raw,key,0,raw.length())==null?key+" {\n"+raw+"\n}\n":raw;}
    static String ensureDnsPath(String raw,String... keys){
        String s=wrap("dns",raw);List<String> path=new ArrayList<>();path.add("dns");
        for(String key:keys){Block parent=path(s,path.toArray(new String[0]));Block child=block(s,key,parent.open+1,parent.close);
            if(child==null)s=s.substring(0,parent.close)+"\n"+key+" {\n}\n"+s.substring(parent.close);path.add(key);
        }return s;
    }
    static List<Entry> rules(String raw,boolean dns,String phase){
        Block area=dns?path(raw,"dns","routing",phase):block(raw,"routing",0,raw.length());
        if(dns&&area==null)return new ArrayList<>();return entries(raw,area==null?0:area.open+1,area==null?raw.length():area.close);
    }
    static String replace(String s,Entry e,String value){return s.substring(0,e.body)+value+s.substring(e.codeEnd);}
    static String remove(String s,Entry e){return s.substring(0,e.start)+s.substring(e.end);}
    static String swap(String s,Entry a,Entry b){
        if(a.start>b.start){Entry temp=a;a=b;b=temp;}
        String left=s.substring(a.start,a.end),right=s.substring(b.start,b.end);
        if(!left.endsWith("\n"))left+="\n";if(!right.endsWith("\n"))right+="\n";
        return s.substring(0,a.start)+right+s.substring(a.end,b.start)+left+s.substring(b.end);
    }
    static String insertRule(String raw,boolean dns,String phase,String line){
        boolean missingPhase=dns&&path(raw,"dns","routing",phase)==null;
        String s=dns?ensureDnsPath(raw,"routing",phase):raw;
        if(missingPhase&&!line.matches("(?s)^fallback\\s*:.*")){
            Block created=path(s,"dns","routing",phase);
            s=s.substring(0,created.close)+"\nfallback: "+(phase.equals("response")?"accept":"asis")+"\n"+s.substring(created.close);
        }
        Block area=dns?path(s,"dns","routing",phase):block(s,"routing",0,s.length());
        int at=area==null?s.length():area.close;
        for(Entry e:rules(s,dns,phase))if(e.fallback()){at=e.start;break;}
        return s.substring(0,at)+"\n"+line+"\n"+s.substring(at);
    }
    static String quoted(String value){return "'"+value.replace("\\","\\\\").replace("'","\\'")+"'";}
}
