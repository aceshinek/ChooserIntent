package smartisanos.app;

import com.smartisan.notes.intentchooser.R;
import com.smartisan.notes.intentchooser.R.drawable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

public class IndicatorView extends View {

    private static final int MAX_NUM_DOT = 25;
    // state
    private int mAll = 0;
    // 0 based
    private int mCur = 0;
    private int mDiameter;
    // drawalbe resource
    private Bitmap mHighligt;
    private Bitmap mNormal;

    public IndicatorView(Context context) {
        this(context, null);
    }

    public IndicatorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IndicatorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // load resource
        mHighligt = BitmapFactory.decodeResource(getResources(), R.drawable.indicator_highlight);
        mNormal = BitmapFactory.decodeResource(getResources(), R.drawable.indicator);
        // update relatied variable
        mDiameter = mHighligt.getWidth();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // judge part
        if (mAll < 0)
            return;
        if (mAll > MAX_NUM_DOT)
            mAll = MAX_NUM_DOT;
        if (mCur < 0)
            mCur = 0;
        if (mCur >= mAll)
            mCur = mAll - 1;

        // when the number of point is less than 2, we will draw nothing
        if (mAll <= 1)
            return;

        // begin draw
        int length = mDiameter * mAll;
        int left = (getWidth() - length) / 2;
        int top = (getHeight() - mDiameter) / 2;
        for (int i = 0; i < mAll; ++i) {
            Bitmap now = i == mCur ? mHighligt : mNormal;
            canvas.drawBitmap(now, left, top, null);
            left += mDiameter;
        }
    }

    @Override
    protected void onDetachedFromWindow (){
        super.onDetachedFromWindow();
        mHighligt.recycle();
        mNormal.recycle();
    }

    /**
     * @param all
     * @param cur, 0 based
     */
    public void setState(int all, int cur) {
        mAll = all;
        mCur = cur;
        invalidate();
    }

    public void setState(int all) {
        mAll = all;
        invalidate();
    }
}
