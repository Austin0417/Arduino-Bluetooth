package com.example.myapp2;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class InformationDisplayActivity extends AppCompatActivity {
    TextView statusText;
    TextView lastDetected;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.information_display_activity);
    statusText = findViewById(R.id.statusText);
    lastDetected = findViewById(R.id.lastDetectedText);
}
}
