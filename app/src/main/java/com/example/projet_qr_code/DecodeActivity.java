package com.example.projet_qr_code;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;

public class DecodeActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int GALLERY_REQUEST_CODE = 200;

    private Spinner spinnerGridSizeDecode;
    private Button buttonCapture, buttonSelectFromGallery;
    private ImageView imageViewCaptured;
    private TextView textViewDecoded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decode);

        spinnerGridSizeDecode = findViewById(R.id.spinnerGridSizeDecode);
        buttonCapture = findViewById(R.id.buttonCapture);
        buttonSelectFromGallery = findViewById(R.id.buttonSelectFromGallery);
        imageViewCaptured = findViewById(R.id.imageViewCaptured);
        textViewDecoded = findViewById(R.id.textViewDecoded);

        // Setup spinner with grid sizes from res/values/strings.xml
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.grid_sizes,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGridSizeDecode.setAdapter(adapter);

        buttonCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(
                        DecodeActivity.this,
                        Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            DecodeActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            CAMERA_PERMISSION_CODE
                    );
                } else {
                    openCamera();
                }
            }
        });

        buttonSelectFromGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGallery();
            }
        });
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to capture image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            Bitmap capturedImage = (Bitmap) extras.get("data");
            if (capturedImage != null) {
                imageViewCaptured.setImageBitmap(capturedImage);

                int gridSize = Integer.parseInt(spinnerGridSizeDecode.getSelectedItem().toString());
                String decodedBinary = decodeQRCodeVariant(capturedImage, gridSize);
                textViewDecoded.setText("Decoded Binary: " + decodedBinary);
            }
        } else if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
                    Bitmap selectedImage = handleSamplingAndRotationBitmap(selectedImageUri);
                    imageViewCaptured.setImageBitmap(selectedImage);

                    int gridSize = Integer.parseInt(spinnerGridSizeDecode.getSelectedItem().toString());
                    String decodedBinary = decodeQRCodeVariant(selectedImage, gridSize);
                    textViewDecoded.setText("Decoded Binary: " + decodedBinary);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public Bitmap handleSamplingAndRotationBitmap(Uri selectedImage) throws IOException {
        int MAX_HEIGHT = 1024;
        int MAX_WIDTH = 1024;

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream imageStream = getContentResolver().openInputStream(selectedImage);
        BitmapFactory.decodeStream(imageStream, null, options);
        imageStream.close();

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        imageStream = getContentResolver().openInputStream(selectedImage);
        Bitmap img = BitmapFactory.decodeStream(imageStream, null, options);
        imageStream.close();

        // Rotate the image if needed
        return rotateImageIfRequired(img, selectedImage);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).
            final float totalPixels = width * height;

            // Anything more than 2x the requested pixels we'll sample down further
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    private Bitmap rotateImageIfRequired(Bitmap img, Uri selectedImage) throws IOException {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(selectedImage, projection, null, null, null);

        if (cursor == null) {
            ExifInterface ei = new ExifInterface(getContentResolver().openInputStream(selectedImage));
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            return rotateImage(img, getRotationFromOrientation(orientation));
        } else {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();

            ExifInterface ei = new ExifInterface(path);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            return rotateImage(img, getRotationFromOrientation(orientation));
        }
    }

    private static int getRotationFromOrientation(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        if (degree == 0) return img;

        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    // Decodes the QR code variant by reading the central region of the bitmap using the provided grid size
    private String decodeQRCodeVariant(Bitmap bitmap, int gridSize) {
        if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return "No image to decode.";
        }

        int blockWidth = bitmap.getWidth() / gridSize;
        int blockHeight = bitmap.getHeight() / gridSize;
        StringBuilder binaryResult = new StringBuilder();

        // Process only the central area (excluding the border row/column)
        for (int row = 1; row < gridSize - 1; row++) {
            for (int col = 1; col < gridSize - 1; col++) {
                int startX = col * blockWidth;
                int startY = row * blockHeight;
                binaryResult.append(getBlockValue(bitmap, startX, startY, blockWidth, blockHeight));
            }
        }
        return binaryResult.toString();
    }

    private char getBlockValue(Bitmap bitmap, int startX, int startY, int blockWidth, int blockHeight) {
        int blackCount = 0;
        int whiteCount = 0;

        int innerPadding = (int) (Math.min(blockWidth, blockHeight) * 0.2);
        int sampleStartX = startX + innerPadding;
        int sampleStartY = startY + innerPadding;
        int sampleEndX = startX + blockWidth - innerPadding;
        int sampleEndY = startY + blockHeight - innerPadding;

        int samplePoints = 0;

        for (int y = sampleStartY; y < sampleEndY; y += 5) {
            for (int x = sampleStartX; x < sampleEndX; x += 5) {
                if (x >= 0 && x < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()) {
                    int pixel = bitmap.getPixel(x, y);
                    int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                    if (gray < 128) {
                        blackCount++;
                    } else {
                        whiteCount++;
                    }
                    samplePoints++;
                }
            }
        }
        if (samplePoints == 0) {
            return '0';
        }
        return (blackCount > whiteCount) ? '1' : '0';
    }
}