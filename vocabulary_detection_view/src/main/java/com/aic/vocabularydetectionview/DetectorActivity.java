/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aic.vocabularydetectionview;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.aic.vocabularydetectionview.customview.OverlayView;
import com.aic.vocabularydetectionview.env.BorderedText;
import com.aic.vocabularydetectionview.env.ImageUtils;
import com.aic.vocabularydetectionview.env.Logger;
import com.aic.vocabularydetectionview.ml.LiteModelObjectDetectionMobileObjectLabelerV11;
import com.aic.vocabularydetectionview.tflite.Detector;
import com.aic.vocabularydetectionview.tflite.TFLiteObjectDetectionAPIModel;
import com.aic.vocabularydetectionview.tracking.MultiBoxTracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener, CameraActivity.OnCameraTouchListener {
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged SSD model.
  private static final int TF_OD_API_INPUT_SIZE = 192;
  private static final String TF_OD_API_MODEL_FILE = "lite-model_object_detection_mobile_object_localizer_v1_1_metadata_2.tflite";
  private static final boolean TF_OD_API_IS_QUANTIZED = true;
  private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;
  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
  private static final boolean MAINTAIN_ASPECT = false;
  private static final Size DESIRED_PREVIEW_SIZE = new Size(1280, 960);
  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;
  private Integer sensorOrientation;

  private Detector detector;
  public LiteModelObjectDetectionMobileObjectLabelerV11 labeler;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private BorderedText borderedText;

  public static List<ResultItem> selected = new ArrayList<>();

  public static List<String> values = new ArrayList<>(Arrays.asList("pen", "phone", "cup", "bottle", "keyboard", "mouse"));

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    selected = new ArrayList<>();
  }

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;

    try {
      detector =
              TFLiteObjectDetectionAPIModel.create(
                      this,
                      TF_OD_API_MODEL_FILE,
                      TF_OD_API_LABELS_FILE,
                      TF_OD_API_INPUT_SIZE,
                      TF_OD_API_IS_QUANTIZED);
      labeler = LiteModelObjectDetectionMobileObjectLabelerV11.newInstance(this);
      cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      e.printStackTrace();
      LOGGER.e(e, "Exception initializing Detector!");
      Toast toast =
              Toast.makeText(
                      getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
            ImageUtils.getTransformationMatrix(
                    previewWidth, previewHeight,
                    cropSize, cropSize,
                    sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
            canvas -> {
              tracker.draw(canvas);
              if (isDebug()) {
                tracker.drawDebug(canvas);
              }
            });

    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
  }

  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveFrame(rgbFrameBitmap);
      ImageUtils.saveBitmap(croppedBitmap);
    }

    runInBackground(
            new Runnable() {
              @Override
              public void run() {
                LOGGER.i("Running detection on image " + currTimestamp);
                final long startTime = SystemClock.uptimeMillis();
                final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                final Canvas canvas = new Canvas(cropCopyBitmap);
                final Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Style.STROKE);
                paint.setStrokeWidth(2.0f);

                float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                switch (MODE) {
                  case TF_OD_API:
                    minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                    break;
                }

                final List<Detector.Recognition> mappedRecognitions = new ArrayList<Detector.Recognition>();

                for (final Detector.Recognition result : results) {
                  final RectF location = result.getLocation();
                  if (location != null && result.getConfidence() >= minimumConfidence) {
                    canvas.drawRect(location, paint);

                    cropToFrameTransform.mapRect(location);

                    try {
                      Matrix matrix = new Matrix();
                      matrix.postRotate(90);
                      Bitmap croppedBmp = Bitmap.createBitmap(rgbFrameBitmap, (int) location.left, (int) location.top, (int) location.width(), (int) location.height(), matrix, true);
                      Log.d("=======>", "run: " + croppedBmp.getWidth() + " - " + croppedBmp.getHeight());
                      result.setCroppedBitmap(croppedBmp);
                      result.setLocation(location);
                      mappedRecognitions.add(result);
                    } catch (IllegalArgumentException e) {
                      e.printStackTrace();
                    }
                  }
                }

                tracker.trackResults(mappedRecognitions, currTimestamp);
                trackingOverlay.postInvalidate();

                computingDetection = false;
              }
            });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.tfe_od_camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onTouched(int x, int y) {
    if (tracker != null) {
      MultiBoxTracker.TrackedRecognition trackedRecognition = tracker.locateFrameClicked(x, y);
      if (trackedRecognition != null) {
        showPopup(x, y, trackedRecognition.getCroppedBitmap());
      }
    }
  }

  private void showPopup(int x, int y, Bitmap croppedBitmap) {
    final LabelPopup menu = new LabelPopup(this);
    menu.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
    menu.setWidth(getPxFromDp(200));
    menu.setOutsideTouchable(true);
    menu.setFocusable(true);
    menu.setCurrentBitmap(croppedBitmap);
    menu.showAtLocation(trackingOverlay, Gravity.NO_GRAVITY, x, y);
  }

  //Convert DP to Pixel
  private int getPxFromDp(int dp) {
    return (int) (dp * getResources().getDisplayMetrics().density);
  }

  @SuppressLint("NewApi")
  public void updateResults() {
    TextView selectedLabel = findViewById(R.id.selected);
    Button reset = findViewById(R.id.btn_reset);
    Button show = findViewById(R.id.btn_show);

    List<String> selectedString = new ArrayList<>();
    for (int i = 0; i < selected.size(); i++) {
      selectedString.add(selected.get(i).getSelected());
    }

    selectedLabel.setText("Từ đã chọn : [" + String.join(", ", selectedString) + "]");
    reset.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        List<String> selectedString = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
          selectedString.add(selected.get(i).getSelected());
        }
        //        StartActivity.values.addAll(selectedString);
        selected.clear();
        selectedLabel.setText("Từ đã chọn: []");
      }
    });

    show.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(DetectorActivity.this);
        builderSingle.setTitle("Từ chọn - Kết quả API");
        builderSingle.setNegativeButton("Thoát", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
          }
        });
        builderSingle.setAdapter(new CustomListViewAdapter(DetectorActivity.this, android.R.layout.activity_list_item, selected), null);
        builderSingle.show();
      }
    });
  }

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.
  private enum DetectorMode {
    TF_OD_API;
  }
}
