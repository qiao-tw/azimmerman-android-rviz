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
package org.ros.android.rviz_for_android.layers;

import geometry_msgs.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.opengles.GL10;

import org.ros.android.renderer.Camera;
import org.ros.android.renderer.shapes.BaseShapeInterface;
import org.ros.android.renderer.shapes.Color;
import org.ros.android.renderer.shapes.GenericColoredShape;
import org.ros.android.rviz_for_android.drawable.Arrow;
import org.ros.android.rviz_for_android.drawable.ColladaMesh;
import org.ros.android.rviz_for_android.drawable.Cube;
import org.ros.android.rviz_for_android.drawable.Cylinder;
import org.ros.android.rviz_for_android.drawable.Sphere;
import org.ros.android.rviz_for_android.drawable.StlMesh;
import org.ros.android.rviz_for_android.prop.ButtonProperty;
import org.ros.android.rviz_for_android.prop.LayerWithProperties;
import org.ros.android.rviz_for_android.prop.Property;
import org.ros.android.rviz_for_android.prop.Property.PropertyUpdateListener;
import org.ros.android.rviz_for_android.prop.ReadOnlyProperty.StatusColor;
import org.ros.android.rviz_for_android.urdf.MeshFileDownloader;
import org.ros.android.rviz_for_android.urdf.UrdfDrawable;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.rosjava_geometry.FrameTransformTree;
import org.ros.rosjava_geometry.Transform;

import visualization_msgs.Marker;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.opengl.GLES20;
import android.os.Handler;
import android.util.Log;

public class MarkerLayer extends EditableStatusSubscriberLayer<visualization_msgs.Marker> implements LayerWithProperties {

	// List of all received namespaces and all namespaces to draw
	private List<String> namespaceList = new LinkedList<String>();
	private List<String> enabledNamespaces = new LinkedList<String>();

	// Map from namespace to map from ID to marker
	private Map<String, HashMap<Integer, MarkerObj>> markers = new HashMap<String, HashMap<Integer, MarkerObj>>();

	private FrameTransformTree ftt;
	private long nextPruneTime;
	private static final long PRUNE_PERIOD = 300; // Milliseconds
	private Object lockObj = new Object();
	private final MeshFileDownloader mfd;

	public MarkerLayer(GraphName topicName, String messageType, Camera cam, MeshFileDownloader mfd) {
		super(topicName, messageType, cam);
		this.mfd = mfd;
		nextPruneTime = System.currentTimeMillis() + PRUNE_PERIOD;
		super.prop.addSubProperty(new ButtonProperty("Namespaces ", "Select", new PropertyUpdateListener<String>() {
			@Override
			public void onPropertyChanged(String newval) {
				showNamespaceSelectDialog();
			}
		}));
	}

	@Override
	protected void onMessageReceived(Marker msg) {
		super.onMessageReceived(msg);
//		Log.i("MarkerLayer", "MSGS: " + super.messageCount);
		String ns = msg.getNs();
		int id = msg.getId();

		synchronized(lockObj) {
			switch(msg.getAction()) {
			case Marker.ADD:
				if(!markers.containsKey(ns)) {
					markers.put(ns, new HashMap<Integer, MarkerObj>());
					enabledNamespaces.add(ns);
					namespaceList.add(ns);
				}
				markers.get(ns).put(id, new MarkerObj(msg, super.camera, mfd));
				break;
			case Marker.DELETE:
				Log.i("MarkerLayer", "Deleting marker " + ns + ":" + id);
				if(markers.containsKey(ns))
					markers.get(ns).remove(id);
				break;
			default:
				Log.e("MarkerLayer", "Received a message with unknown action " + msg.getAction());
				return;
			}
		}
	}

	@Override
	public void draw(GL10 glUnused) {
		synchronized(lockObj) {
			for(String namespace : enabledNamespaces) {
				for(MarkerObj mo : markers.get(namespace).values()) {
					camera.pushM();
					if(mo.frame != null)
						camera.applyTransform(ftt.newTransformIfPossible(camera.getFixedFrame(), mo.frame));
					camera.scaleM(mo.scale[0], mo.scale[1], mo.scale[2]);
					mo.draw(glUnused);
					camera.popM();
				}
			}
			if(System.currentTimeMillis() >= nextPruneTime)
				pruneMarkers();
		}
	}

	private void pruneMarkers() {
		boolean error = false;
		
		// Prune markers which have expired
		for(HashMap<Integer, MarkerObj> hm : markers.values()) {
			List<Integer> removeIds = new LinkedList<Integer>();
			for(Integer i : hm.keySet()) {
				MarkerObj mo = hm.get(i);
				if(mo.isExpired())
					removeIds.add(i);
				else if(mo.markerType == MarkerObj.DrawType.ERROR) {
					super.statusController.setFrameChecking(false);
					super.statusController.setStatus("Marker " + mo.id + " in " + mo.namespace + " is invalid", StatusColor.WARN);
					error = true;
				}
			}
			for(Integer i : removeIds) {
				hm.remove(i);
			}
		}

		if(!error && super.messageCount > 0)
			super.statusController.setFrameChecking(true);

		nextPruneTime += PRUNE_PERIOD;

	}

	@Override
	public void onStart(ConnectedNode connectedNode, Handler handler, FrameTransformTree frameTransformTree, Camera camera) {
		super.onStart(connectedNode, handler, frameTransformTree, camera);
		this.ftt = frameTransformTree;
	}

	@Override
	public Property<?> getProperties() {
		return super.prop;
	}

	@Override
	public boolean isEnabled() {
		return prop.getValue();
	}

	@Override
	protected String getMessageFrameId(Marker msg) {
		return msg.getHeader().getFrameId();
	}

	protected void showNamespaceSelectDialog() {
		int count = namespaceList.size();
		boolean[] selected = new boolean[count];
		CharSequence[] namespacesArray = new CharSequence[count];

		for(int i = 0; i < count; i++) {
			selected[i] = enabledNamespaces.contains(namespaceList.get(i));
			namespacesArray[i] = namespaceList.get(i);
		}

		DialogInterface.OnMultiChoiceClickListener coloursDialogListener = new DialogInterface.OnMultiChoiceClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which, boolean isChecked) {
				synchronized(lockObj) {
					if(isChecked)
						enabledNamespaces.add(namespaceList.get(which));
					else
						enabledNamespaces.remove(namespaceList.get(which));
				}
			}
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(mfd.getContext());
		builder.setTitle("Select Namespaces");
		builder.setMultiChoiceItems(namespacesArray, selected, coloursDialogListener);
		builder.setNeutralButton("Ok", null);

		AlertDialog dialog = builder.create();
		dialog.show();
	}

	// TODO: Make MarkerObj its own class and clean it up?
	private static class MarkerObj {
		public static enum DrawType {
			PRIMITIVE, ARRAY, MESH, SHAPE, ERROR
		};

		private static final float[] UNIT_SCALE = new float[] { 1f, 1f, 1f };
		private static Map<String, BaseShapeInterface> loadedMeshes = new HashMap<String, BaseShapeInterface>();

		private Camera cam;
		private MeshFileDownloader mfd;
		private BaseShapeInterface shape;
		private GraphName frame;
		private Color color;
		private long endTime;
		private int duration;
		private float[] scale;
		private int type;
		private DrawType markerType = DrawType.SHAPE;
		private boolean useMaterials = false;
		private Transform shapeTrans;

		private String namespace;
		private int id;

		private int shapeArraySize;
		private List<Point> shapeArrayPositions;
		private List<Color> shapeArrayColors;
		private boolean individualShapeArrayColors;

		public MarkerObj(Marker msg, Camera cam, MeshFileDownloader mfd) {
			namespace = msg.getNs();
			id = msg.getId();

			this.cam = cam;
			this.mfd = mfd;
			this.type = msg.getType();
			this.frame = msg.getFrameLocked() ? GraphName.of(msg.getHeader().getFrameId()) : null;
			this.scale = new float[] { (float) msg.getScale().getX(), (float) msg.getScale().getY(), (float) msg.getScale().getZ() };
			this.duration = msg.getLifetime().secs * 1000;
			this.endTime = System.currentTimeMillis() + duration;
			this.color = new Color(msg.getColor().getR(), msg.getColor().getG(), msg.getColor().getB(), msg.getColor().getA());
			this.useMaterials = msg.getMeshUseEmbeddedMaterials();

			switch(type) {
			case Marker.CUBE:
				shape = new Cube(cam);
				break;
			case Marker.SPHERE:
				shape = new Sphere(cam, 0.5f);
				break;
			case Marker.CYLINDER:
				shape = new Cylinder(cam, 0.5f, 1f);
				break;
			case Marker.ARROW:
				shape = new Arrow(cam);
				break;
			case Marker.MESH_RESOURCE:
				markerType = DrawType.MESH;
				if(!loadedMeshes.containsKey(msg.getMeshResource())) {
					shape = (BaseShapeInterface) loadMesh(msg.getMeshResource());
					loadedMeshes.put(msg.getMeshResource(), shape);
				} else {
					shape = loadedMeshes.get(msg.getMeshResource());
				}
				break;
			case Marker.CUBE_LIST:
				shape = new Cube(cam);
				initArray(msg);
				markerType = DrawType.ARRAY;
				if(!individualShapeArrayColors)
					shape.setColor(color);
				break;
			case Marker.SPHERE_LIST:
				shape = new Sphere(cam, 0.5f);
				initArray(msg);
				markerType = DrawType.ARRAY;
				if(!individualShapeArrayColors)
					shape.setColor(color);
				break;
			case Marker.LINE_LIST:
				initArray(msg);
				markerType = DrawType.PRIMITIVE;
				float[] vertices = initPrimitivePositions();
				float[] colors = initPrimitiveColors();
				if(vertices.length % 2 == 0) {
					if(colors == null)
						shape = new GenericColoredShape(cam, GLES20.GL_LINES, vertices);
					else
						shape = new GenericColoredShape(cam, GLES20.GL_LINES, vertices, colors);
					scale = UNIT_SCALE;
				} else {
					markerType = DrawType.ERROR;
				}
				break;
			case Marker.LINE_STRIP:
				initArray(msg);
				markerType = DrawType.PRIMITIVE;
				vertices = initPrimitivePositions();
				colors = initPrimitiveColors();
				if(vertices.length % 2 == 0) {
					if(colors == null)
						shape = new GenericColoredShape(cam, GLES20.GL_LINE_STRIP, vertices);
					else
						shape = new GenericColoredShape(cam, GLES20.GL_LINE_STRIP, vertices, colors);
					scale = UNIT_SCALE;
				} else {
					markerType = DrawType.ERROR;
				}
				break;
			case Marker.TRIANGLE_LIST:
				initArray(msg);
				markerType = DrawType.PRIMITIVE;
				vertices = initPrimitivePositions();
				colors = initPrimitiveColors();
				if(vertices.length % 3 == 0) {
					if(colors == null)
						shape = new GenericColoredShape(cam, GLES20.GL_TRIANGLES, vertices);
					else
						shape = new GenericColoredShape(cam, GLES20.GL_TRIANGLES, vertices, colors);
					scale = UNIT_SCALE;
				} else {
					markerType = DrawType.ERROR;
				}
				break;
			case Marker.POINTS:
				initArray(msg);
				markerType = DrawType.PRIMITIVE;
				vertices = initPrimitivePositions();
				colors = initPrimitiveColors();
				if(colors == null)
					shape = new GenericColoredShape(cam, GLES20.GL_POINTS, vertices);
				else
					shape = new GenericColoredShape(cam, GLES20.GL_POINTS, vertices, colors);
				scale = UNIT_SCALE;
				break;
			default:
				Log.e("MarkerLayer", "Unknown marker type: " + msg.getType());
				markerType = DrawType.ERROR;
			}

			if(markerType != DrawType.ERROR) {
				Log.d("MarkerLayer", "New marker, " + namespace + " : " + id + "  of type " + markerType.toString() + " : " + shape.toString());
				shapeTrans = Transform.fromPoseMessage(msg.getPose());
				shape.setTransform(shapeTrans);
				shape.setColor(this.color);
			}
		}

		private float[] initPrimitivePositions() {
			markerType = DrawType.PRIMITIVE;
			float[] vertices = new float[shapeArraySize * 3];
			int idx = 0;
			for(Point p : shapeArrayPositions) {
				vertices[idx++] = (float) p.getX();
				vertices[idx++] = (float) p.getY();
				vertices[idx++] = (float) p.getZ();
			}
			return vertices;
		}

		private float[] initPrimitiveColors() {
			float[] colors = null;
			if(individualShapeArrayColors) {
				colors = new float[shapeArraySize * 4];
				int idx = 0;
				for(Color c : shapeArrayColors) {
					colors[idx++] = c.getRed();
					colors[idx++] = c.getGreen();
					colors[idx++] = c.getBlue();
					colors[idx++] = c.getAlpha();
				}
			}
			return colors;
		}

		private void initArray(Marker msg) {
			shapeArrayPositions = msg.getPoints();
			shapeArraySize = shapeArrayPositions.size();
			individualShapeArrayColors = (shapeArraySize == msg.getColors().size());

			if(individualShapeArrayColors) {
				shapeArrayColors = new ArrayList<Color>();
				for(std_msgs.ColorRGBA c : msg.getColors())
					shapeArrayColors.add(new Color(c.getR(), c.getG(), c.getB(), c.getA()));
			}
		}

		private static final Color WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);

		public void draw(GL10 glUnused) {
			if(markerType == DrawType.MESH) {
				if(type != Marker.MESH_RESOURCE || !useMaterials)
					shape.setColor(this.color);
				else
					shape.setColor(WHITE);
				shape.setTransform(shapeTrans);
				shape.draw(glUnused);
			} else if(markerType == DrawType.ARRAY) {
				cam.pushM();
				cam.applyTransform(shapeTrans);
				for(int i = 0; i < shapeArraySize; i++) {
					cam.pushM();
					Point p = shapeArrayPositions.get(i);
					cam.translateM((float) p.getX(), (float) p.getY(), (float) p.getZ());
					if(individualShapeArrayColors)
						shape.setColor(shapeArrayColors.get(i));
					shape.draw(glUnused);
					cam.popM();
				}
				cam.popM();
			} else if(markerType != DrawType.ERROR) {
				shape.draw(glUnused);
			}
		}

		public boolean isExpired() {
			if(duration == 0)
				return false;
			else
				return System.currentTimeMillis() > endTime;
		}

		private UrdfDrawable loadMesh(String meshResourceName) {
			UrdfDrawable ud;
			if(meshResourceName.toLowerCase().endsWith(".dae")) {
				ud = ColladaMesh.newFromFile(meshResourceName, mfd, cam);
			} else if(meshResourceName.toLowerCase().endsWith(".stl")) {
				ud = StlMesh.newFromFile(meshResourceName, mfd, cam);
			} else {
				Log.e("MarkerLayer", "Unknown mesh type! " + meshResourceName);
				return null;
			}

			return ud;
		}
	}
}
