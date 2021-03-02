package com.aic.vocabularydetectionview;

import android.graphics.Bitmap;

public class ResultItem {

  private String selected;
  private String result;
  private Bitmap photoData;

  public ResultItem(String selected, String result, Bitmap photoData) {
    this.selected = selected;
    this.result = result;
    this.photoData = photoData;
  }

  public String getSelected() {
    return selected;
  }

  public void setSelected(String selected) {
    this.selected = selected;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public Bitmap getPhotoData() {
    return photoData;
  }

  public void setPhotoData(Bitmap photoData) {
    this.photoData = photoData;
  }
}
