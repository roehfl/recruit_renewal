package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.common.util.HtmlTextUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Notice extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String contentHtml;
    @Column(length = 1000)
    private String contentText;
    private boolean pinned = false;


    private Notice(
            String title,
            String contentHtml,
            boolean pinned
    ) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.contentText = HtmlTextUtils.extractText(contentHtml);
        this.pinned = pinned;
    }


    public static Notice create(
            String title,
            String contentHtml,
            boolean pinned
    ) {
        return new Notice(title, contentHtml, pinned);
    }


}
