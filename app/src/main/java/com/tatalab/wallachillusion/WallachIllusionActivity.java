/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tatalab.wallachillusion;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Google VR sample application.
 * </p><p>
 * The TreasureHunt scene consists of a planar ground grid and a floating
 * "treasure" cube. When the user looks at the cube, the cube will turn gold.
 * While gold, the user can activate the Carboard trigger, which will in turn
 * randomly reposition the cube.
 */
public class WallachIllusionActivity extends GvrActivity implements GvrView.StereoRenderer {

  protected float[] moving2xCube;
  protected float[] moving2xPosition;

  protected float[] movingCube;
  protected float[] movingPosition;

  protected float[] staticCube;
  protected float[] staticPosition;

  private static final String TAG = "WallachIllusionActivity";

  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 100.0f;

  private static final float CAMERA_Z = 0.01f;
  private static final float TIME_DELTA = 0.3f;

  private static final float YAW_LIMIT = 0.12f;
  private static final float PITCH_LIMIT = 0.12f;

  private static final int COORDS_PER_VERTEX = 3;

  // We keep the light always position just above the user.
  private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[] {0.0f, 2.0f, 0.0f, 1.0f};

  // Convenience vector for extracting the position from a matrix via multiplication.
  private static final float[] POS_MATRIX_MULTIPLY_VEC = {0, 0, 0, 1.0f};

  private static final float MIN_MODEL_DISTANCE = 3.0f;
  private static final float MAX_MODEL_DISTANCE = 7.0f;

  private static final String SOUND_FILE = "cube_sound.wav";

  private final float[] lightPosInEyeSpace = new float[4];

  private FloatBuffer floorVertices;
  private FloatBuffer floorColors;
  private FloatBuffer floorNormals;

  private FloatBuffer moving2xVertices;
  private FloatBuffer moving2xColors;
  private FloatBuffer moving2xNormals;

  private FloatBuffer cubeVertices;
  private FloatBuffer cubeColors;
  private FloatBuffer cubeNormals;

  private FloatBuffer staticVertices;
  private FloatBuffer staticColors;
  private FloatBuffer staticNormals;

  private int moving2xProgram;
  private int cubeProgram;
  private int staticProgram;
  private int floorProgram;

  private int moving2xPositionParam;
  private int moving2xNormalParam;
  private int moving2xColorParam;
  private int moving2xModelParam;
  private int moving2xModelViewParam;
  private int moving2xModelViewProjectionParam;
  private int moving2xLightPosParam;

  private int cubePositionParam;
  private int cubeNormalParam;
  private int cubeColorParam;
  private int cubeModelParam;
  private int cubeModelViewParam;
  private int cubeModelViewProjectionParam;
  private int cubeLightPosParam;

  private int staticPositionParam;
  private int staticNormalParam;
  private int staticColorParam;
  private int staticModelParam;
  private int staticModelViewParam;
  private int staticModelViewProjectionParam;
  private int staticLightPosParam;

  private int floorPositionParam;
  private int floorNormalParam;
  private int floorColorParam;
  private int floorModelParam;
  private int floorModelViewParam;
  private int floorModelViewProjectionParam;
  private int floorLightPosParam;

  private float[] camera;
  private float[] view;
  private float[] headView;
  private float[] modelViewProjection;
  private float[] modelView;
  private float[] modelFloor;

  private float[] tempPosition;
  private float[] headRotation;
  private float[] forwardVector;
  private float[] prevForwardVector;

  private int rotationMode = 0;

  private float objectDistance = MAX_MODEL_DISTANCE / 2.0f;
  private float floorDepth = 20f;

  private Vibrator vibrator;

  //private GvrAudioEngine gvrAudioEngine;
  //private volatile int soundId = GvrAudioEngine.INVALID_ID;

  private double rotationTest = 0;



  double c;
  double dist;

  int numBeamsPerHemifield;
  int numLags;

  int shiftAmount[];
  int angle = 0;

  private void initAudioParams() {
    c = 336.628;
    dist = 0.14;

    numBeamsPerHemifield = (int) Math.ceil( (dist/c)*48000 );
    numLags = 2*numBeamsPerHemifield +1;

    //only shifts of even numbers work correctly, so numlags gets divided by two, and then the
    //shift amount is multiplied by two.
    numLags /= 2;


    shiftAmount = new int[360];

    for(int i=0; i<90; i++)
      shiftAmount[i] = (int) ((i/90.0)*numLags);

    for(int i=90; i<180; i++)
      shiftAmount[i] = numLags - (int) ((i%90/90.0)*numLags);

    for(int i=180; i<270; i++)
      shiftAmount[i] = (int) ((i%90/90.0)*numLags);

    for(int i=270; i<360; i++)
      shiftAmount[i] = numLags - (int) ((i%90/90.0)*numLags);
  }

  public void playWav(){

    while(true) {


      int minBufferSize = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
      int bufferSize = 512;
      AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, 48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM);


      int i, j, k;

      byte[] s0 = new byte[bufferSize];
      byte[] s1 = new byte[bufferSize];
      byte[] s2 = new byte[bufferSize];
      try {
        InputStream is = getAssets().open("cube_sound.wav");

        i = is.read(s0, 0, bufferSize);
        j = is.read(s1, 0, bufferSize);


        at.play();


        while ((k = is.read(s2, 0, bufferSize)) > -1) {

          int shift = shiftAmount[angle];
          byte[] ster = new byte[bufferSize * 2];

          //shift amount is only for right ear, so if the angle is > 180 this means the right ear
          //is farther away from the sound source, so the shift offset is negative.
          if(angle > 180)
            shift *= -1;

          //static happens when the shift amount is odd, so numLags is divided by two, and then
          // shift gets multiplied by two so the shift amount is always even
          shift *= 2;

          //Log.i(TAG,"The shift: " + shift);


          byte[] concat = new byte[bufferSize*3];

          System.arraycopy(s0, 0, concat, 0, s0.length);
          System.arraycopy(s1, 0, concat, s0.length, s1.length);
          System.arraycopy(s2, 0, concat, s0.length+s1.length, s2.length);

          for (int l = 0; l < bufferSize; l += 2) {
            ster[l * 2 + 0] = concat[bufferSize+l];
            ster[l * 2 + 1] = concat[bufferSize+l+1];
            ster[l * 2 + 2] = concat[bufferSize+l+shift];
            ster[l * 2 + 3] = concat[bufferSize+l+shift+1];
          }

          at.write(ster, 0, bufferSize*2);

          System.arraycopy(s1, 0, s0, 0, s1.length);
          System.arraycopy(s2, 0, s1, 0, s2.length);

        }
        at.stop();
        at.release();
        is.close();
        is.close();

      } catch (FileNotFoundException e) {
        // TODO
        e.printStackTrace();
      } catch (IOException e) {
        // TODO
        e.printStackTrace();
      }
    }
  }

  /**
   * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
   *
   * @param type The type of shader we will be creating.
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The shader object handler.
   */
  private int loadGLShader(int type, int resId) {
    String code = readRawTextFile(resId);
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, code);
    GLES20.glCompileShader(shader);

    // Get the compilation status.
    final int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

    // If the compilation failed, delete the shader.
    if (compileStatus[0] == 0) {
      Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }

    if (shader == 0) {
      throw new RuntimeException("Error creating shader.");
    }

    return shader;
  }

  /**
   * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
   *
   * @param label Label to report in case of error.
   */
  private static void checkGLError(String label) {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, label + ": glError " + error);
      throw new RuntimeException(label + ": glError " + error);
    }
  }

  /**
   * Sets the view to our GvrView and initializes the transformation matrices we will use
   * to render our scene.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    initializeGvrView();

    moving2xCube = new float[16];
    movingCube = new float[16];
    staticCube = new float[16];
    camera = new float[16];
    view = new float[16];
    modelViewProjection = new float[16];
    modelView = new float[16];
    modelFloor = new float[16];
    tempPosition = new float[4];
    // Model first appears directly in front of user.
    moving2xPosition = new float[] {0.0f, 0.0f, -MAX_MODEL_DISTANCE };
    movingPosition = new float[]   {0.0f, 0.0f, -MAX_MODEL_DISTANCE };
    staticPosition = new float[]   {0.0f, 0.0f, MAX_MODEL_DISTANCE };

    headRotation = new float[4];
    forwardVector = new float[3];
    prevForwardVector = new float[3];
    headView = new float[16];
    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

    // Initialize 3D audio engine.
    //gvrAudioEngine = new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.STEREO_PANNING);
  }

  public void initializeGvrView() {
    setContentView(R.layout.common_ui);

    GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
    gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

    gvrView.setRenderer(this);
    gvrView.setTransitionViewEnabled(true);
    gvrView.setOnCardboardBackButtonListener(
        new Runnable() {
          @Override
          public void run() {
            onBackPressed();
          }
        });

    if (gvrView.setAsyncReprojectionEnabled(true)) {
      // Async reprojection decouples the app framerate from the display framerate,
      // allowing immersive interaction even at the throttled clockrates set by
      // sustained performance mode.
      AndroidCompat.setSustainedPerformanceMode(this, true);
    }

    setGvrView(gvrView);
  }

  @Override
  public void onPause() {
    //gvrAudioEngine.pause();
    super.onPause();
  }

  @Override
  public void onResume() {
    super.onResume();
    //gvrAudioEngine.resume();
  }

  @Override
  public void onRendererShutdown() {
    Log.i(TAG, "onRendererShutdown");
  }

  @Override
  public void onSurfaceChanged(int width, int height) {
    Log.i(TAG, "onSurfaceChanged");
  }

  /**
   * Creates the buffers we use to store information about the 3D world.
   *
   * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
   * Hence we use ByteBuffers.
   *
   * @param config The EGL configuration used when creating the surface.
   */
  @Override
  public void onSurfaceCreated(EGLConfig config) {
    Log.i(TAG, "onSurfaceCreated");
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.

    ByteBuffer bbMovingVertices = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COORDS.length * 4);
    bbMovingVertices.order(ByteOrder.nativeOrder());
    moving2xVertices = bbMovingVertices.asFloatBuffer();
    moving2xVertices.put(WorldLayoutData.CUBE_COORDS);
    moving2xVertices.position(0);

    ByteBuffer bbVertices = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COORDS.length * 4);
    bbVertices.order(ByteOrder.nativeOrder());
    cubeVertices = bbVertices.asFloatBuffer();
    cubeVertices.put(WorldLayoutData.CUBE_COORDS);
    cubeVertices.position(0);

    ByteBuffer bbStaticVertices = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COORDS.length * 4);
    bbStaticVertices.order(ByteOrder.nativeOrder());
    staticVertices = bbStaticVertices.asFloatBuffer();
    staticVertices.put(WorldLayoutData.CUBE_COORDS);
    staticVertices.position(0);

    ByteBuffer bbMoving2xColors =
        ByteBuffer.allocateDirect(WorldLayoutData.CUBE_MOVING_2X_COLORS.length * 4);
    bbMoving2xColors.order(ByteOrder.nativeOrder());
    moving2xColors = bbMoving2xColors.asFloatBuffer();
    moving2xColors.put(WorldLayoutData.CUBE_MOVING_2X_COLORS);
    moving2xColors.position(0);

    ByteBuffer bbColors = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COLORS.length * 4);
    bbColors.order(ByteOrder.nativeOrder());
    cubeColors = bbColors.asFloatBuffer();
    cubeColors.put(WorldLayoutData.CUBE_COLORS);
    cubeColors.position(0);

    ByteBuffer bbStaticColors = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COLORS.length * 4);
    bbStaticColors.order(ByteOrder.nativeOrder());
    staticColors = bbStaticColors.asFloatBuffer();
    staticColors.put(WorldLayoutData.STATIC_COLORS);
    staticColors.position(0);

    ByteBuffer bbMoving2xNormals = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_NORMALS.length * 4);
    bbMoving2xNormals.order(ByteOrder.nativeOrder());
    moving2xNormals = bbMoving2xNormals.asFloatBuffer();
    moving2xNormals.put(WorldLayoutData.CUBE_NORMALS);
    moving2xNormals.position(0);

    ByteBuffer bbNormals = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_NORMALS.length * 4);
    bbNormals.order(ByteOrder.nativeOrder());
    cubeNormals = bbNormals.asFloatBuffer();
    cubeNormals.put(WorldLayoutData.CUBE_NORMALS);
    cubeNormals.position(0);

    ByteBuffer bbStaticNormals = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_NORMALS.length * 4);
    bbStaticNormals.order(ByteOrder.nativeOrder());
    staticNormals = bbStaticNormals.asFloatBuffer();
    staticNormals.put(WorldLayoutData.CUBE_NORMALS);
    staticNormals.position(0);

    // make a floor
    ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COORDS.length * 4);
    bbFloorVertices.order(ByteOrder.nativeOrder());
    floorVertices = bbFloorVertices.asFloatBuffer();
    floorVertices.put(WorldLayoutData.FLOOR_COORDS);
    floorVertices.position(0);

    ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_NORMALS.length * 4);
    bbFloorNormals.order(ByteOrder.nativeOrder());
    floorNormals = bbFloorNormals.asFloatBuffer();
    floorNormals.put(WorldLayoutData.FLOOR_NORMALS);
    floorNormals.position(0);

    ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COLORS.length * 4);
    bbFloorColors.order(ByteOrder.nativeOrder());
    floorColors = bbFloorColors.asFloatBuffer();
    floorColors.put(WorldLayoutData.FLOOR_COLORS);
    floorColors.position(0);

    int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
    int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
    int passthroughShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

    moving2xProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(moving2xProgram, vertexShader);
    GLES20.glAttachShader(moving2xProgram, passthroughShader);
    GLES20.glLinkProgram(moving2xProgram);
    GLES20.glUseProgram(moving2xProgram);

    cubeProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(cubeProgram, vertexShader);
    GLES20.glAttachShader(cubeProgram, passthroughShader);
    GLES20.glLinkProgram(cubeProgram);
    GLES20.glUseProgram(cubeProgram);


    staticProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(staticProgram, vertexShader);
    GLES20.glAttachShader(staticProgram, passthroughShader);
    GLES20.glLinkProgram(staticProgram);
    GLES20.glUseProgram(staticProgram);

    checkGLError("Cube program");

    moving2xPositionParam = GLES20.glGetAttribLocation(moving2xProgram, "a_Position");
    moving2xNormalParam = GLES20.glGetAttribLocation(moving2xProgram, "a_Normal");
    moving2xColorParam = GLES20.glGetAttribLocation(moving2xProgram, "a_Color");


    cubePositionParam = GLES20.glGetAttribLocation(cubeProgram, "a_Position");
    cubeNormalParam = GLES20.glGetAttribLocation(cubeProgram, "a_Normal");
    cubeColorParam = GLES20.glGetAttribLocation(cubeProgram, "a_Color");

    staticPositionParam = GLES20.glGetAttribLocation(staticProgram, "a_Position");
    staticNormalParam = GLES20.glGetAttribLocation(staticProgram, "a_Normal");
    staticColorParam = GLES20.glGetAttribLocation(staticProgram, "a_Color");

    moving2xModelParam = GLES20.glGetUniformLocation(moving2xProgram, "u_Model");
    moving2xModelViewParam = GLES20.glGetUniformLocation(moving2xProgram, "u_MVMatrix");
    moving2xModelViewProjectionParam = GLES20.glGetUniformLocation(moving2xProgram, "u_MVP");
    moving2xLightPosParam = GLES20.glGetUniformLocation(moving2xProgram, "u_LightPos");

    cubeModelParam = GLES20.glGetUniformLocation(cubeProgram, "u_Model");
    cubeModelViewParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVMatrix");
    cubeModelViewProjectionParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVP");
    cubeLightPosParam = GLES20.glGetUniformLocation(cubeProgram, "u_LightPos");

    staticModelParam = GLES20.glGetUniformLocation(staticProgram, "u_Model");
    staticModelViewParam = GLES20.glGetUniformLocation(staticProgram, "u_MVMatrix");
    staticModelViewProjectionParam = GLES20.glGetUniformLocation(staticProgram, "u_MVP");
    staticLightPosParam = GLES20.glGetUniformLocation(staticProgram, "u_LightPos");

    checkGLError("Cube program params");

    floorProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(floorProgram, vertexShader);
    GLES20.glAttachShader(floorProgram, gridShader);
    GLES20.glLinkProgram(floorProgram);
    GLES20.glUseProgram(floorProgram);

    checkGLError("Floor program");

    floorModelParam = GLES20.glGetUniformLocation(floorProgram, "u_Model");
    floorModelViewParam = GLES20.glGetUniformLocation(floorProgram, "u_MVMatrix");
    floorModelViewProjectionParam = GLES20.glGetUniformLocation(floorProgram, "u_MVP");
    floorLightPosParam = GLES20.glGetUniformLocation(floorProgram, "u_LightPos");

    floorPositionParam = GLES20.glGetAttribLocation(floorProgram, "a_Position");
    floorNormalParam = GLES20.glGetAttribLocation(floorProgram, "a_Normal");
    floorColorParam = GLES20.glGetAttribLocation(floorProgram, "a_Color");

    checkGLError("Floor program params");

    Matrix.setIdentityM(modelFloor, 0);
    Matrix.translateM(modelFloor, 0, 0, -floorDepth, 0); // Floor appears below user.

    initAudioParams();
    // Avoid any delays during start-up due to decoding of sound files.

    new Thread(
            new Runnable() {
              @Override
              public void run() {
                // Start spatial audio playback of SOUND_FILE at the model postion. The returned
                //soundId handle is stored and allows for repositioning the sound object whenever
                // the cube position changes.
//                gvrAudioEngine.preloadSoundFile(SOUND_FILE);
//                soundId = gvrAudioEngine.createSoundObject(SOUND_FILE);
//                gvrAudioEngine.setSoundObjectPosition(
//                    soundId, moving2xPosition[0], moving2xPosition[1], moving2xPosition[2]);
//                gvrAudioEngine.playSound(soundId, true /* looped playback */);
                try {
                  playWav();
                } catch (Throwable t) {
                  t.printStackTrace();
                }
              }
            })
        .start();

    updateModelPosition();

    checkGLError("onSurfaceCreated");
  }

  /**
   * Updates the cube model position.
   */
  protected void updateModelPosition() {
    Matrix.setIdentityM(moving2xCube, 0);
    Matrix.translateM(moving2xCube, 0, moving2xPosition[0], moving2xPosition[1], moving2xPosition[2]);

    Matrix.setIdentityM(movingCube, 0);
    Matrix.translateM(movingCube, 0, movingPosition[0], movingPosition[1], movingPosition[2]);

    Matrix.setIdentityM(staticCube, 0);
    Matrix.translateM(staticCube, 0, staticPosition[0], staticPosition[1], staticPosition[2]);

//    // Update the sound location to match it with the new cube position.
//    if (soundId != GvrAudioEngine.INVALID_ID) {
//      gvrAudioEngine.setSoundObjectPosition(
//          soundId, moving2xPosition[0], moving2xPosition[1], moving2xPosition[2]);
//    }
    checkGLError("updateCubePosition");
  }

  /**
   * Converts a raw text file into a string.
   *
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The context of the text file, or null in case of error.
   */
  private String readRawTextFile(int resId) {
    InputStream inputStream = getResources().openRawResource(resId);
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      reader.close();
      return sb.toString();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Prepares OpenGL ES before we draw a frame.
   *
   * @param headTransform The head transformation in the new frame.
   */
  @Override
  public void onNewFrame(HeadTransform headTransform) {
    setCubeRotation();

    // Build the camera matrix and apply it to the ModelView.
    Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

    headTransform.getHeadView(headView, 0);

    System.arraycopy(forwardVector, 0, prevForwardVector, 0, 3);
    headTransform.getForwardVector(forwardVector, 0);

    double deltaAngle2X = (Math.atan2(forwardVector[0], forwardVector[2])
            - Math.atan2(prevForwardVector[0], prevForwardVector[2]))*2.0;

    //angle offset
    double deltaAngle = -0.25;


    //System.out.println("[" + forwardVector[2] + ", " + forwardVector[0] + ", " + forwardVector[1]
    //        + "], [" + moving2xPosition[2] + ", " + moving2xPosition[0] + ", " + moving2xPosition[1] + "]");



  double rotX = forwardVector[2]*Math.cos(deltaAngle) - forwardVector[0]*Math.sin(deltaAngle);
  double rotY = forwardVector[2]*Math.sin(deltaAngle) + forwardVector[0]*Math.cos(deltaAngle);

  double rot2XX = moving2xPosition[2]*Math.cos(deltaAngle2X) - moving2xPosition[0]*Math.sin(deltaAngle2X);
  double rot2XY = moving2xPosition[2]*Math.sin(deltaAngle2X) + moving2xPosition[0]*Math.cos(deltaAngle2X);

  movingPosition[2] = (float) rotX*MAX_MODEL_DISTANCE;
  movingPosition[0] = (float) rotY*MAX_MODEL_DISTANCE;

  moving2xPosition[2] = (float) rot2XX;
  moving2xPosition[0] = (float) rot2XY;
  updateModelPosition();

    double objectDeltaAngle = (Math.atan2(forwardVector[0], forwardVector[2])
            - Math.atan2(moving2xPosition[0], moving2xPosition[2])+2*Math.PI)*360/(2*Math.PI);



    angle = (int) objectDeltaAngle%360;

    //Log.i(TAG,"The angle: " + objectDeltaAngle);

    // Update the 3d audio engine with the most recent head rotation.
    headTransform.getQuaternion(headRotation, 0);
    //gvrAudioEngine.setHeadRotation(
    //    headRotation[0], headRotation[1], headRotation[2], headRotation[3]);
    // Regular update call to GVR audio engine.
    //gvrAudioEngine.update();

    checkGLError("onReadyToDraw");
  }

  protected void setCubeRotation() {
    Matrix.rotateM(moving2xCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);
    Matrix.rotateM(movingCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);
    Matrix.rotateM(staticCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);
  }

  /**
   * Draws a frame for an eye.
   *
   * @param eye The eye to render. Includes all required transformations.
   */
  @Override
  public void onDrawEye(Eye eye) {
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    checkGLError("colorParam");

    // Apply the eye transformation to the camera.
    Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

    // Set the position of the light
    Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

    // Build the ModelView and ModelViewProjection matrices
    // for calculating cube position and light.
    float[] moving2xPerspective = eye.getPerspective(Z_NEAR, Z_FAR);
    Matrix.multiplyMM(modelView, 0, view, 0, moving2xCube, 0);
    Matrix.multiplyMM(modelViewProjection, 0, moving2xPerspective, 0, modelView, 0);
    drawMoving2xCube();

    // Build the ModelView and ModelViewProjection matrices
    // for calculating cube position and light.
    float[] movingPerspective = eye.getPerspective(Z_NEAR, Z_FAR);
    Matrix.multiplyMM(modelView, 0, view, 0, movingCube, 0);
    Matrix.multiplyMM(modelViewProjection, 0, movingPerspective, 0, modelView, 0);
    drawMovingCube();

    // Build the ModelView and ModelViewProjection matrices
    // for calculating cube position and light.
    float[] staticPerspective = eye.getPerspective(Z_NEAR, Z_FAR);
    Matrix.multiplyMM(modelView, 0, view, 0, staticCube, 0);
    Matrix.multiplyMM(modelViewProjection, 0, staticPerspective, 0, modelView, 0);
    drawStaticCube();

    // Set modelView for the floor, so we draw floor in the correct location
    Matrix.multiplyMM(modelView, 0, view, 0, modelFloor, 0);
    Matrix.multiplyMM(modelViewProjection, 0, staticPerspective, 0, modelView, 0);
    drawFloor();
  }

  @Override
  public void onFinishFrame(Viewport viewport) {}

  /**
   * Draw the cube.
   *
   * <p>We've set all of our transformation matrices. Now we simply pass them into the shader.
   */
  public void drawMoving2xCube() {
    GLES20.glUseProgram(moving2xProgram);

    GLES20.glUniform3fv(moving2xLightPosParam, 1, lightPosInEyeSpace, 0);

    // Set the Model in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(moving2xModelParam, 1, false, moving2xCube, 0);

    // Set the ModelView in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(moving2xModelViewParam, 1, false, modelView, 0);

    // Set the position of the cube
    GLES20.glVertexAttribPointer(
            moving2xPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, moving2xVertices);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(moving2xModelViewProjectionParam, 1, false, modelViewProjection, 0);

    // Set the normal positions of the cube, again for shading
    GLES20.glVertexAttribPointer(moving2xNormalParam, 3, GLES20.GL_FLOAT, false, 0, moving2xNormals);
    GLES20.glVertexAttribPointer(moving2xColorParam, 4, GLES20.GL_FLOAT, false, 0, moving2xColors);

    // Enable vertex arrays
    GLES20.glEnableVertexAttribArray(moving2xPositionParam);
    GLES20.glEnableVertexAttribArray(moving2xNormalParam);
    GLES20.glEnableVertexAttribArray(moving2xColorParam);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    checkGLError("Drawing cube");
  }

  public void drawMovingCube() {
    GLES20.glUseProgram(cubeProgram);

    GLES20.glUniform3fv(cubeLightPosParam, 1, lightPosInEyeSpace, 0);

    // Set the Model in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(cubeModelParam, 1, false, movingCube, 0);

    // Set the ModelView in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(cubeModelViewParam, 1, false, modelView, 0);

    // Set the position of the cube
    GLES20.glVertexAttribPointer(
            cubePositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, cubeVertices);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(cubeModelViewProjectionParam, 1, false, modelViewProjection, 0);

    // Set the normal positions of the cube, again for shading
    GLES20.glVertexAttribPointer(cubeNormalParam, 3, GLES20.GL_FLOAT, false, 0, cubeNormals);
    GLES20.glVertexAttribPointer(cubeColorParam, 4, GLES20.GL_FLOAT, false, 0, cubeColors);

    // Enable vertex arrays
    GLES20.glEnableVertexAttribArray(cubePositionParam);
    GLES20.glEnableVertexAttribArray(cubeNormalParam);
    GLES20.glEnableVertexAttribArray(cubeColorParam);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    checkGLError("Drawing cube");
  }

  public void drawStaticCube() {
    GLES20.glUseProgram(staticProgram);

    GLES20.glUniform3fv(staticLightPosParam, 1, lightPosInEyeSpace, 0);

    // Set the Model in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(staticModelParam, 1, false, staticCube, 0);

    // Set the ModelView in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(staticModelViewParam, 1, false, modelView, 0);

    // Set the position of the cube
    GLES20.glVertexAttribPointer(
            staticPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, staticVertices);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(staticModelViewProjectionParam, 1, false, modelViewProjection, 0);

    // Set the normal positions of the cube, again for shading
    GLES20.glVertexAttribPointer(staticNormalParam, 3, GLES20.GL_FLOAT, false, 0, staticNormals);
    GLES20.glVertexAttribPointer(staticColorParam, 4, GLES20.GL_FLOAT, false, 0, staticColors);

    // Enable vertex arrays
    GLES20.glEnableVertexAttribArray(staticPositionParam);
    GLES20.glEnableVertexAttribArray(staticNormalParam);
    GLES20.glEnableVertexAttribArray(staticColorParam);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    checkGLError("Drawing cube");
  }

  /**
   * Draw the floor.
   *
   * <p>This feeds in data for the floor into the shader. Note that this doesn't feed in data about
   * position of the light, so if we rewrite our code to draw the floor first, the lighting might
   * look strange.
   */
  public void drawFloor() {
    GLES20.glUseProgram(floorProgram);

    // Set ModelView, MVP, position, normals, and color.
    GLES20.glUniform3fv(floorLightPosParam, 1, lightPosInEyeSpace, 0);
    GLES20.glUniformMatrix4fv(floorModelParam, 1, false, modelFloor, 0);
    GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, modelView, 0);
    GLES20.glUniformMatrix4fv(floorModelViewProjectionParam, 1, false, modelViewProjection, 0);
    GLES20.glVertexAttribPointer(
        floorPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, floorVertices);
    GLES20.glVertexAttribPointer(floorNormalParam, 3, GLES20.GL_FLOAT, false, 0, floorNormals);
    GLES20.glVertexAttribPointer(floorColorParam, 4, GLES20.GL_FLOAT, false, 0, floorColors);

    GLES20.glEnableVertexAttribArray(floorPositionParam);
    GLES20.glEnableVertexAttribArray(floorNormalParam);
    GLES20.glEnableVertexAttribArray(floorColorParam);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 24);

    checkGLError("drawing floor");
  }

  /**
   * Called when the Cardboard trigger is pulled.
   */
  @Override
  public void onCardboardTrigger() {
    Log.i(TAG, "onCardboardTrigger");

    if (isLookingAtObject()) {
      hideObject();
    }

    rotationMode = 1 - rotationMode;

    // Always give user feedback.
    vibrator.vibrate(50);
  }

  /**
   * Find a new random position for the object.
   *
   * <p>We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little bit.
   */
  protected void hideObject() {
    float[] rotationMatrix = new float[16];
    float[] posVec = new float[4];

    // First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
    // the object's distance from the user.
    float angleXZ = (float) Math.random() * 180 + 90;
    Matrix.setRotateM(rotationMatrix, 0, angleXZ, 0f, 1f, 0f);
    float oldObjectDistance = objectDistance;
    objectDistance =
        (float) Math.random() * (MAX_MODEL_DISTANCE - MIN_MODEL_DISTANCE) + MIN_MODEL_DISTANCE;
    float objectScalingFactor = objectDistance / oldObjectDistance;
    Matrix.scaleM(rotationMatrix, 0, objectScalingFactor, objectScalingFactor, objectScalingFactor);
    Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, moving2xCube, 12);

    float angleY = (float) Math.random() * 80 - 40; // Angle in Y plane, between -40 and 40.
    angleY = (float) Math.toRadians(angleY);
    float newY = (float) Math.tan(angleY) * objectDistance;

    moving2xPosition[0] = posVec[0];
    moving2xPosition[1] = newY;
    moving2xPosition[2] = posVec[2];

    updateModelPosition();
  }

  /**
   * Check if user is looking at object by calculating where the object is in eye-space.
   *
   * @return true if the user is looking at the object.
   */
  private boolean isLookingAtObject() {
    // Convert object space to camera space. Use the headView from onNewFrame.
    Matrix.multiplyMM(modelView, 0, headView, 0, moving2xCube, 0);
    Matrix.multiplyMV(tempPosition, 0, modelView, 0, POS_MATRIX_MULTIPLY_VEC, 0);

    float pitch = (float) Math.atan2(tempPosition[1], -tempPosition[2]);
    float yaw = (float) Math.atan2(tempPosition[0], -tempPosition[2]);

    return Math.abs(pitch) < PITCH_LIMIT && Math.abs(yaw) < YAW_LIMIT;
  }
}
