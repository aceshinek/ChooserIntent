package com.smartisan.notes.intentchooser;

import java.util.ArrayList;
import java.util.List;

import com.smartisan.notes.intentchooser.ResolverActivity.ViewHolder;

import smartisanos.app.IndicatorView;
import smartisanos.view.PagerAdapter;
import smartisanos.view.ViewPager;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.Toast;

public class ChooserActivity extends Activity {

    public static final String EXCLOUD_PKG = "excloud_pkg";

    private String mType = "";
    private String mExtraText = "";
    private Uri mExtraStream = null;
    private ArrayList<String> mExcloud;
    
    private ViewPager mViewPager;
    private IndicatorView mIndicatorView;
    
    private ChooserPagerAdapter mChooserPagerAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mType = intent.getType();
        mExtraText = intent.getExtras().getString(Intent.EXTRA_TEXT);
        mExtraStream = intent.getExtras().getParcelable(Intent.EXTRA_STREAM);
        mExcloud = intent.getExtras().getStringArrayList(EXCLOUD_PKG);
        
        Toast.makeText(getApplicationContext(), "" + mType, Toast.LENGTH_SHORT).show();

        setContentView(R.layout.resolver_layout);
        Window window = getWindow();
        window.setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams lp = window.getAttributes();
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
//        lockOritation(display.getRotation());
        Point point = new Point();
        display.getSize(point);
        lp.width = point.x;
        window.setAttributes(lp);
        
        this.setFinishOnTouchOutside(true);
        
        List<Intent> targetedShareIntents = new ArrayList<Intent>();
        
        Intent shareToIntent = chooserIntent(mType  , mExtraStream, mExtraText);

        List<ResolveInfo> resInfo = getPackageManager().queryIntentActivities(
                shareToIntent, PackageManager.MATCH_DEFAULT_ONLY);
        int pageNumber = 0;
        if (resInfo.size() <= 6) {
            pageNumber = 1;
        }else{
            if (resInfo.size() % 6 == 0) {
                pageNumber = resInfo.size() / 6;
            }else {
                pageNumber = (resInfo.size() - (resInfo.size() % 6)) / 6 + 1;
            }
        }
        LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
        List<GridView> gridViews = new ArrayList<>();
        for (int i = 0, j = 0; i < pageNumber; i++) {
            View view = (View) inflater.inflate(R.layout.resolver_grid, null);
            GridView gridView = (GridView) view.findViewById(R.id.gridView);
            ViewGroup parent = (ViewGroup) gridView.getParent();
            if (parent != null) {
                parent.removeView(gridView);
            }
            List<ResolveInfo> rList = new ArrayList<>();
            int end = (j + 6) > resInfo.size() ? resInfo.size() : (j + 6);
            rList.addAll(resInfo.subList(j, end));
            final GridViewAdapter gridViewAdapter = new GridViewAdapter(getApplicationContext(), rList);
            gridView.setAdapter(gridViewAdapter);
            gridView.setOnItemClickListener(new OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                    List<ResolveInfo> ris = gridViewAdapter.getResolveInfos();
                    ResolveInfo ri = ris.get(arg2);
                    startActivityByResolveInfo(ri);
                }
            });
            j = end;
            gridViews.add(gridView);
        }
        mChooserPagerAdapter = new ChooserPagerAdapter(gridViews);
        mIndicatorView = (IndicatorView) findViewById(R.id.indicator);
        mIndicatorView.setState(mChooserPagerAdapter.getCount(), 0);
        
        init();
        
        
        
        
//        
//        for (ResolveInfo info : resInfo) {
//            if (!mExcloud.contains(info.activityInfo.packageName)) {
//                startActivityByResolveInfo(info);
//            }
//        }
    }

    private void startActivityByResolveInfo(ResolveInfo info) {
        Intent targetedShare = new Intent(Intent.ACTION_SEND);
//                targetedShare.setAction(Intent.ACTION_SEND);
        targetedShare.setType(mType);
        targetedShare.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        targetedShare.putExtra(Intent.EXTRA_TEXT, mExtraText);
        targetedShare.putExtra(Intent.EXTRA_STREAM, mExtraStream);
        targetedShare.putExtra("sms_body", mExtraText);
        targetedShare.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
        targetedShare.setPackage(info.activityInfo.packageName);
//                targetedShareIntents.add(new LabeledIntent(targetedShare, info.activityInfo.packageName, info.loadLabel(getPackageManager()), info.icon));

        Log.d("ChooserActivity_onCreate", "package_name: " + info.activityInfo.packageName + "   activity: " + info.activityInfo.name);
//                if ("com.android.mms".equals(info.activityInfo.packageName)) {
//                    startActivity(targetedShare);
//                    finish();
//                }
//        startActivity(targetedShare);
//        finish();
    }
    
    
    
    private class GridViewAdapter extends BaseAdapter {
        private  LayoutInflater mInflater;
        private List<ResolveInfo> mResolveInfos;

        public GridViewAdapter(Context context, List<ResolveInfo> resolveInfos) {
            mResolveInfos = resolveInfos;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public List<ResolveInfo> getResolveInfos(){
            return mResolveInfos;
        }
        
        public int getCount() {
            return mResolveInfos == null ? 0: mResolveInfos.size();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            if (convertView == null) {
                view = mInflater.inflate(R.layout.resolver_grid_item, parent, false);

                final ViewHolder holder = new ViewHolder(view);
                view.setTag(holder);
            } else {
                view = convertView;
            }
            bindView(view, position);
            return view;
        }

        private final void bindView(View view, final int position) {
            final ViewHolder holder = (ViewHolder) view.getTag();
            holder.text.setMaxWidth(getResources().getDimensionPixelSize(R.dimen.text_max_length_portrait));
            holder.text.setText(mResolveInfos.get(position).loadLabel(getPackageManager()));
            holder.icon.setImageDrawable(mResolveInfos.get(position).loadIcon(getPackageManager()));
        }

        @Override
        public Object getItem(int position) {
            return mResolveInfos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
    
    private void init(){
        mViewPager = (ViewPager) findViewById(R.id.viewPager);
        mViewPager.setAdapter(mChooserPagerAdapter);
        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(int arg0) {
                mIndicatorView.setState(mChooserPagerAdapter.getCount(), arg0);
            }

            @Override
            public void onPageScrollStateChanged(int arg0) {
            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {
            }
        });
    }
    
    private final class ChooserPagerAdapter extends PagerAdapter {
        public ChooserPagerAdapter(List<GridView> gridViews) {
            this.mGridList = gridViews;
        }

        private List<GridView> mGridList;

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == arg1;
        }

        @Override
        public void destroyItem(View container, int position, Object object) {
            ((ViewPager) container).removeView((View) object);
        }

        @Override
        public Object instantiateItem(View container, int position) {
            ((ViewPager) container).addView(mGridList.get(position));
            return mGridList.get(position);
        }

        @Override
        public int getCount() {
            return mGridList != null ? mGridList.size() : 0;
        }

        @Override
        public int getItemPosition(Object object) {
            int position = mGridList.indexOf(object);
            if (position == -1) {
                return PagerAdapter.POSITION_NONE;
            } else {
                return position;
            }
        }
    }
    
    private static Intent chooserIntent(String shareType, Uri shareExtraStream, String readString) {
        Intent targetedShare = new Intent(android.content.Intent.ACTION_SEND);

        targetedShare.setAction(Intent.ACTION_SEND);
        targetedShare.setType(shareType);
        if (!TextUtils.isEmpty(readString)) {
            targetedShare.putExtra(Intent.EXTRA_TEXT, readString);
        }
        if (shareType.contains("image") && shareExtraStream != null) {
            targetedShare.putExtra(Intent.EXTRA_STREAM, shareExtraStream);
        }
        targetedShare.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return targetedShare;
    }
}
