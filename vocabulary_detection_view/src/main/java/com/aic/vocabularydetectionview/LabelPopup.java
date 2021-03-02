package com.aic.vocabularydetectionview;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;

import com.aic.vocabularydetectionview.ml.LiteModelObjectDetectionMobileObjectLabelerV11;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class LabelPopup extends PopupWindow {

  private final Context context;
  private ListView listView;

  public LabelPopup(Context context) {
    super(context);
    this.context = context;
    setupView();
  }

  private void setupView() {
    View view = LayoutInflater.from(context).inflate(R.layout.popup_label, null);
    LiteModelObjectDetectionMobileObjectLabelerV11 labeler = ((DetectorActivity) context).labeler;

    listView = view.findViewById(R.id.labels);

    ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
            android.R.layout.simple_list_item_1, android.R.id.text1, DetectorActivity.values);

    // Assign adapter to ListView
    listView.setAdapter(adapter);
    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String itemValue = (String) listView.getItemAtPosition(position);

        AsyncTask.execute(new Runnable() {
          @Override
          public void run() {
            if (bitmap != null) {
              StringBuilder result = new StringBuilder();
              try {
                TensorImage image = TensorImage.fromBitmap(Bitmap.createScaledBitmap(bitmap, 224, 224, false));
                LiteModelObjectDetectionMobileObjectLabelerV11.Outputs outputs = labeler.process(image);
                List<Category> probability = outputs.getProbabilityAsCategoryList();
                Collections.sort(probability, (o1, o2) -> -Double.compare(o1.getScore(), o2.getScore()));

                for (Category category : probability.subList(0, 3)) {
                  result.append(category.getLabel()).append(" ").append(category.getScore()).append(" | ");
                }

              } catch (Exception e) {
                e.printStackTrace();
              }

              DetectorActivity.selected.add(new ResultItem(itemValue, result.toString(), Bitmap.createBitmap(bitmap)));
              DetectorActivity.values.remove(itemValue);

              ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  dismiss();
                  ((DetectorActivity) context).updateResults();
                }
              });
            }
            // More code here
          }
        });
      }
    });
    setContentView(view);
  }

  private Bitmap loadImage(String fileName) throws Exception {
    AssetManager assetManager = context.getAssets();
    InputStream inputStream = assetManager.open(fileName);
    return BitmapFactory.decodeStream(inputStream);
  }

  private Bitmap bitmap;

  public void setCurrentBitmap(Bitmap croppedBitmap) {
    this.bitmap = croppedBitmap;
  }

}
