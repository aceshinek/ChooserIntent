package com.smartisan.notes.intentchooser;


import smartisanos.app.IndicatorView;
import smartisanos.view.PagerAdapter;
import smartisanos.view.ViewPager;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AppGlobals;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This activity is displayed when the system attempts to start an Intent for
 * which there is more than one matching activity, allowing the user to decide
 * which to go to.  It is not normally used directly by application developers.
 */
public class ResolverActivity extends Activity {
    private static final String TAG = "ResolverActivity";
    private static final boolean DEBUG = false;

    private static final int RIGHT_HAND_MODE = 1;
    private static final String EXTRA_COPY_AND_TOAST = "android.intent.extra.CLICK_TOAST";

    private int mLaunchedFromUid;
    private ResolveInfoListBuilder mAdapter;
    private ResolvePagerAdapter mPageOffsetAdapter;
    private PackageManager mPm;
    private boolean mAlwaysUseOption;
    private View mContentView;
    private ViewPager mViewPager;
    private IndicatorView mIndicatorView;
    private TextView mTitleView;
    private int mIconDpi;
    private int mMaxPageGridCount;
    private int mTextMaxWidth;
    private int mIconShadowColor;
    private boolean mIsPortait;
    private int mLastSelected = -1;
    private Button mConfirmButton;
    private Button mSetDefaultButton;
    private ViewHolder mCheckedFrame;
    private String mLinkToCopy;
    private String mCopiedToastText;

    private boolean mRegistered;

    private Intent makeMyIntent() {
        Intent intent = new Intent(getIntent());
        intent.setComponent(null);
        // The resolver activity is set to be hidden from recent tasks.
        // we don't want this attribute to be propagated to the next activity
        // being launched.  Note that if the original Intent also had this
        // flag set, we are now losing it.  That should be a very rare case
        // and we can live with this.
        intent.setFlags(intent.getFlags()&~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Use a specialized prompt when we're handling the 'Home' app startActivity()
        final int titleResource;
        final Intent intent = makeMyIntent();
        final Set<String> categories = intent.getCategories();
        if (Intent.ACTION_MAIN.equals(intent.getAction())
                && categories != null
                && categories.size() == 1
                && categories.contains(Intent.CATEGORY_HOME)) {
            titleResource = com.android.internal.R.string.whichHomeApplication;
        } else {
            titleResource = com.android.internal.R.string.whichApplication;
        }

        onCreate(savedInstanceState, intent, getResources().getText(titleResource),
                null, null, true);
    }

    protected void onCreate(Bundle savedInstanceState, Intent intent,
            CharSequence title, Intent[] initialIntents, List<ResolveInfo> rList,
            boolean alwaysUseOption) {
        super.onCreate(savedInstanceState);
        mContentView = getLayoutInflater().inflate(R.layout.resolver_layout, null);
        setContentView(mContentView);
        Window window = getWindow();
        window.setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams lp = window.getAttributes();
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        lockOritation(display.getRotation());
        Point point = new Point();
        display.getSize(point);
        lp.width = point.x;
        window.setAttributes(lp);

        try {
            mLaunchedFromUid = ActivityManagerNative.getDefault().getLaunchedFromUid(
                    getActivityToken());
        } catch (RemoteException e) {
            mLaunchedFromUid = -1;
        }
        mPm = getPackageManager();
        mAlwaysUseOption = canAlwaysUse(alwaysUseOption, intent);
        mRegistered = true;
        final ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        mIconDpi = am.getLauncherLargeIconDensity();

        final Resources res = getResources();
        mTextMaxWidth = mIsPortait ? res.getDimensionPixelSize(R.dimen.text_max_length_portrait)
                : res.getDimensionPixelSize(R.dimen.text_max_length_landscape);
        final int maxColumns = res.getInteger(R.integer.maxGridColumns);
        mMaxPageGridCount = maxColumns * res.getInteger(R.integer.maxGridRows);
        mIconShadowColor = res.getColor(R.color.icon_shadow);

        mAdapter = new ResolveInfoListBuilder(this, intent, initialIntents, rList,
                mLaunchedFromUid);
        int count = mAdapter.getCount();
        if (mLaunchedFromUid < 0 || UserHandle.isIsolated(mLaunchedFromUid)) {
            // Gulp!
            finish();
            return;
        } else if (count > 1) {
            if (count <= mMaxPageGridCount) {
                View contentPanel = (View) findViewById(R.id.contentPanel);
                LayoutParams rlp = contentPanel.getLayoutParams();
                rlp.height = ((count + maxColumns - 1) / maxColumns)
                        * res.getDimensionPixelSize(R.dimen.grid_item_height)
                        + res.getDimensionPixelSize(R.dimen.grid_bottom_padding);
                contentPanel.setLayoutParams(rlp);
            }

            mTitleView = (TextView) findViewById(R.id.resolver_title);
            mTitleView.setText(title);

            final Button btnCancel = (Button) findViewById(R.id.btn_cancel);
            btnCancel.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            mSetDefaultButton = (Button) findViewById(R.id.btn_set_default);
            mConfirmButton = (Button) findViewById(R.id.btn_confirm);
            final boolean rightHand = Settings.Global.getInt(
                    getContentResolver(), Settings.Global.ONE_HAND_MODE,
                    RIGHT_HAND_MODE) == RIGHT_HAND_MODE;
            if (mAlwaysUseOption) {
                mSetDefaultButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        animateToShowConfirm();
                    }
                });
                if (!rightHand) {
                    RelativeLayout.LayoutParams blp = (RelativeLayout.LayoutParams) mSetDefaultButton
                            .getLayoutParams();
                    blp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    blp.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    mSetDefaultButton.setLayoutParams(blp);
                    blp = (RelativeLayout.LayoutParams) mConfirmButton
                            .getLayoutParams();
                    blp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    blp.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    mConfirmButton.setLayoutParams(blp);
                    blp = (RelativeLayout.LayoutParams) btnCancel.getLayoutParams();
                    blp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    blp.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    btnCancel.setLayoutParams(blp);
                }
            } else {
                if (rightHand) {
                    RelativeLayout.LayoutParams blp = (RelativeLayout.LayoutParams) btnCancel.getLayoutParams();
                    blp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    blp.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    btnCancel.setLayoutParams(blp);
                }
                mSetDefaultButton.setVisibility(View.INVISIBLE);
            }

            mAdapter.initAdapter();
            mViewPager = (ViewPager) findViewById(R.id.viewPager);
            mViewPager.setAdapter(mPageOffsetAdapter);
            mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

                @Override
                public void onPageSelected(int arg0) {
                    mIndicatorView.setState(mPageOffsetAdapter.getCount(), arg0);
                }

                @Override
                public void onPageScrollStateChanged(int arg0) {
                }

                @Override
                public void onPageScrolled(int arg0, float arg1, int arg2) {
                }
            });
            mIndicatorView = (IndicatorView) findViewById(R.id.indicator);
            mIndicatorView.setState(mPageOffsetAdapter.getCount(), 0);
            if (count <= mMaxPageGridCount) {
                ViewGroup.LayoutParams vlp = mIndicatorView.getLayoutParams();
                vlp.height = res.getDimensionPixelSize(R.dimen.indicator_min_height);
                mIndicatorView.setLayoutParams(vlp);
                mIndicatorView.setVisibility(View.INVISIBLE);
            }

            if (!mAlwaysUseOption && intent.hasExtra(EXTRA_COPY_AND_TOAST)) {
                mLinkToCopy = intent.getStringExtra(Intent.EXTRA_TEXT);;
                mCopiedToastText = intent.getStringExtra(EXTRA_COPY_AND_TOAST);
            }
        } else if (count == 1) {
            startActivity(mAdapter.intentForPosition(0));
            mRegistered = false;
            super.finish();
            return;
        } else {
            Toast.makeText(this, com.android.internal.R.string.noApplications,
                    Toast.LENGTH_LONG).show();
            super.finish();
            return;
        }
    }

    // Don't set AlwaysUse when sending email without SENDTO action.
    boolean canAlwaysUse(boolean alwaysUseOption, Intent intent) {
        return alwaysUseOption
                && (!"mailto".equals(intent.getScheme()) || Intent.ACTION_SENDTO
                        .equals(intent.getAction()));
    }

    void animateToShowConfirm() {
        AnimationSet hideAnim = (AnimationSet) AnimationUtils
                .loadAnimation(this, R.anim.resolver_hide_set_default);
        mContentView.startAnimation(hideAnim);
        (new Handler()).postDelayed(new Runnable() {
            @Override
            public void run() {
                AnimationSet showAnim = (AnimationSet) AnimationUtils
                        .loadAnimation(getBaseContext(), R.anim.resolver_show_confirm);
                mContentView.startAnimation(showAnim);
                mConfirmButton.setVisibility(View.VISIBLE);
                mConfirmButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mLastSelected != -1) {
                            startSelected(mLastSelected, true);
                            finish();
                        }
                    }
                });
                mSetDefaultButton.setVisibility(View.INVISIBLE);
                mTitleView.setText(R.string.title_set_default);
                // Rebuild list to show unchecked frame
                mAdapter.initAdapter();
                mPageOffsetAdapter.notifyDataSetChanged();
            }
        }, hideAnim.getDuration());
    }

    void lockOritation(int rotation) {
        int orientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND;
        mIsPortait = false;
        switch (rotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                mIsPortait = true;
                break;
            case Surface.ROTATION_90:
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                break;
            case Surface.ROTATION_270:
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                break;
        }
        setRequestedOrientation(orientation);
    }

    Drawable getIcon(Resources res, int resId) {
        Drawable result;
        try {
            result = res.getDrawableForDensity(resId, mIconDpi);
        } catch (Resources.NotFoundException e) {
            result = null;
        }

        return result;
    }

    Drawable loadIconForResolveInfo(ResolveInfo ri) {
        Drawable dr;
        try {
            if (ri.resolvePackageName != null && ri.icon != 0) {
                dr = getIcon(mPm.getResourcesForApplication(ri.resolvePackageName), ri.icon);
                if (dr != null) {
                    return dr;
                }
            }
            final int iconRes = ri.getIconResource();
            if (iconRes != 0) {
                dr = getIcon(mPm.getResourcesForApplication(ri.activityInfo.packageName), iconRes);
                if (dr != null) {
                    return dr;
                }
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't find resources for package", e);
        }
        return ri.loadIcon(mPm);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fake_anim, com.android.internal.R.anim.dock_bottom_exit);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (!mRegistered) {
            mRegistered = true;
        }
        mAdapter.handlePackagesChanged();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mRegistered) {
            mRegistered = false;
        }
        if ((getIntent().getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
            // This resolver is in the unusual situation where it has been
            // launched at the top of a new task.  We don't let it be added
            // to the recent tasks shown to the user, and we need to make sure
            // that each time we are launched we get the correct launching
            // uid (not re-using the same resolver from an old launching uid),
            // so we will now finish ourself since being no longer visible,
            // the user probably can't get back to us.
            if (!isChangingConfigurations()) {
                finish();
            }
        }
    }

    void startSelected(int which, boolean always) {
        if (isFinishing()) {
            return;
        }
        ResolveInfo ri = mAdapter.resolveInfoForPosition(which);
        Intent intent = mAdapter.intentForPosition(which);
        onIntentSelected(ri, intent, always);
        super.finish();
    }

    protected void onIntentSelected(ResolveInfo ri, Intent intent, boolean alwaysCheck) {
        if (mAlwaysUseOption && mAdapter.mOrigResolveList != null) {
            // Build a reasonable intent filter, based on what matched.
            IntentFilter filter = new IntentFilter();

            if (intent.getAction() != null) {
                filter.addAction(intent.getAction());
            }
            Set<String> categories = intent.getCategories();
            if (categories != null) {
                for (String cat : categories) {
                    filter.addCategory(cat);
                }
            }
            filter.addCategory(Intent.CATEGORY_DEFAULT);

            int cat = ri.match&IntentFilter.MATCH_CATEGORY_MASK;
            Uri data = intent.getData();
            if (cat == IntentFilter.MATCH_CATEGORY_TYPE) {
                String mimeType = intent.resolveType(this);
                if (mimeType != null) {
                    try {
                        filter.addDataType(mimeType);
                    } catch (IntentFilter.MalformedMimeTypeException e) {
                        Log.w("ResolverActivity", e);
                        filter = null;
                    }
                }
            }

            if (data != null && data.getScheme() != null) {
                final String scheme = data.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    filter.addDataScheme("http");
                    filter.addDataScheme("https");
                } else {
                    filter.addDataScheme(scheme);
                }

                // Look through "ALL" the resolved filters to determine which
                // parts
                // of them matched the original Intent.
                final int N = mAdapter.mOrigResolveList.size();
                final String ssp = data.getSchemeSpecificPart();
                final String path = data.getPath();
                boolean addDataSsp = ssp == null;
                boolean addDataAuthority = false;
                boolean addDataPath = path == null;
                for (int i = 0; i < N; ++i) {
                    ResolveInfo r = mAdapter.mOrigResolveList.get(i);
                    if (!addDataSsp) {
                        Iterator<PatternMatcher> pIt = r.filter
                                .schemeSpecificPartsIterator();
                        if (pIt != null) {
                            while (pIt.hasNext()) {
                                PatternMatcher p = pIt.next();
                                if (p.match(ssp)) {
                                    filter.addDataSchemeSpecificPart(
                                            p.getPath(), p.getType());
                                    addDataSsp = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!addDataAuthority) {
                        Iterator<IntentFilter.AuthorityEntry> aIt = r.filter
                                .authoritiesIterator();
                        if (aIt != null) {
                            while (aIt.hasNext()) {
                                IntentFilter.AuthorityEntry a = aIt.next();
                                if (a.match(data) >= 0) {
                                    int port = a.getPort();
                                    filter.addDataAuthority(a.getHost(),
                                            port >= 0 ? Integer.toString(port)
                                                    : null);
                                    addDataAuthority = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!addDataPath) {
                        Iterator<PatternMatcher> pIt = r.filter.pathsIterator();
                        if (pIt != null) {
                            while (path != null && pIt.hasNext()) {
                                PatternMatcher p = pIt.next();
                                if (p.match(path)) {
                                    filter.addDataPath(p.getPath(), p.getType());
                                    addDataPath = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (addDataSsp && addDataAuthority && addDataPath)
                        break;
                }
            }

            if (filter != null) {
                final int N = mAdapter.mOrigResolveList.size();
                ComponentName[] set = new ComponentName[N];
                int bestMatch = 0;
                for (int i=0; i<N; i++) {
                    ResolveInfo r = mAdapter.mOrigResolveList.get(i);
                    set[i] = new ComponentName(r.activityInfo.packageName,
                            r.activityInfo.name);
                    if (r.match > bestMatch) bestMatch = r.match;
                }
                if (alwaysCheck) {
                    getPackageManager().addPreferredActivity(filter, bestMatch, set,
                            intent.getComponent());
                    Toast.makeText(this, R.string.set_default_success,
                            Toast.LENGTH_LONG).show();
                } else {
                    try {
                        AppGlobals.getPackageManager().setLastChosenActivity(intent,
                                intent.resolveTypeIfNeeded(getContentResolver()),
                                PackageManager.MATCH_DEFAULT_ONLY,
                                filter, bestMatch, intent.getComponent());
                    } catch (RemoteException re) {
                        Log.d(TAG, "Error calling setLastChosenActivity\n" + re);
                    }
                }
            }
        }

        if (intent != null) {
//            if (!mAlwaysUseOption) {
//                getPackageManager().updateChosenActivities(
//                        new ComponentName(ri.activityInfo.packageName,
//                                ri.activityInfo.name).flattenToShortString(),
//                        ri.chosenPriority + 1);
//            }
            if (!TextUtils.isEmpty(mLinkToCopy)) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText(null, mLinkToCopy));
                Toast.makeText(this, mCopiedToastText, Toast.LENGTH_LONG).show();
            }
            startActivity(intent);
        }
    }

    void showAppDetails(ResolveInfo ri) {
        Intent in = new Intent().setAction("android.settings.APPLICATION_DETAILS_SETTINGS")
                .setData(Uri.fromParts("package", ri.activityInfo.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        startActivity(in);
    }

    private final class DisplayResolveInfo {
        ResolveInfo ri;
        CharSequence displayLabel;
        Drawable displayIcon;
        CharSequence extendedInfo;
        Intent origIntent;

        DisplayResolveInfo(ResolveInfo pri, CharSequence pLabel,
                CharSequence pInfo, Intent pOrigIntent) {
            ri = pri;
            displayLabel = pLabel;
            extendedInfo = pInfo;
            origIntent = pOrigIntent;
        }
    }

    private final class ResolveInfoListBuilder {

        // Make system apps front, instead of ResolveInfo.DisplayNameComparator
        private class ResolverComparator implements Comparator<ResolveInfo> {

            public ResolverComparator(PackageManager pm) {
                mPM = pm;
                mCollator.setStrength(Collator.PRIMARY);
            }

            public final int compare(ResolveInfo a, ResolveInfo b) {
                if (a.chosenPriority < b.chosenPriority) return 1;
                if (a.chosenPriority > b.chosenPriority) return -1;
                int fa = a.activityInfo.applicationInfo.flags & FLAG_SYSTEM_APP;
                int fb = b.activityInfo.applicationInfo.flags & FLAG_SYSTEM_APP;
                if (fa < fb) return 1;
                if (fa > fb) return -1;

                CharSequence  sa = a.loadLabel(mPM);
                if (sa == null) sa = a.activityInfo.name;
                CharSequence  sb = b.loadLabel(mPM);
                if (sb == null) sb = b.activityInfo.name;

                return mCollator.compare(sa.toString(), sb.toString());
            }

            private final static int FLAG_SYSTEM_APP = ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            private final Collator mCollator = Collator.getInstance();
            private PackageManager mPM;
        }

        private final Intent[] mInitialIntents;
        private final List<ResolveInfo> mBaseResolveList;
        private final Intent mIntent;
        private final int mLaunchedFromUid;

        List<DisplayResolveInfo> mList;
        List<ResolveInfo> mOrigResolveList;
        private Context mContext;

        public ResolveInfoListBuilder(Context context, Intent intent,
                Intent[] initialIntents, List<ResolveInfo> rList, int launchedFromUid) {
            mIntent = new Intent(intent);
            mInitialIntents = initialIntents;
            mBaseResolveList = rList;
            mLaunchedFromUid = launchedFromUid;
            mContext = context;
            mList = new ArrayList<DisplayResolveInfo>();

            rebuildList();
        }

        public void initAdapter() {
            if (mPageOffsetAdapter == null) {
                mPageOffsetAdapter = new ResolvePagerAdapter();
            }
            ArrayList<GridView> gList = new ArrayList<GridView>();
            int gridCount = getCount();
            for(int i = 0; i < gridCount; i += mMaxPageGridCount) {
                LayoutInflater inflater = LayoutInflater.from(ResolverActivity.this);
                View view = (View) inflater.inflate(R.layout.resolver_grid, null);
                GridView gridView = (GridView) view.findViewById(R.id.gridView);
                ViewGroup parent = (ViewGroup) gridView.getParent();
                if (parent != null) {
                    parent.removeView(gridView);
                }
                ResolveListAdapter adapter = new ResolveListAdapter(mContext, mIntent, i);
                gridView.setAdapter(adapter);
                gridView.setChoiceMode(GridView.CHOICE_MODE_SINGLE);
                gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> parent, View view,
                            int position, long id) {
                        final ViewHolder holder = (ViewHolder) view.getTag();
                        holder.icon.setColorFilter(mIconShadowColor);
                        final int page = ((ResolveListAdapter) parent.getAdapter()).mPageOffset;
                        if (View.VISIBLE == mConfirmButton.getVisibility()) {
                            int checkedPos = position + page;
                            if (mLastSelected != checkedPos) {
                                mConfirmButton.setEnabled(true);
                                mLastSelected = checkedPos;
                                holder.checked.setVisibility(View.VISIBLE);
                                holder.unchecked.setVisibility(View.INVISIBLE);
                                if (mCheckedFrame != null) {
                                    mCheckedFrame.checked.setVisibility(View.INVISIBLE);
                                    mCheckedFrame.unchecked.setVisibility(View.VISIBLE);
                                    mCheckedFrame.icon.clearColorFilter();
                                }
                                mCheckedFrame = holder;
                            }
                        } else {
                            startSelected(position + page, false);
                        }
                    }
                });
                gridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent,
                            View view, int position, long id) {
                        final int page = ((ResolveListAdapter) parent.getAdapter()).mPageOffset;
                        ResolveInfo ri = mAdapter.resolveInfoForPosition(position + page);
                        showAppDetails(ri);
                        return true;
                    }
                });
                gList.add(gridView);
            }
            mPageOffsetAdapter.setList(gList);
        }

        public void handlePackagesChanged() {
            int oldPageCount = mPageOffsetAdapter.getCount();
            rebuildList();
            initAdapter();
            mPageOffsetAdapter.notifyDataSetChanged();
            if (getCount() <= 0) {
                // We no longer have any items...  just finish the activity.
                finish();
            }
            int newPageConut = mPageOffsetAdapter.getCount();
            if (newPageConut != oldPageCount) {
                mIndicatorView.setState(newPageConut);
            }
        }

        private void rebuildList() {
            List<ResolveInfo> currentResolveList;

            mList.clear();
            if (mBaseResolveList != null) {
                currentResolveList = mBaseResolveList;
                mOrigResolveList = null;
            } else {
                currentResolveList = mOrigResolveList = mPm.queryIntentActivities(
                        mIntent, PackageManager.MATCH_DEFAULT_ONLY
                        | (mAlwaysUseOption ? PackageManager.GET_RESOLVED_FILTER : 0));
                // Filter out any activities that the launched uid does not
                // have permission for.  We don't do this when we have an explicit
                // list of resolved activities, because that only happens when
                // we are being subclassed, so we can safely launch whatever
                // they gave us.
                if (currentResolveList != null) {
                    for (int i=currentResolveList.size()-1; i >= 0; i--) {
                        ActivityInfo ai = currentResolveList.get(i).activityInfo;
                        int granted = ActivityManager.checkComponentPermission(
                                ai.permission, mLaunchedFromUid,
                                ai.applicationInfo.uid, ai.exported);
                        if (granted != PackageManager.PERMISSION_GRANTED) {
                            // Access not allowed!
                            if (mOrigResolveList == currentResolveList) {
                                mOrigResolveList = new ArrayList<ResolveInfo>(mOrigResolveList);
                            }
                            currentResolveList.remove(i);
                        }
                    }
                }
            }
            int N;
            if ((currentResolveList != null) && ((N = currentResolveList.size()) > 0)) {
                // Only display the first matches that are either of equal
                // priority or have asked to be default options.
                ResolveInfo r0 = currentResolveList.get(0);
                for (int i=1; i<N; i++) {
                    ResolveInfo ri = currentResolveList.get(i);
                    if (DEBUG) Log.v(
                        "ResolveListActivity",
                        r0.activityInfo.name + "=" +
                        r0.priority + "/" + r0.isDefault + " vs " +
                        ri.activityInfo.name + "=" +
                        ri.priority + "/" + ri.isDefault);
                    if (r0.priority != ri.priority ||
                        r0.isDefault != ri.isDefault) {
                        while (i < N) {
                            if (mOrigResolveList == currentResolveList) {
                                mOrigResolveList = new ArrayList<ResolveInfo>(mOrigResolveList);
                            }
                            // This is a bug fix of below commit
                            // commit 6d8dfbd8149942f25f2b9643a12f1fb38f3be834
                            // Author: Dianne Hackborn <hackbod@google.com>
                            // Date:   Tue Sep 24 08:38:51 2013
                            // PreferredComponent.sameSet(*) will check the priority
                            // just use the mOrigResolveList to addPreferredActivity
                            // will fail if the Resolver filter the set by priority.
                            if (r0.priority != ri.priority) {
                                mOrigResolveList.remove(currentResolveList.get(i));
                            }
                            currentResolveList.remove(i);
                            N--;
                        }
                    }
                }
                if (N > 1) {
                    Collections.sort(currentResolveList, new ResolverComparator(mPm));
                }

                // First put the initial items at the top.
                if (mInitialIntents != null) {
                    for (int i=0; i<mInitialIntents.length; i++) {
                        Intent ii = mInitialIntents[i];
                        if (ii == null) {
                            continue;
                        }
                        ActivityInfo ai = ii.resolveActivityInfo(
                                getPackageManager(), 0);
                        if (ai == null) {
                            Log.w("ResolverActivity", "No activity found for "
                                    + ii);
                            continue;
                        }
                        ResolveInfo ri = new ResolveInfo();
                        ri.activityInfo = ai;
                        if (ii instanceof LabeledIntent) {
                            LabeledIntent li = (LabeledIntent)ii;
                            ri.resolvePackageName = li.getSourcePackage();
                            ri.labelRes = li.getLabelResource();
                            ri.nonLocalizedLabel = li.getNonLocalizedLabel();
                            ri.icon = li.getIconResource();
                        }
                        mList.add(new DisplayResolveInfo(ri,
                                ri.loadLabel(getPackageManager()), null, ii));
                    }
                }

                // Check for applications with same name and use application name or
                // package name if necessary
                r0 = currentResolveList.get(0);
                int start = 0;
                CharSequence r0Label =  r0.loadLabel(mPm);
                for (int i = 1; i < N; i++) {
                    if (r0Label == null) {
                        r0Label = r0.activityInfo.packageName;
                    }
                    ResolveInfo ri = currentResolveList.get(i);
                    CharSequence riLabel = ri.loadLabel(mPm);
                    if (riLabel == null) {
                        riLabel = ri.activityInfo.packageName;
                    }
                    if (riLabel.equals(r0Label)) {
                        continue;
                    }
                    processGroup(currentResolveList, start, (i-1), r0, r0Label);
                    r0 = ri;
                    r0Label = riLabel;
                    start = i;
                }
                // Process last group
                processGroup(currentResolveList, start, (N-1), r0, r0Label);
            }
        }

        private void processGroup(List<ResolveInfo> rList, int start, int end, ResolveInfo ro,
                CharSequence roLabel) {
            // Process labels from start to i
            int num = end - start+1;
            if (num == 1) {
                // No duplicate labels. Use label for entry at start
                mList.add(new DisplayResolveInfo(ro, roLabel, null, null));
            } else {
                boolean usePkg = false;
                CharSequence startApp = ro.activityInfo.applicationInfo.loadLabel(mPm);
                if (startApp == null) {
                    usePkg = true;
                }
                if (!usePkg) {
                    // Use HashSet to track duplicates
                    HashSet<CharSequence> duplicates =
                        new HashSet<CharSequence>();
                    duplicates.add(startApp);
                    for (int j = start+1; j <= end ; j++) {
                        ResolveInfo jRi = rList.get(j);
                        CharSequence jApp = jRi.activityInfo.applicationInfo.loadLabel(mPm);
                        if ( (jApp == null) || (duplicates.contains(jApp))) {
                            usePkg = true;
                            break;
                        } else {
                            duplicates.add(jApp);
                        }
                    }
                    // Clear HashSet for later use
                    duplicates.clear();
                }
                for (int k = start; k <= end; k++) {
                    ResolveInfo add = rList.get(k);
                    if (usePkg) {
                        // Use application name for all entries from start to end-1
                        mList.add(new DisplayResolveInfo(add, roLabel,
                                add.activityInfo.packageName, null));
                    } else {
                        // Use package name for all entries from start to end-1
                        mList.add(new DisplayResolveInfo(add, roLabel,
                                add.activityInfo.applicationInfo.loadLabel(mPm), null));
                    }
                }
            }
        }

        public ResolveInfo resolveInfoForPosition(int position) {
            return mList.get(position).ri;
        }

        public Intent intentForPosition(int position) {
            DisplayResolveInfo dri = mList.get(position);

            Intent intent = new Intent(dri.origIntent != null
                    ? dri.origIntent : mIntent);
            intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT
                    |Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
            ActivityInfo ai = dri.ri.activityInfo;
            intent.setComponent(new ComponentName(
                    ai.applicationInfo.packageName, ai.name));
            return intent;
        }

        public int getCount() {
            return mList.size();
        }
    }

    private final class ResolveListAdapter extends BaseAdapter {
        private final Intent mIntent;
        private final LayoutInflater mInflater;
        private final int mPageOffset;
        private final int size;

        public ResolveListAdapter(Context context, Intent intent, int page) {
            mIntent = intent;
            mInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mPageOffset = page;
            int totalSize = mAdapter.mList.size();
            if (mPageOffset + mMaxPageGridCount <= totalSize) {
                size = mMaxPageGridCount;
            } else {
                size = totalSize - mPageOffset;
            }
        }

        public int getCount() {
            return size;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            if (convertView == null) {
                view = mInflater.inflate(R.layout.resolver_grid_item, parent, false);

                final ViewHolder holder = new ViewHolder(view);
                view.setTag(holder);
                if (position + mPageOffset == mLastSelected
                        && mCheckedFrame != holder) {
                    holder.icon.setColorFilter(mIconShadowColor);
                    holder.checked.setVisibility(View.VISIBLE);
                    mCheckedFrame = holder;
                } else if (View.VISIBLE == mConfirmButton.getVisibility()) {
                    holder.unchecked.setVisibility(View.VISIBLE);
                }
            } else {
                view = convertView;
            }
            bindView(view, position);
            return view;
        }

        private final void bindView(View view, final int position) {
            final ViewHolder holder = (ViewHolder) view.getTag();
            DisplayResolveInfo info = mAdapter.mList.get(mPageOffset + position);
            holder.text.setMaxWidth(mTextMaxWidth);
            holder.text.setText(info.displayLabel);
            if (info.displayIcon == null) {
                info.displayIcon = loadIconForResolveInfo(info.ri);
            }
            holder.icon.setImageDrawable(info.displayIcon);
        }

        @Override
        public Object getItem(int position) {
            return mAdapter.mList.get(mPageOffset + position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    static class ViewHolder {
        public TextView text;
        public ImageView icon;
        public ImageView checked;
        public ImageView unchecked;

        public ViewHolder(View view) {
            text = (TextView) view.findViewById(R.id.icon_text);
            icon = (ImageView) view.findViewById(R.id.icon_image);
            checked = (ImageView) view.findViewById(R.id.checked_image);
            unchecked = (ImageView) view.findViewById(R.id.unchecked_image);
        }
    }

    private final class ResolvePagerAdapter extends PagerAdapter {
        private List<GridView> mGridList;

        public void setList(List<GridView> gList) {
            mGridList = gList;
        }

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
}
