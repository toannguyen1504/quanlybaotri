package com.example.quanlybaotri.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class KnowledgeIndexTest {
    @Test void findsReviewedWorkflowSources() throws Exception {
        KnowledgeIndex index=new KnowledgeIndex();index.load();
        var results=index.search("quy trình tạo phiếu bảo trì và mức ưu tiên");
        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(item->item.sourceId()!=null&&!item.sourceId().isBlank());
        assertThat(results.stream().map(KnowledgeIndex.Snippet::content).toList())
            .anyMatch(content->content.contains("SUBMITTED"));
    }

    @Test void returnsNoEvidenceForUnrelatedQuestion() throws Exception {
        KnowledgeIndex index=new KnowledgeIndex();index.load();
        assertThat(index.search("thiên văn lượng tử galaxy")).isEmpty();
    }
}
