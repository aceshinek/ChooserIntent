package com.smartisan.notes.intentchooser;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class ChooserActivity extends Activity {

    public static final String EXCLOUD_PKG = "excloud_pkg";

    private String mType = "";
    private String mExtraText = "";
    private Uri mExtraStream = null;
    private int flag = Intent.FLAG_ACTIVITY_NEW_TASK;
    private ArrayList<String> mExcloud;
    
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
        
        
        Intent shareToIntent = chooserIntent(mType  , mExtraStream, mExtraText);

        List<Intent> targetedShareIntents = new ArrayList<Intent>();
        List<ResolveInfo> resInfo = getPackageManager().queryIntentActivities(
                shareToIntent, PackageManager.MATCH_DEFAULT_ONLY);
        
        for (ResolveInfo info : resInfo) {
            if (!mExcloud.contains(info.activityInfo.packageName)) {
                Intent targetedShare = new Intent(Intent.ACTION_SEND);
//                targetedShare.setAction(Intent.ACTION_SEND);
                targetedShare.setType(mType);
                targetedShare.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                targetedShare.putExtra(Intent.EXTRA_TEXT, mExtraText);
                targetedShare.putExtra(Intent.EXTRA_STREAM, mExtraStream);
                targetedShare.putExtra("sms_body", mExtraText);
                targetedShare.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
                targetedShare.setPackage(info.activityInfo.packageName);
                targetedShareIntents.add(new LabeledIntent(targetedShare, info.activityInfo.packageName, info.loadLabel(getPackageManager()), info.icon));

                Log.d("ChooserActivity_onCreate", "package_name: " + info.activityInfo.packageName + "   activity: " + info.activityInfo.name);
                if ("com.android.mms".equals(info.activityInfo.packageName)) {
                    startActivity(targetedShare);
                    finish();
                }
//                startActivity(targetedShare);
//                finish();
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
