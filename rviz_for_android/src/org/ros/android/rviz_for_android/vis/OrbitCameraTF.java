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
package org.ros.android.rviz_for_android.vis;

import javax.microedition.khronos.opengles.GL10;
import javax.vecmath.Vector3d;

import org.ros.android.view.visualization.Camera;
import org.ros.android.view.visualization.Utility;
import org.ros.android.view.visualization.Viewport;
import org.ros.namespace.GraphName;
import org.ros.rosjava.tf.TransformTree;
import org.ros.rosjava_geometry.FrameTransformTree;
import org.ros.rosjava_geometry.Transform;
import org.ros.rosjava_geometry.Vector3;

import com.google.common.base.Preconditions;

import android.graphics.Point;

public class OrbitCameraTF implements Camera {
	/**
	 * The default reference frame.
	 * 
	 * TODO(moesenle): make this the root of the TF tree.
	 */
	private static final String DEFAULT_FIXED_FRAME = "/world";

	/**
	 * The default target frame is null which means that the renderer uses the user set camera.
	 */
	private static final String DEFAULT_TARGET_FRAME = null;

	private float orbitRadius = 5.0f;
	private static final float MAX_FLING_VELOCITY = 25;
	private static final float MIN_FLING_VELOCITY = 0.05f;
	private static final float MAX_TRANSLATE_SPEED = 0.18f;

	private float angleTheta = (float) (Math.PI / 4);
	private float anglePhi = (float) (Math.PI / 4);
	private Vector3 location;
	private Vector3 lookTarget;

	private float vTheta = 0;
	private float vPhi = 0;

	/**
	 * Size of the viewport.
	 */
	private Viewport viewport;

	/**
	 * The TF frame the camera is locked on. If set, the camera point is set to the location of this frame in fixedFrame. If the camera is set or moved, the lock is removed.
	 */
	private String targetFrame;

	/**
	 * The frame in which to render everything. The default value is /map which indicates that everything is rendered in map. If this is changed to, for instance, base_link, the view follows the robot and the robot itself is in the origin.
	 */
	private String fixedFrame;

	private TransformTree transformTree;
	
	public OrbitCameraTF(TransformTree transformTree) {
		this.transformTree = transformTree;
		fixedFrame = DEFAULT_FIXED_FRAME;
		location = Vector3.newIdentityVector3();
		lookTarget = Vector3.newIdentityVector3();
		updateLocation();
		location = location.add(lookTarget);
	}

	@Override
	public void apply(GL10 gl) {
		viewport.zoom(gl);
		velocityUpdate();
		
		synchronized(fixedFrame) {
			if(targetFrame != null && transformTree.canTransform(fixedFrame, targetFrame)) {
				Vector3d trans = transformTree.lookupMostRecent(fixedFrame, targetFrame).getTranslation();
				lookTarget.setX(trans.x/2);
				lookTarget.setY(trans.y/2);
				lookTarget.setZ(trans.z/2);
				updateLocation();
			}
		}
		
		rotateOrbit(gl);
	}

	private void rotateOrbit(GL10 gl) {
		android.opengl.GLU.gluLookAt(gl, (float) location.getX(), (float) location.getY(), (float) location.getZ(), (float) lookTarget.getX(), (float) lookTarget.getY(), (float) lookTarget.getZ(), 0, 0, 1f);
		gl.glTranslatef(-(float) location.getX(), -(float) location.getY(), -(float) location.getZ());
	}
	
	private void updateLocation() {
		location.setX((float) lookTarget.getX() + (orbitRadius * Math.sin(angleTheta) * Math.cos(anglePhi)));
		location.setY((float) lookTarget.getY() + (orbitRadius * Math.sin(angleTheta) * Math.sin(anglePhi)));
		location.setZ((float) lookTarget.getZ() + (orbitRadius * Math.cos(angleTheta)));
	}

	private void velocityUpdate() {
		if(vTheta != 0f || vPhi != 0f) {
			moveOrbitPosition(vPhi, vTheta);
			vTheta *= .9f;
			vPhi *= .9f;
		}

		if(Math.abs(vTheta) < MIN_FLING_VELOCITY)
			vTheta = 0;
		if(Math.abs(vPhi) < MIN_FLING_VELOCITY)
			vPhi = 0;
	}


	public void flingCamera(float vX, float vY) {
		vPhi = Utility.cap(-vX / 500, -MAX_FLING_VELOCITY, MAX_FLING_VELOCITY);
		vTheta = Utility.cap(-vY / 500, -MAX_FLING_VELOCITY, MAX_FLING_VELOCITY);
	}

	public void moveOrbitPosition(float xDistance, float yDistance) {
		anglePhi += Math.toRadians(xDistance);
		anglePhi = Utility.angleWrap(anglePhi);

		angleTheta += Math.toRadians(yDistance);
		angleTheta = Utility.cap(angleTheta, 0.00872664626f, 3.13286601f);

		updateLocation();
	}

	@Override
	public void moveCameraScreenCoordinates(float xDistance, float yDistance) {
		targetFrame = null;

		float xDistCap = Utility.cap(xDistance, -MAX_TRANSLATE_SPEED, MAX_TRANSLATE_SPEED);
		float yDistCap = Utility.cap(yDistance, -MAX_TRANSLATE_SPEED, MAX_TRANSLATE_SPEED);

		lookTarget = lookTarget.subtract(new Vector3(Math.cos(anglePhi - Math.PI / 2) * xDistCap - Math.sin(anglePhi + Math.PI / 2) * yDistCap, Math.sin(anglePhi - Math.PI / 2) * xDistCap + Math.cos(anglePhi + Math.PI / 2) * yDistCap, 0));
		updateLocation();
	}
	

	public void setCamera(Vector3 newCameraPoint) {
		resetTargetFrame();
		lookTarget = newCameraPoint;
	}

	public Vector3 getCamera() {
		return location;
	}

	public void zoomCamera(float factor) {
		orbitRadius /= factor;
	}

	public GraphName getFixedFrame() {
		return new GraphName(fixedFrame);
	}

	public void setFixedFrame(GraphName fixedFrame) {
		Preconditions.checkNotNull(fixedFrame, "Fixed frame must be specified.");
		this.fixedFrame = fixedFrame.toString();
	}

	public void resetFixedFrame() {
		synchronized(fixedFrame) {
			fixedFrame = DEFAULT_FIXED_FRAME;
		}
	}

	public void resetTargetFrame() {
		targetFrame = DEFAULT_TARGET_FRAME;
	}

	public void setTargetFrame(GraphName frame) {
		targetFrame = frame.toString();
	}

	public GraphName getTargetFrame() {
		return new GraphName(targetFrame);
	}
	
	// String frame manipulation
	public String getTargetFrameString() {
		return targetFrame;
	}
	public void setTargetFrameString(String frame) {
		targetFrame = frame;
	}
	public String getFixedFrameString() {
		return fixedFrame;
	}
	public void setFixedFrameString(String frame) {
		fixedFrame = frame;
	}
	
	

	public Viewport getViewport() {
		return viewport;
	}

	public void setViewport(Viewport viewport) {
		this.viewport = viewport;
	}

	public float getZoom() {
		return viewport.getZoom();
	}

	public void setZoom(float zoom) {
		viewport.setZoom(zoom);
	}
	
	@Override
	public Vector3 toWorldCoordinates(Point screenPoint) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Transform toOpenGLPose(Point goalScreenPoint, float orientation) {
		// TODO Auto-generated method stub
		return null;
	}
}