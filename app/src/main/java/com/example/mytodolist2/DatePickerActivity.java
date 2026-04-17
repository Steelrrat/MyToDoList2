package com.example.mytodolist2;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.DatePicker;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;

public class DatePickerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        int year = getIntent().getIntExtra("year", Calendar.getInstance().get(Calendar.YEAR));
        int month = getIntent().getIntExtra("month", Calendar.getInstance().get(Calendar.MONTH));
        int day = getIntent().getIntExtra("day", Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, 
            (view, selectedYear, selectedMonth, selectedDay) -> {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("year", selectedYear);
                resultIntent.putExtra("month", selectedMonth);
                resultIntent.putExtra("day", selectedDay);
                setResult(RESULT_OK, resultIntent);
                finish();
            }, year, month, day);
        
        datePickerDialog.setOnCancelListener(dialog -> finish());
        datePickerDialog.show();
    }
}