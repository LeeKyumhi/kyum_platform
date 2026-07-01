package com.guidematch.guide;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "post_comments")
public class PostComment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PostComment() {}

    public PostComment(Long postId, Long userId, String content) {
        this.postId = postId;
        this.userId = userId;
        this.content = content;
    }

    public Long getId()           { return id; }
    public Long getPostId()       { return postId; }
    public Long getUserId()       { return userId; }
    public String getContent()    { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
