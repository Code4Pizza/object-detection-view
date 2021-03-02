package com.aic.vocabularydetectionview;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class CustomListViewAdapter extends ArrayAdapter<ResultItem> {

  Context context;

  public CustomListViewAdapter(Context context, int resourceId, //resourceId=your layout
                               List<ResultItem> items) {
    super(context, resourceId, items);
    this.context = context;
  }

  /*private view holder class*/
  private class ViewHolder {
    ImageView imageView;
    TextView txtTitle;
  }

  public View getView(int position, View convertView, ViewGroup parent) {
    ViewHolder holder = null;
    ResultItem rowItem = getItem(position);

    LayoutInflater mInflater = (LayoutInflater) context
            .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
    if (convertView == null) {
      convertView = mInflater.inflate(android.R.layout.activity_list_item, null);
      holder = new ViewHolder();
      holder.txtTitle = (TextView) convertView.findViewById(android.R.id.text1);
      holder.txtTitle.setGravity(Gravity.CENTER_VERTICAL);
      holder.imageView = (ImageView) convertView.findViewById(android.R.id.icon);
      LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(300, 300);
      holder.imageView.setLayoutParams(layoutParams);
      convertView.setTag(holder);
    } else
      holder = (ViewHolder) convertView.getTag();

    holder.txtTitle.setText(rowItem.getSelected() + " - " + rowItem.getResult());

    if (rowItem.getPhotoData() != null) {
      holder.imageView.setImageBitmap(rowItem.getPhotoData());
    }

    return convertView;
  }
}