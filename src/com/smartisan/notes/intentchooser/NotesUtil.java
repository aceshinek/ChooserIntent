
package com.smartisan.notes.intentchooser;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.telephony.PhoneNumberUtils;
import android.text.Layout;
import android.text.Selection;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotesUtil {
    private static final String TAG = "NoteUtil";
    public static final String ACTION_SMARTISAN_INSERT_OR_EDIT = "com.android.contact.activities.ContactSelectionActivity.smartisanInsertOrEdit";

    public static final String SEND_SHARE_WEIBO_OR_IMAGE = "isWeibo";

    private NotesUtil() {
    }



    /**
     * @param context
     * @param packageNameToExclude
     * @param shareType "text/plain"
     * @param shareTextContent
     * @return
     */
    public static Intent shareExludingApp(Context context, List<String> packageNameToExclude, String shareType,
            String shareTextContent, Uri shareExtraStream) {
        // String readString = null;
        // if (shareTextContent != null && shareTextContent.length() > 20000) {
        // readString = shareTextContent.subSequence(0, 20000).toString();
        // } else {
        // readString = shareTextContent;
        // }
        Intent shareToIntent = chooserIntent(shareType, shareExtraStream, shareTextContent);
        if (packageNameToExclude == null || packageNameToExclude.isEmpty()) {
            return shareToIntent;
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

            if (targetedShareIntents.size() > 0) {
                Intent chooserIntent = Intent.createChooser(targetedShareIntents.remove(0),"001");
                LabeledIntent[] li = targetedShareIntents.toArray(new LabeledIntent[targetedShareIntents.size()]);

                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, li);
                return chooserIntent;
            } else {
                return new Intent();
            }
        }
        return new Intent();
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
