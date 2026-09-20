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

    @Column(nullable = false)
    private boolean deleted = false;


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

    public void update(
            String title,
            String contentHtml,
            boolean pinned
    ) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.contentText = HtmlTextUtils.extractText(contentHtml);
        this.pinned = pinned;
    }

    /** soft delete. 지원자 화면에서만 빠지고 관리자 목록에는 삭제됨으로 남는다. */
    public void delete() {
        this.deleted = true;
    }

    public void restore() {
        this.deleted = false;
    }

    public void changePinned(boolean pinned) {
        this.pinned = pinned;
    }

}
