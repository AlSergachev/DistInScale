package com.example.distinscale.domain;


import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.distinscale.R;

import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.Mat;

import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

    private final LayoutInflater inflater;
    private final List<Mat> matList;

    public ImageAdapter(Context context, List<Mat> matList) {
        this.matList = matList;
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public ImageAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = inflater.inflate(R.layout.list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ImageAdapter.ViewHolder holder, int position) {
        Mat mat = matList.get(position);
        holder.resultImg.setImageBitmap(convertMatToBitmap(mat));
    }

    @Override
    public int getItemCount() {
        return matList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView resultImg;

        ViewHolder(View view) {
            super(view);
            resultImg = view.findViewById(R.id.resultImg);
        }
    }

    // Конвертирует Mat в Bitmap
    private static Bitmap convertMatToBitmap(Mat inputM) {
        Bitmap bmp = null;
        Mat rgb = inputM.clone();
        try {
            bmp = Bitmap.createBitmap(rgb.cols(), rgb.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rgb, bmp);
        } catch (CvException e) {
            Log.d("Exception", e.getMessage());
        }
        return bmp;
    }
}
