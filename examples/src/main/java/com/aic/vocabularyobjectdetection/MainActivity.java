package com.aic.vocabularyobjectdetection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.aic.vocabularydetectionview.DetectorActivity;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
  }

  public void openDetector(View view) {
    startActivity(new Intent(this, DetectorActivity.class));
  }
}