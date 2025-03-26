package com.example.projet_qr_code;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button buttonEncode;
    private Button buttonDecode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonEncode = findViewById(R.id.buttonEncode);
        buttonDecode = findViewById(R.id.buttonDecode);

        buttonEncode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, EncodeActivity.class);
                startActivity(intent);
            }
        });

        buttonDecode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DecodeActivity.class);
                startActivity(intent);
            }
        });
    }
}