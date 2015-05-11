
package com.smartisan.notes.intentchooser;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        List<String> pkg = new ArrayList<>();
        pkg.add("com.sina.weibo");
        
        Intent intent = new Intent(MainActivity.this, ResolverActivity.class);
        
        LabeledIntent[] labeledIntents = shareExludingApp(getApplicationContext(), pkg, "text/plain", "123", null);
        
        intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, labeledIntents);
        
        startActivity(intent);
    }

    public static LabeledIntent[] shareExludingApp(Context context, List<String> packageNameToExclude, String shareType,
            String shareTextContent, Uri shareExtraStream) {
        // String readString = null;
        // if (shareTextContent != null && shareTextContent.length() > 20000) {
        // readString = shareTextContent.subSequence(0, 20000).toString();
        // } else {
        // readString = shareTextContent;
        // }
        Intent shareToIntent = chooserIntent(shareType, shareExtraStream, shareTextContent);
        if (packageNameToExclude == null || packageNameToExclude.isEmpty()) {
            return null;
        }

        List<Intent> targetedShareIntents = new ArrayList<Intent>();
        List<ResolveInfo> resInfo = context.getPackageManager().queryIntentActivities(
                shareToIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!resInfo.isEmpty()) {
            for (ResolveInfo info : resInfo) {
                if (!packageNameToExclude.contains(info.activityInfo.packageName)) {
                    Intent targetedShare = chooserIntent(shareType, shareExtraStream, shareTextContent);
                    targetedShare.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
                    targetedShare.setPackage(info.activityInfo.packageName);
                    targetedShareIntents.add(new LabeledIntent(targetedShare, info.activityInfo.packageName, info
                            .loadLabel(context.getPackageManager()), info.icon));
                }
            }
            // add sina item
            Intent sinaIntent = chooserIntent(shareType, shareExtraStream, shareTextContent);
            sinaIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ComponentName cn = new ComponentName(context, MainActivity.class);
            sinaIntent.setComponent(cn);
//            sinaIntent.putExtra(Constants.IS_LONG_WEIBO, shareExtraStream != null);
//            sinaIntent.putExtra(Constants.NORMAL_WEIBO_CONTENT, shareTextContent);
//            sinaIntent.putExtra(LongLengthWeiboActivity.IMAGE_FILE_NAME, getRealPath(shareExtraStream, context));
            targetedShareIntents.add(1,
                    new LabeledIntent(sinaIntent, context.getPackageName(), context.getText(R.string.share_note_weibo),
                            R.drawable.ic_launcher));

            if (targetedShareIntents.size() > 0) {
                Intent chooserIntent = Intent.createChooser(targetedShareIntents.remove(0),
                        context.getText(R.string.share_dialog_send_method));
                LabeledIntent[] li = targetedShareIntents.toArray(new LabeledIntent[targetedShareIntents.size()]);

                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, li);
                return li;
            } else {
                return null;
            }
        }
        return null;
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
