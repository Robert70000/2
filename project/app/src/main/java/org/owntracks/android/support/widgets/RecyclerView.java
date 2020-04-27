package org.owntracks.android.support.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

// Recycler view that dynamically shows/hides an empty placeholder view
public class RecyclerView extends androidx.recyclerview.widget.RecyclerView {
    private View emptyView;

    private final AdapterDataObserver emptyObserver = new AdapterDataObserver() {


        @Override
        public void onChanged() {
            Adapter<?> adapter =  getAdapter();
            if(adapter != null && emptyView != null) {
                if(adapter.getItemCount() == 0) {
                    emptyView.setVisibility(View.VISIBLE);
                    RecyclerView.this.setVisibility(View.GONE);
                }
                else {
                    emptyView.setVisibility(View.GONE);
                    RecyclerView.this.setVisibility(View.VISIBLE);
                }
            }

        }
    };

    public RecyclerView(Context context) {
        super(context);
    }

    public RecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);

        if(adapter != null) {
            adapter.registerAdapterDataObserver(emptyObserver);
        }

        emptyObserver.onChanged();
    }
    public void setEmptyView(View emptyView) {
        this.emptyView = emptyView;
    }
}
