package com.example.oraldiseases;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");
    private OkHttpClient client = new OkHttpClient();
    private Uri selectedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request permission to access photos
        requestPhotosAccess();

        // Find the ImageView and set the placeholder image
        ImageView imageView = findViewById(R.id.imageView);
        imageView.setImageResource(R.drawable._356649); // Set the placeholder image here


        // Set up button to open image gallery
        Button selectImageButton = findViewById(R.id.button);
        selectImageButton.setOnClickListener(v -> openGallery());

        // Set up button to check the prediction
        Button checkButton = findViewById(R.id.button3);
        checkButton.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                try {
                    uploadImageToServer();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Error uploading image", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "No image selected", Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void requestPhotosAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 300);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 200);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if ((requestCode == 300 || requestCode == 200) && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        } else {
            Toast.makeText(this, "Permission denied to access photos", Toast.LENGTH_SHORT).show();
        }
    }

    ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    ImageView imageView = findViewById(R.id.imageView);
                    imageView.setImageURI(selectedImageUri);
                } else {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }
    private byte[] readBytesFromInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024]; // buffer size

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }


    private void uploadImageToServer() throws IOException {
        if (selectedImageUri != null) {
            // Open InputStream from the URI
            try (InputStream inputStream = getContentResolver().openInputStream(selectedImageUri)) {

                // Read bytes from the InputStream using the helper method
                byte[] imageBytes = readBytesFromInputStream(inputStream);

                // Create the request body for the image data
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "image.jpg", RequestBody.create(imageBytes, MEDIA_TYPE_JPEG))
                        .build();

                // Build the request
                Request request = new Request.Builder()
                        .url("https://oraldiseases-20d5973e00ec.herokuapp.com/predict")
                        .post(requestBody)
                        .build();

                // Execute the request
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            System.out.println("Raw response: " + responseBody); // Log raw response
                            runOnUiThread(() -> handleApiResponse(responseBody));
                        } else {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error in API response", Toast.LENGTH_SHORT).show());
                        }
                    }

                });
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Error reading image data", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
        }
    }


    // Get file path from URI
    private String getPathFromUri(Uri uri) {
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        try (Cursor cursor = getContentResolver().query(uri, filePathColumn, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                return cursor.getString(columnIndex);
            }
        }
        return null;
    }

    private String getSuggestedSteps(String disease) {
        switch (disease) {
            case "Dental Caries":
                return "Visit a dentist for a check-up and consider a fluoride treatment.";
            case "Gingivitis":
                return "Improve oral hygiene by brushing and flossing daily; consider using an antibacterial mouthwash.";
            case "Tooth Discoloration":
                return "Reduce consumption of staining foods and drinks, and consult your dentist about whitening options.";
            case "Mouth Ulcer":
                return "Avoid spicy foods, maintain oral hygiene, and consult a doctor if it persists for more than two weeks.";
            case "Calculus":
                return "Schedule a professional cleaning with your dentist.";
            case "Hypodontia":
                return "Consult an orthodontist for potential treatment options.";
            default:
                return "No specific advice available. Consult your dentist for further guidance.";
        }
    }


    // Handle API response and show popup
    private void handleApiResponse(String responseBody) {
        // Parse JSON response
        Gson gson = new Gson();
        ApiResponse apiResponse = gson.fromJson(responseBody, ApiResponse.class);

        String diseaseName = apiResponse.getPredictionClass();
        String suggestion = getSuggestedSteps(diseaseName);

        // Build a formatted message with bold and centered text
        SpannableStringBuilder message = new SpannableStringBuilder();

        // "Prediction Result" - Larger and bold
        String predictionTitle = "Prediction Result\n\n";
        message.append(predictionTitle);
        message.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, predictionTitle.length(), 0);
        message.setSpan(new android.text.style.RelativeSizeSpan(1.5f), 0, predictionTitle.length(), 0);
        message.setSpan(new android.text.style.AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, predictionTitle.length(), 0);

        // Disease name - Bold and slightly smaller than "Prediction Result"
        int start = message.length();
        message.append(diseaseName + "\n\n");
        message.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, message.length(), 0);
        message.setSpan(new android.text.style.RelativeSizeSpan(1.2f), start, message.length(), 0);
        message.setSpan(new android.text.style.AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), start, message.length(), 0);

        // "Steps to take:" label and suggestion text
        message.append("Steps to take:\n\n");
        start = message.length();
        message.append(suggestion);
        message.setSpan(new android.text.style.AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), start, message.length(), 0);

        // Show the prediction and suggested steps in a pop-up
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(null) // Set to null to avoid title's default formatting
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }




    // Inner class for API response
    private class ApiResponse {
        @SerializedName("prediction class")
        private String predictionClass;

        public String getPredictionClass() {
            return predictionClass;
        }

        @Override
        public String toString() {
            return "ApiResponse{" +
                    predictionClass + '\'' +
                    '}';
        }
    }
}
