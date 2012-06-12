/*
 * Copyright (c) 2012, Willow Garage, Inc.
 * All rights reserved.
 *
 * Willow Garage licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.ros.android.rviz_for_android.prop;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ros.android.rviz_for_android.R;
import org.ros.rosjava.tf.TransformTree;
import org.ros.rosjava.tf.adt.AvailableFrameTracker.FrameAddedListener;

import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

public class TfFrameProperty extends Property<String> {

	private static final ArrayList<String> defaultFrameList = new ArrayList<String>(Arrays.asList(new String[] { "<Fixed Frame>" }));
	private Set<Integer> elementsToIgnore = new HashSet<Integer>();
	private List<String> defaultList;
	private int defaultListSize = 1;

	private List<String> framesToList = new ArrayList<String>();
	private List<String> spinnerFrameList;

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if(aa != null) {
				generateSpinnerContents();
				aa.notifyDataSetChanged();
			}
		}
	};

	private ArrayAdapter<String> aa;

	private int selection = 0;
	private TransformTree tt;
	private TextView textView;
	private Spinner spin;

	public TfFrameProperty(String name, String value, PropertyUpdateListener<String> updateListener, TransformTree ftt) {
		super(name, value, updateListener);
		this.setTransformTree(ftt);

		this.addUpdateListener(new PropertyUpdateListener<String>() {
			public void onPropertyChanged(String newval) {
				if(newval == null) {
					selection = 0;
				}
			}
		});
		setDefaultList(defaultFrameList, 0);
		spinnerFrameList = defaultList;
	}

	private void generateSpinnerContents() {
		if(tt != null) {
			framesToList.clear();
			framesToList.addAll(0, defaultList);
			Set<String> framesFromFtt = tt.getTracker().getAvailableFrames();
			for(String s : framesFromFtt) {
				if(!framesToList.contains(s))
					framesToList.add(s);
			}
			spinnerFrameList = framesToList;
		} else {
			spinnerFrameList = defaultList;
		}
	}

	/**
	 * Provide a default list of options to show. All available GraphNames will be listed AFTER these default options.
	 * 
	 * @param def
	 *            the list of options to show
	 * @param toIgnore
	 *            optional, the indices of elements in the list to "ignore". When items at these indices are selected, the value will be set to null. Useful for making '<Fixed Frame>', an invalid GraphName, an option.
	 * @return self
	 */
	public TfFrameProperty setDefaultList(List<String> def, int... toIgnore) {
		defaultList = def;
		defaultListSize = def.size();
		elementsToIgnore.clear();
		for(int i : toIgnore) {
			if(i > defaultListSize - 1)
				throw new InvalidParameterException("Can not ignore a frame that's not in your default list!");
			elementsToIgnore.add(i);
		}
		return this;
	}

	public TfFrameProperty setDefaultItem(String defaultItem, boolean toIgnore) {
		ArrayList<String> newDefault = new ArrayList<String>();
		newDefault.add(defaultItem);
		if(toIgnore)
			return this.setDefaultList(newDefault, 0);
		else
			return this.setDefaultList(newDefault);
	}

	@Override
	public View getGUI(View convertView, ViewGroup parent, LayoutInflater inflater, String title) {
		convertView = inflater.inflate(R.layout.row_property_spinner, parent, false);

		textView = (TextView) convertView.findViewById(R.id.tvProp_Spinner_Name);
		if(title != null)
			textView.setText(title);
		else
			textView.setText(super.name);

		generateSpinnerContents();
		if(aa == null) {
			aa = new ArrayAdapter<String>(parent.getContext(), android.R.layout.simple_spinner_item, spinnerFrameList);
			aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		}

		spin = (Spinner) convertView.findViewById(R.id.spProp_Spinner);

		spin.setAdapter(aa);
		spin.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> arg0, View v, int position, long id) {
				selection = position;
				if(elementsToIgnore.contains(selection))
					setValue(null);
				else {
					setValue(spinnerFrameList.get(selection));
				}
			}

			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

		spin.setSelection(selection);
		return convertView;
	}

	public void setTransformTree(TransformTree tt) {
		this.tt = tt;
		if(tt != null) {
			tt.getTracker().addListener(new FrameAddedListener() {
				public void informFrameAdded(Set<String> newFrames) {
					handler.sendEmptyMessage(0);
				}
			});
		}
	}

}