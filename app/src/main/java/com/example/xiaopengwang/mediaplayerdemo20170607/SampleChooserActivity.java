/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.example.xiaopengwang.mediaplayerdemo20170607;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An activity for selecting from a number of samples.
 */
public class SampleChooserActivity extends Activity {

    ExpandableListView sampleList;
    final List<SampleGroup> sampleGroups = new ArrayList<>();

    // Customer should set this URL
    private String mGatewayURL = "https://testonly.conviva.com";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_chooser_activity);

        sampleList = (ExpandableListView) findViewById(R.id.sample_list);

        if(!isStoragePermissionGranted()) {
            SampleGroup group = new SampleGroup("HLS");
            group.addAll(Samples.HLS);
            sampleGroups.add(group);
            group = new SampleGroup("HLS-Live");
            group.addAll(Samples.HLS_LIVE);
            sampleGroups.add(group);
            group = new SampleGroup("MP4");
            group.addAll(Samples.MP4);
            sampleGroups.add(group);

            sampleList.setAdapter(new SampleAdapter(this, sampleGroups));
        }

        sampleList.setOnChildClickListener(new OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View view, int groupPosition,
                                        int childPosition, long id) {
                onSampleSelected(sampleGroups.get(groupPosition).samples.get(childPosition));
                return true;
            }
        });

    }

    private String readSampleURLJson() {
        String path = Environment.getExternalStorageDirectory().getPath() + "/" +
                "sample_config.json";
        File file = new File(path);
        String jsonConfigString = null;
        if(file != null) {
            StringBuilder stringBuilder = new StringBuilder();

            try {
                java.io.BufferedReader bfr = null;
                bfr = new java.io.BufferedReader(
                        new java.io.FileReader(path));
                String line;
                while( ( line = bfr.readLine() ) != null ) {
                    stringBuilder.append( line );
                }
                bfr.close();
                jsonConfigString = stringBuilder.toString();
                return jsonConfigString;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void onSampleSelected(Samples.Sample sample) {
        Intent mpdIntent = new Intent(this, PlayerActivity.class)
                .setData(Uri.parse(sample.uri));
        mpdIntent.putExtra("gatewayUrl", mGatewayURL);
        startActivity(mpdIntent);
    }

    private static final class SampleAdapter extends BaseExpandableListAdapter {

        private final Context context;
        private final List<SampleGroup> sampleGroups;

        public SampleAdapter(Context context, List<SampleGroup> sampleGroups) {
            this.context = context;
            this.sampleGroups = sampleGroups;
        }

        @Override
        public Samples.Sample getChild(int groupPosition, int childPosition) {
            return getGroup(groupPosition).samples.get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                                 View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent,
                        false);
            }
            ((TextView) view).setText(getChild(groupPosition, childPosition).name);
            return view;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return getGroup(groupPosition).samples.size();
        }

        @Override
        public SampleGroup getGroup(int groupPosition) {
            return sampleGroups.get(groupPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                                 ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(context).inflate(R.layout.sample_chooser_inline_header, parent,
                        false);
            }
            ((TextView) view).setText(getGroup(groupPosition).title);
            return view;
        }

        @Override
        public int getGroupCount() {
            return sampleGroups.size();
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

    }
    public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                return loadJSONContent();
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            return loadJSONContent();
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
            loadJSONContent();
        }
    }



    private boolean loadJSONContent() {
        String jsonConfigString = readSampleURLJson();
        if (jsonConfigString != null) {
            try {
                JSONObject obj = new JSONObject(jsonConfigString);
                mGatewayURL = obj.optString("cwsGateway");
                JSONArray array = obj.getJSONArray("protocols");
                sampleGroups.clear();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject jsonObject = array.getJSONObject(i);
                    final Samples.Sample[] sample  = new Samples.Sample[] {
                            new Samples.Sample(jsonObject.getString("test_label"),
                                    jsonObject.getString("contentSrc"))
                    };
                    SampleGroup group = new SampleGroup(jsonObject.getString("video_label"));
                    group.addAll(sample);
                    sampleGroups.add(group);
                }
                sampleList.setAdapter(new SampleAdapter(this, sampleGroups));

                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static final class SampleGroup {

        public final String title;
        public final List<Samples.Sample> samples;

        public SampleGroup(String title) {
            this.title = title;
            this.samples = new ArrayList<>();
        }

        public void addAll(Samples.Sample[] samples) {
            Collections.addAll(this.samples, samples);
        }

    }

}
