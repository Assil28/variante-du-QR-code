package com.example.projet_qr_code;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class EncodeActivity extends AppCompatActivity {

    private EditText editTextBinary;
    private Spinner spinnerGridSizeEncode;
    private Button buttonGenerate;
    private ImageView imageViewCode;

    private int blockSize = 100; // Define block size (adjust as needed)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encode);

        editTextBinary = findViewById(R.id.editTextBinary);
        spinnerGridSizeEncode = findViewById(R.id.spinnerGridSizeEncode);
        buttonGenerate = findViewById(R.id.buttonGenerate);
        imageViewCode = findViewById(R.id.imageViewCode);

        // Setup spinner with grid sizes from res/values/strings.xml
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.grid_sizes,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGridSizeEncode.setAdapter(adapter);

        buttonGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String binaryInput = editTextBinary.getText().toString().trim();
                int gridSize = Integer.parseInt(spinnerGridSizeEncode.getSelectedItem().toString());
                int requiredLength = (gridSize - 2) * (gridSize - 2);

                if (binaryInput.length() != requiredLength) {
                    Toast.makeText(
                            EncodeActivity.this,
                            "Binary input must be exactly " + requiredLength + " bits.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                Bitmap generatedBitmap = generateQRCodeVariant(binaryInput, gridSize);
                imageViewCode.setImageBitmap(generatedBitmap);

                // Save to gallery
                String fileName = "QRCodeVariant_" + System.currentTimeMillis();
                saveBitmapToGallery(generatedBitmap, fileName);

            }
        });
    }

    // Generates the QR code variant bitmap with visible grid lines from the binary string and grid size
    private Bitmap generateQRCodeVariant(String binary, int gridSize) {
        int width = gridSize * blockSize;
        int height = gridSize * blockSize;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        // 1) Fill the background with white
        canvas.drawColor(Color.WHITE);

        // 2) Draw the alternating border (first row and first column)
        for (int col = 0; col < gridSize; col++) {
            paint.setColor((col % 2 == 0) ? Color.BLACK : Color.WHITE);
            canvas.drawRect(col * blockSize, 0, (col + 1) * blockSize, blockSize, paint);
        }
        for (int row = 0; row < gridSize; row++) {
            paint.setColor((row % 2 == 0) ? Color.BLACK : Color.WHITE);
            canvas.drawRect(0, row * blockSize, blockSize, (row + 1) * blockSize, paint);
        }

        // 3) Fill the central region with the binary data
        int index = 0;
        for (int row = 1; row < gridSize - 1; row++) {
            for (int col = 1; col < gridSize - 1; col++) {
                if (index < binary.length()) {
                    char bit = binary.charAt(index);
                    paint.setColor(bit == '1' ? Color.BLACK : Color.WHITE);
                    canvas.drawRect(col * blockSize, row * blockSize,
                            (col + 1) * blockSize, (row + 1) * blockSize, paint);
                    index++;
                }
            }
        }

        // 4) Draw grid lines for clear block boundaries
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        for (int i = 0; i <= gridSize; i++) {
            int y = i * blockSize;
            canvas.drawLine(0, y, width, y, paint);
        }
        for (int i = 0; i <= gridSize; i++) {
            int x = i * blockSize;
            canvas.drawLine(x, 0, x, height, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        return bitmap;
    }

    private void saveBitmapToGallery(Bitmap bitmap, String fileName) {
        OutputStream fos;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QRCodeVariants");

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    fos = getContentResolver().openOutputStream(uri);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    if (fos != null) fos.close();
                    Toast.makeText(this, "Saved to gallery!", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Legacy method for API < 29
                String imagesDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                ).toString() + "/QRCodeVariants";
                File file = new File(imagesDir);
                if (!file.exists()) file.mkdirs();

                File image = new File(file, fileName + ".png");
                fos = new FileOutputStream(image);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                fos.close();

                // Make visible in gallery
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(image);
                mediaScanIntent.setData(contentUri);
                this.sendBroadcast(mediaScanIntent);

                Toast.makeText(this, "Saved to gallery!", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save image.", Toast.LENGTH_SHORT).show();
        }
    }

}