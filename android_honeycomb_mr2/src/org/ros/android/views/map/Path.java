/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android.views.map;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public class Path implements OpenGlDrawable {
  /**
   * Vertices for the path.
   */
  private FloatBuffer pathVertexBuffer;

  private int pathIndexCount;
  
  /**
   * Creates a new set of points to render. These points represent the path
   * generated by the navigation planner.
   * 
   * @param newPath
   *          The path generated by the planner.
   * @param resolution
   *          The resolution of the current map.
   */
  public void update(org.ros.message.nav_msgs.Path path) {
    float[] pathVertices = new float[path.poses.size() * 3];
    // Add the path coordinates to the array.
    for (int i = 0; i < path.poses.size(); i++) {
      pathVertices[i * 3] = (float) path.poses.get(i).pose.position.x;
      pathVertices[i * 3 + 1] = (float) path.poses.get(i).pose.position.y;
      pathVertices[i * 3 + 2] = 0f;
    }
    updateVertices(path, pathVertices);
   }

  private void updateVertices(org.ros.message.nav_msgs.Path path, float[] vertices) {
    ByteBuffer pathVertexByteBuffer =
        ByteBuffer.allocateDirect(vertices.length * Float.SIZE / 8);
    pathVertexByteBuffer.order(ByteOrder.nativeOrder());
    pathVertexBuffer = pathVertexByteBuffer.asFloatBuffer();
    pathVertexBuffer.put(vertices);
    pathVertexBuffer.position(0);
    pathIndexCount = path.poses.size();
  }
  
  public void init() {
    float[] pathVertices = new float[3];
    // 0,0
    pathVertices[0] = 0f;
    pathVertices[1] = 0f;
    pathVertices[2] = 0f;
    ByteBuffer pathVertexByteBuffer =
        ByteBuffer.allocateDirect(pathVertices.length * Float.SIZE / 8);
    pathVertexByteBuffer.order(ByteOrder.nativeOrder());
    pathVertexBuffer = pathVertexByteBuffer.asFloatBuffer();
    pathVertexBuffer.put(pathVertices);
    pathVertexBuffer.position(0);
    pathIndexCount = 0;
  }

  @Override
  public void draw(GL10 gl) {
    gl.glEnable(GL10.GL_POINT_SMOOTH);
    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, pathVertexBuffer);
    gl.glPointSize(2);
    gl.glColor4f(0.2f, 0.8f, 0.2f, 1f);
    gl.glDrawArrays(GL10.GL_POINTS, 0, pathIndexCount);
    gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
    gl.glDisable(GL10.GL_POINT_SMOOTH);
  }
}
