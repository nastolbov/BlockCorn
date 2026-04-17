package com.blockcorn.android;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** First-run screen for setting up the accountability PIN. */
public class PinActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        EditText pinField    = findViewById(R.id.pinField);
        EditText confirmField = findViewById(R.id.confirmPinField);
        Button   saveBtn     = findViewById(R.id.savePinBtn);

        saveBtn.setOnClickListener(v -> {
            String pin     = pinField.getText().toString().trim();
            String confirm = confirmField.getText().toString().trim();

            if (pin.length() < 4) {
                Toast.makeText(this, "PIN должен быть не менее 4 символов", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pin.equals(confirm)) {
                Toast.makeText(this, "PIN и подтверждение не совпадают", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                PinManager.setPin(this, pin);
                Toast.makeText(this, "PIN установлен", Toast.LENGTH_SHORT).show();
                finish();
            } catch (Exception e) {
                Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
