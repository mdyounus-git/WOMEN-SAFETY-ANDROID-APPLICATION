package com.example.womensafety.news;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.ItemNewsBinding;
import com.example.womensafety.databinding.ItemNewsFeaturedBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** First story is shown as a large card, the rest as a clean list. */
final class NewsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_FEATURED = 0;
    private static final int TYPE_ROW = 1;

    private final List<NewsItem> items = new ArrayList<>();
    private final Consumer<NewsItem> onClick;

    NewsAdapter(@NonNull Consumer<NewsItem> onClick) {
        this.onClick = onClick;
    }

    void submit(@NonNull List<NewsItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_FEATURED : TYPE_ROW;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FEATURED) {
            return new Featured(ItemNewsFeaturedBinding.inflate(inf, parent, false));
        }
        return new Row(ItemNewsBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        NewsItem item = items.get(position);
        String time = Ui.relativeTime(holder.itemView.getContext(), item.publishedAt);
        if (holder instanceof Featured) {
            ItemNewsFeaturedBinding b = ((Featured) holder).b;
            b.source.setText(item.source);
            b.title.setText(item.title);
            b.time.setText(time);
        } else {
            ItemNewsBinding b = ((Row) holder).b;
            b.source.setText(item.source);
            b.title.setText(item.title);
            b.time.setText(time);
            b.divider.setVisibility(position == items.size() - 1 ? View.INVISIBLE : View.VISIBLE);
        }
        holder.itemView.setOnClickListener(v -> onClick.accept(item));
    }

    static final class Featured extends RecyclerView.ViewHolder {
        final ItemNewsFeaturedBinding b;

        Featured(ItemNewsFeaturedBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class Row extends RecyclerView.ViewHolder {
        final ItemNewsBinding b;

        Row(ItemNewsBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
