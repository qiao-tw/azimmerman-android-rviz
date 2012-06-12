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
package org.ros.android.rviz_for_android.layersTF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

import org.ros.android.rviz_for_android.MainActivity;
import org.ros.android.rviz_for_android.vis.DefaultLayerTF;
import org.ros.android.view.visualization.Camera;
import org.ros.android.view.visualization.Utility;
import org.ros.android.view.visualization.layer.DefaultLayer;
import org.ros.node.ConnectedNode;
import org.ros.rosjava.tf.TransformTree;
import org.ros.rosjava_geometry.FrameTransformTree;

import android.os.Handler;
import android.util.Log;

public class FPSLayerTF extends DefaultLayerTF {

	private boolean isLoaded = false;
	private TexFont txt;

	private int width, height;
	private long now = 0l;
	private long last = 0l;
	private long dT = 0l;
	private double framerate = 0.0;

	private double avgRate = 0l;
	private int ratecount = 0;
	
	// Window limit should scale with FPS to keep the update rate the same.
	private static final int FPS_RECALCULATIONS_PER_SECOND = 2;
	private int windowLimit = 10;

	private int toDraw = 0;

	@Override
	public void draw(GL10 gl) {
		now = System.nanoTime();
		dT = now - last;
		if(dT != 0)
			framerate = 1000000000.0/dT;
		last = now;

		avgRate += framerate;
		ratecount++;

		if(!isLoaded) {
			txt = new TexFont(MainActivity.getAppContext(), gl);
			try {
				txt.LoadFont("TestFont.bff", gl);
				isLoaded = true;
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		if(ratecount == windowLimit) {
			txt.SetScale(1);
			toDraw = Utility.cap((int)Math.round(avgRate / ratecount),99);
			avgRate = 0;
			ratecount = 0;
			windowLimit = Utility.cap(toDraw / FPS_RECALCULATIONS_PER_SECOND, 5, 50);
		}
		txt.PrintAt(gl, "FPS: " + toDraw, width - 50, height - 26);
	}

	@Override
	public void onStart(ConnectedNode connectedNode, Handler handler, TransformTree transformTree, Camera camera) {
		width = camera.getViewport().getWidth();
		height = camera.getViewport().getHeight();
	}

}