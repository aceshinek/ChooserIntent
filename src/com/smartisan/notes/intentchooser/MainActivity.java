
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
//        
//        Intent labeledIntents = shareExludingApp(getApplicationContext(), pkg, "text/plain", "123", null);
//        
//        intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, labeledIntents);
        
        startActivity(intent);
    }

    
}
