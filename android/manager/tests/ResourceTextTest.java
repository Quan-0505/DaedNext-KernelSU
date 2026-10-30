package io.github.quan0505.daednext;

import java.util.*;

/** Span safety checks: quotes, comments, nested syntax, and source preservation. */
public final class ResourceTextTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        String text="# first rule\ndomain(\n  suffix:example.com,\n  'suffix:a#b->c' # keep nested comment\n) && dport(443) -> proxy # keep inline\n# fallback note\nfallback: direct\n";
        List<ResourceText.Entry> rules=ResourceText.rules(text,false,"");
        check(rules.size()==2,"multiline expression must stay one entry");
        check(rules.get(0).arrow()>0,"quoted arrow must not be used as operator");
        check(rules.get(0).text.contains("a#b->c"),"quoted hash is data");
        String edited=ResourceText.replace(text,rules.get(0),"domain(suffix:test.example) -> direct");
        check(edited.startsWith("# first rule\n"),"leading comment preserved");
        check(edited.contains("# keep inline\n# fallback note\nfallback: direct"),"trailing comment and following rule preserved");
        check(ResourceText.rules(edited,false,"").size()==2,"editing does not add/remove rules");
        String removed=ResourceText.remove(text,rules.get(0));
        check(removed.equals("# fallback note\nfallback: direct\n"),"only deleted rule and its leading comment removed");
        String pair="# A\ndport(80) -> direct\n# B\ndport(443) -> proxy";
        List<ResourceText.Entry> p=ResourceText.rules(pair,false,"");String swapped=ResourceText.swap(pair,p.get(0),p.get(1));
        check(ResourceText.rules(swapped,false,"").size()==2,"last line without newline stays separate after swap");
        check(swapped.startsWith("# B\ndport(443) -> proxy\n# A\n"),"comments move with their rules");
        String dns="dns {\n  cache_size: 32\n  # upstream { fake }\n  upstream {\n    first: 'https://dns.example/{path}#fragment'\n  }\n  routing { request {\n    qname(suffix:cn) -> first\n    fallback: first\n  } }\n}\n";
        ResourceText.Block upstream=ResourceText.path(dns,"dns","upstream");
        check(upstream!=null&&ResourceText.entries(dns,upstream.open+1,upstream.close).size()==1,"quoted braces and commented blocks ignored");
        String withResponse=ResourceText.ensureDnsPath(dns,"routing","response");
        check(withResponse.contains("cache_size: 32"),"unknown DNS options preserved");
        check(ResourceText.path(withResponse,"dns","routing","response")!=null,"missing nested block created");
        String inserted=ResourceText.insertRule(dns,true,"request","qtype(aaaa) -> asis");
        List<ResourceText.Entry> request=ResourceText.rules(inserted,true,"request");
        check(request.size()==3&&request.get(2).fallback(),"new rule precedes fallback");
        check(inserted.contains("cache_size: 32"),"unrelated source preserved during insertion");
        String response=ResourceText.insertRule(dns,true,"response","qtype(aaaa) -> reject");
        List<ResourceText.Entry> responseRules=ResourceText.rules(response,true,"response");
        check(responseRules.size()==2&&responseRules.get(1).text.equals("fallback: accept"),"new response block includes its required fallback");
        String fallbackOnly=ResourceText.insertRule(dns,true,"response","fallback: reject");
        check(ResourceText.rules(fallbackOnly,true,"response").size()==1,"explicit fallback does not create duplicate default");
        String wrapped="routing {\n dport(53) -> direct\n fallback: proxy\n}\n";
        check(ResourceText.rules(wrapped,false,"").size()==2,"wrapped routing accepted");
        boolean rejected=false;try{ResourceText.rules("domain('broken) -> direct",false,"");}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"unclosed expression rejected");
        System.out.println("ResourceText span safety checks passed.");
    }
}
