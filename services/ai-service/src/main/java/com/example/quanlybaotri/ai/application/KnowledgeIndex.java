package com.example.quanlybaotri.ai.application;

import com.example.quanlybaotri.ai.api.AiDtos.SourceRef;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIndex {
    private static final List<String> FILES=List.of("ticket-workflow.md","roles-and-access.md","cost-payment-feedback.md");
    private final List<Snippet> snippets=new ArrayList<>();
    @PostConstruct void load() throws IOException {for(String file:FILES)parse(file,new String(new ClassPathResource("knowledge/"+file).getInputStream().readAllBytes(),StandardCharsets.UTF_8));}
    public List<Snippet> search(String query){
        Set<String> words=tokens(query);return snippets.stream().map(s->new Scored(s,score(s,words))).filter(x->x.score()>=3)
            .sorted(Comparator.comparingInt(Scored::score).reversed()).limit(3).map(Scored::snippet).toList();
    }
    public List<SourceRef> sources(List<Snippet> values){return values.stream().map(s->new SourceRef(s.sourceId(),s.title(),s.section())).toList();}
    private void parse(String file,String markdown){
        String title=file;String section="Tổng quan";StringBuilder content=new StringBuilder();int index=0;
        for(String line:markdown.split("\\R")){
            if(line.startsWith("# ")){title=line.substring(2).trim();continue;}
            if(line.startsWith("## ")){if(!content.toString().isBlank())add(file,index++,title,section,content.toString());section=line.substring(3).trim();content.setLength(0);}
            else content.append(line).append('\n');
        }
        if(!content.toString().isBlank())add(file,index,title,section,content.toString());
    }
    private void add(String file,int index,String title,String section,String content){snippets.add(new Snippet(file.replace(".md","")+"-"+index,title,section,content.trim(),tokens(title+" "+section+" "+content)));}
    private int score(Snippet s,Set<String> query){int score=0;Set<String> heading=tokens(s.title()+" "+s.section());for(String word:query)if(heading.contains(word))score+=5;else if(s.tokens().contains(word))score++;return score;}
    private Set<String> tokens(String value){String normalized=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace('đ','d');
        Set<String> stop=Set.of("va","cua","cho","tu","den","co","the","khi","duoc","noi","mot","nhung");
        Set<String> result=new LinkedHashSet<>();for(String word:Pattern.compile("[^a-z0-9]+",Pattern.UNICODE_CHARACTER_CLASS).split(normalized))if(word.length()>1&&!stop.contains(word))result.add(word);return result;}
    public record Snippet(String sourceId,String title,String section,String content,Set<String> tokens) {}
    private record Scored(Snippet snippet,int score) {}
}
