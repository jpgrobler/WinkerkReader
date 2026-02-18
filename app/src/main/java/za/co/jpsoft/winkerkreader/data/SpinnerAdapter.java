package za.co.jpsoft.winkerkreader.data;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;


import za.co.jpsoft.winkerkreader.R;

/**
 * Created by Pieter Grobler on 04/09/2017.
 */

public class SpinnerAdapter extends BaseAdapter {
        final Context context;
        final int[] images;
        final String[] textNames;
        final LayoutInflater inflter;

        public SpinnerAdapter(Context applicationContext, int[] images, String[] textNames) {
            this.context = applicationContext;
            this.images = images;
            this.textNames = textNames;
            inflter = (LayoutInflater.from(applicationContext));
        }

        @Override
        public int getCount() {
            int result;
            if (images != null){
                result = images.length;}
            else {
                result = -1;
                }
            if (result == -1) {
                if (textNames != null) {
                    result = textNames.length;
                }
            }
            return result;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        static class ViewHolder {
            private ImageView icon;
            private TextView names;
        }
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder mviewholder = null;


                mviewholder = new ViewHolder();
                LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = vi.inflate(R.layout.custom_spinner_layout, null);
                mviewholder.icon = view.findViewById(R.id.imageView);
                mviewholder.names = view.findViewById(R.id.textView);


            if (images != null){
                mviewholder.icon.setVisibility(View.VISIBLE);
                mviewholder.icon.setImageResource(images[i]);}
            else {
                mviewholder.icon.setVisibility(View.GONE);}
            if (textNames != null) {
                mviewholder.names.setVisibility(View.VISIBLE);
                mviewholder.names.setText(textNames[i]);}
            else {
                mviewholder.names.setVisibility(View.GONE);
            }
            return view;
        }
    }