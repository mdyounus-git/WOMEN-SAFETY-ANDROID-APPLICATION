package com.example.womensafety.news;

import androidx.annotation.NonNull;

/** One headline from the news feed. */
public final class NewsItem {

    public final String title;
    public final String source;
    public final String link;
    public final long publishedAt;

    public NewsItem(@NonNull String title, @NonNull String source, @NonNull String link, long publishedAt) {
        this.title = title;
        this.source = source;
        this.link = link;
        this.publishedAt = publishedAt;
    }
}
