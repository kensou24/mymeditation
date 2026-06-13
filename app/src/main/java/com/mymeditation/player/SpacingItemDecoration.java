package com.mymeditation.player;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SpacingItemDecoration extends RecyclerView.ItemDecoration {
    private final int verticalSpacing;
    private final int horizontalSpacing;

    public SpacingItemDecoration(int verticalSpacing, int horizontalSpacing) {
        this.verticalSpacing = verticalSpacing;
        this.horizontalSpacing = horizontalSpacing;
    }

    public SpacingItemDecoration(int verticalSpacing) {
        this(verticalSpacing, 0);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.bottom = verticalSpacing;
        outRect.left = horizontalSpacing;
        outRect.right = horizontalSpacing;

        // Add top spacing only for the first item
        int position = parent.getChildAdapterPosition(view);
        if (position == 0) {
            outRect.top = verticalSpacing;
        }
    }
}
