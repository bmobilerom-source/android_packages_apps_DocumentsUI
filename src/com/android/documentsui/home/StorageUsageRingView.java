/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.documentsui.R;

/**
 * Dual-segment storage usage ring for the Files home dashboard.
 */
public class StorageUsageRingView extends View {
    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mInternalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mExternalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mArcBounds = new RectF();

    private float mInternalFraction;
    private float mExternalFraction;
    private float mStrokeWidth;

    public StorageUsageRingView(Context context) {
        this(context, null);
    }

    public StorageUsageRingView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StorageUsageRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mStrokeWidth = context.getResources().getDimension(R.dimen.home_ring_stroke);

        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        mTrackPaint.setStrokeWidth(mStrokeWidth);
        mTrackPaint.setColor(ContextCompat.getColor(context, R.color.home_ring_track));

        mInternalPaint.setStyle(Paint.Style.STROKE);
        mInternalPaint.setStrokeCap(Paint.Cap.ROUND);
        mInternalPaint.setStrokeWidth(mStrokeWidth);
        mInternalPaint.setColor(ContextCompat.getColor(context, R.color.home_ring_internal));

        mExternalPaint.setStyle(Paint.Style.STROKE);
        mExternalPaint.setStrokeCap(Paint.Cap.ROUND);
        mExternalPaint.setStrokeWidth(mStrokeWidth);
        mExternalPaint.setColor(ContextCompat.getColor(context, R.color.home_ring_external));
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Re-resolve Monet / day-night colors when UI mode changes.
        final Context context = getContext();
        mTrackPaint.setColor(ContextCompat.getColor(context, R.color.home_ring_track));
        mInternalPaint.setColor(ContextCompat.getColor(context, R.color.home_ring_internal));
        mExternalPaint.setColor(ContextCompat.getColor(context, R.color.home_ring_external));
        invalidate();
    }

    /**
     * @param internalUsedBytes used bytes on internal storage
     * @param externalUsedBytes used bytes on external storage (0 if absent)
     * @param totalBytes combined total used as ring denominator (internal+external totals)
     */
    public void setUsage(long internalUsedBytes, long externalUsedBytes, long totalBytes) {
        if (totalBytes <= 0) {
            mInternalFraction = 0f;
            mExternalFraction = 0f;
        } else {
            mInternalFraction = Math.min(1f, internalUsedBytes / (float) totalBytes);
            mExternalFraction = Math.min(1f - mInternalFraction,
                    externalUsedBytes / (float) totalBytes);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float pad = mStrokeWidth / 2f + 2f;
        mArcBounds.set(pad, pad, getWidth() - pad, getHeight() - pad);

        canvas.drawArc(mArcBounds, -90f, 360f, false, mTrackPaint);

        float start = -90f;
        if (mInternalFraction > 0f) {
            final float sweep = 360f * mInternalFraction;
            canvas.drawArc(mArcBounds, start, sweep, false, mInternalPaint);
            start += sweep;
        }
        if (mExternalFraction > 0f) {
            canvas.drawArc(mArcBounds, start, 360f * mExternalFraction, false, mExternalPaint);
        }
    }
}
